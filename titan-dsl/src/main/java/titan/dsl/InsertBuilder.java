package titan.dsl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Minimal fluent Insert builder for the P1.3 baseline.
 *
 * <p>Uses the unified top-level {@link SqlDialect} (audit finding D-4). On MySQL,
 * {@code returning(...)} no longer splices a second {@code SELECT LAST_INSERT_ID()} statement
 * into the SQL text; executors retrieve generated keys via
 * {@code Statement.RETURN_GENERATED_KEYS} instead (see {@link #returningColumns()}).</p>
 */
public final class InsertBuilder {

    private final SqlDialect configuredDialect;
    private final QueryFilters filters;

    private final Table<?> table;
    private final LinkedHashMap<Column<?>, Object> assignments = new LinkedHashMap<>();
    private final List<Column<?>> explicitColumns = new ArrayList<>();
    private final List<List<Object>> batchValues = new ArrayList<>();
    private SelectBuilder selectSource;
    private final List<Column<?>> returningColumns = new ArrayList<>();
    private ConflictAction conflictAction;

    InsertBuilder(Table<?> table) {
        this(null, null, table);
    }

    InsertBuilder(SqlDialect dialect, QueryFilters filters, Table<?> table) {
        this.configuredDialect = dialect;
        this.filters = filters;
        this.table = Objects.requireNonNull(table, "table");
    }

    public <T> InsertBuilder set(Column<T> column, T value) {
        ensureNoValuesOrSelect();
        assignments.put(Objects.requireNonNull(column, "column"), value);
        return this;
    }

    public InsertBuilder columns(Column<?>... columns) {
        if (!assignments.isEmpty()) {
            throw new IllegalStateException("columns(...) cannot be mixed with set(...)");
        }
        if (columns == null || columns.length == 0) {
            throw new IllegalArgumentException("columns(...) requires at least one column");
        }
        if (!explicitColumns.isEmpty()) {
            throw new IllegalStateException("columns(...) was already set");
        }
        explicitColumns.addAll(Arrays.asList(columns));
        return this;
    }

    public InsertBuilder values(Object... values) {
        if (!assignments.isEmpty()) {
            throw new IllegalStateException("values(...) cannot be mixed with set(...)");
        }
        if (selectSource != null) {
            throw new IllegalStateException("values(...) cannot be mixed with select(...)");
        }
        if (explicitColumns.isEmpty()) {
            throw new IllegalStateException("columns(...) must be called before values(...)");
        }
        if (values == null || values.length != explicitColumns.size()) {
            throw new IllegalArgumentException("values(...) count must match columns(...) count");
        }
        batchValues.add(Arrays.asList(values));
        return this;
    }

    public InsertBuilder select(SelectBuilder selectBuilder) {
        if (!assignments.isEmpty()) {
            throw new IllegalStateException("select(...) cannot be mixed with set(...)");
        }
        if (!batchValues.isEmpty()) {
            throw new IllegalStateException("select(...) cannot be mixed with values(...)");
        }
        this.selectSource = Objects.requireNonNull(selectBuilder, "selectBuilder");
        return this;
    }

    public InsertBuilder returning(Column<?>... columns) {
        if (columns == null || columns.length == 0) {
            throw new IllegalArgumentException("returning(...) requires at least one column");
        }
        returningColumns.addAll(Arrays.asList(columns));
        return this;
    }

    /** Columns requested via {@code returning(...)}; empty when none were requested. */
    public List<Column<?>> returningColumns() {
        return Collections.unmodifiableList(returningColumns);
    }

    public OnConflictStep onConflict(Column<?>... columns) {
        if (columns == null || columns.length == 0) {
            throw new IllegalArgumentException("onConflict(...) requires at least one column");
        }
        if (conflictAction != null) {
            throw new IllegalStateException("onConflict(...) was already set");
        }
        List<Column<?>> conflictColumns = new ArrayList<>();
        for (Column<?> column : columns) {
            conflictColumns.add(Objects.requireNonNull(column, "column"));
        }
        this.conflictAction = new ConflictAction(conflictColumns);
        return new OnConflictStep();
    }

    /**
     * Placeholder execute for transpiler-oriented DSL: returns emitted SQL text.
     */
    public String execute() {
        return toSql();
    }

    /**
     * Renders literal SQL using this query's configured dialect. Legacy static-DSL queries
     * default to PostgreSQL. Prefer {@link #render()} for bound parameters on context queries.
     */
    public String toSql() {
        return toSql(defaultDialect());
    }

    private SqlDialect defaultDialect() {
        return configuredDialect == null ? SqlDialect.POSTGRESQL : configuredDialect;
    }

    /**
     * Renders bound SQL using the context's dialect.
     * @throws IllegalStateException if created through the unconfigured static DSL
     */
    public ParameterizedSql render() {
        if (configuredDialect == null) {
            throw new IllegalStateException("No SQL dialect configured. Create queries with DSL.using(dialect), "
                    + "or use render(dialect) for an unconfigured query.");
        }
        return render(configuredDialect);
    }

    /** Renders literal SQL text for the given dialect (values inlined as typed literals). */
    public String toSql(SqlDialect dialect) {
        SqlWriter writer = new SqlWriter(SqlWriter.Mode.LITERAL, Objects.requireNonNull(dialect, "dialect"));
        appendTo(writer);
        return writer.sql();
    }

    /**
     * Renders parameterized SQL for the given dialect: {@code ?} placeholders plus the ordered
     * bind values, for execution via {@code PreparedStatement}.
     */
    public ParameterizedSql render(SqlDialect dialect) {
        SqlWriter writer = new SqlWriter(SqlWriter.Mode.PARAMETERIZED, Objects.requireNonNull(dialect, "dialect"));
        appendTo(writer);
        return writer.toParameterizedSql();
    }

    private void appendTo(SqlWriter writer) {
        SqlDialect dialect = writer.dialect();

        writer.append("INSERT INTO ").append(qualifiedName(table));

        if (!assignments.isEmpty()) {
            appendValuesRows(writer, new ArrayList<>(assignments.keySet()), List.of(new ArrayList<>(assignments.values())));
        } else if (selectSource != null) {
            if (filters != null) {
                filters.checkInsertSelect(table, explicitColumns, selectSource);
            }
            if (!explicitColumns.isEmpty()) {
                appendColumns(writer, explicitColumns);
            }
            writer.append(' ');
            selectSource.appendTo(writer, true);
        } else if (!batchValues.isEmpty()) {
            appendValuesRows(writer, explicitColumns, batchValues);
        } else {
            throw new IllegalStateException("insert requires set(...), values(...), or select(...)");
        }

        appendConflictClause(writer);

        if (!returningColumns.isEmpty()) {
            if (dialect == SqlDialect.POSTGRESQL) {
                StringJoiner returning = new StringJoiner(", ");
                for (Column<?> column : returningColumns) {
                    returning.add(column.name());
                }
                writer.append(" RETURNING ").append(returning.toString());
            } else if (dialect == SqlDialect.MYSQL) {
                if (returningColumns.size() != 1) {
                    throw new IllegalStateException("MySQL insert returning supports exactly one auto-increment column");
                }
                // No SQL is appended: MySQL has no RETURNING clause. Executors must request
                // Statement.RETURN_GENERATED_KEYS for the single auto-increment column instead
                // of the former spliced "; SELECT LAST_INSERT_ID()" multi-statement hack.
            } else {
                throw new IllegalStateException("Unsupported SQL dialect for returning(...): " + dialect);
            }
        }
    }

    /**
     * Renders the column list and VALUES rows. Under a scope, checked columns are verified and
     * missing ones are filled at render time only; the builder's own state is never changed.
     */
    private void appendValuesRows(SqlWriter writer, List<Column<?>> columns, List<List<Object>> rows) {
        List<FilterPolicy.Fill> fills = filters == null ? List.of() : filters.checkInsert(table, columns, rows);
        List<Column<?>> allColumns = new ArrayList<>(columns);
        for (FilterPolicy.Fill fill : fills) {
            allColumns.add(fill.column());
        }
        appendColumns(writer, allColumns);
        writer.append(" VALUES ");
        boolean firstRow = true;
        for (List<Object> row : rows) {
            if (!firstRow) {
                writer.append(", ");
            }
            writer.append('(');
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) {
                    writer.append(", ");
                }
                writer.appendValue(BindValue.of(row.get(i), columns.get(i).sqlType()));
            }
            for (FilterPolicy.Fill fill : fills) {
                writer.append(", ");
                writer.appendValue(BindValue.of(fill.value(), fill.column().sqlType()));
            }
            writer.append(')');
            firstRow = false;
        }
    }

    private void appendConflictClause(SqlWriter writer) {
        if (conflictAction == null) {
            return;
        }
        if (conflictAction.assignments.isEmpty()) {
            throw new IllegalStateException("onConflict(...).doUpdate().set(...) requires at least one set(...) assignment");
        }
        // The existing row an upsert updates must be visible (USING) and stay visible (WITH CHECK).
        // PostgreSQL takes that as DO UPDATE ... WHERE; MySQL has no such clause, so each
        // assignment becomes IF(<guard>, <new value>, <column>) and a foreign row is left as is.
        Condition guard = null;
        if (filters != null) {
            Condition existing = filters.conditionFor(table);
            Condition stillVisible = filters.checkUpdate(table, conflictAction.assignments);
            guard = existing == null ? stillVisible : stillVisible == null ? existing : existing.and(stillVisible);
        }
        if (writer.dialect() == SqlDialect.POSTGRESQL) {
            StringJoiner conflictColumns = new StringJoiner(", ");
            for (Column<?> column : conflictAction.columns) {
                conflictColumns.add(column.name());
            }
            writer.append(" ON CONFLICT (").append(conflictColumns.toString()).append(") DO UPDATE SET ");
        } else {
            writer.append(" ON DUPLICATE KEY UPDATE ");
        }
        boolean mysqlGuard = guard != null && writer.dialect() == SqlDialect.MYSQL;
        boolean first = true;
        for (Map.Entry<Column<?>, Object> assignment : conflictAction.assignments.entrySet()) {
            if (!first) {
                writer.append(", ");
            }
            String column = assignment.getKey().name();
            writer.append(column).append(" = ");
            if (mysqlGuard) {
                writer.append("IF(");
                guard.appendTo(writer);
                writer.append(", ");
            }
            UpdateBuilder.appendAssignmentValue(writer, assignment.getKey(), assignment.getValue());
            if (mysqlGuard) {
                writer.append(", ").append(column).append(')');
            }
            first = false;
        }
        if (guard != null && writer.dialect() == SqlDialect.POSTGRESQL) {
            writer.append(" WHERE ");
            guard.appendTo(writer);
        }
    }

    public final class OnConflictStep {
        private OnConflictStep() {
        }

        public DoUpdateStep doUpdate() {
            return new DoUpdateStep();
        }
    }

    public final class DoUpdateStep {
        private DoUpdateStep() {
        }

        public <T> InsertBuilder set(Column<T> column, T value) {
            conflictAction.assignments.put(Objects.requireNonNull(column, "column"), value);
            return InsertBuilder.this;
        }

        /**
         * Expression-valued conflict assignment (G3): the ON CONFLICT DO UPDATE / ON DUPLICATE KEY
         * UPDATE right-hand side is a column EXPRESSION rather than a caller-supplied
         * literal/parameter, so {@code set(VERSION, VERSION.add(1))} renders
         * {@code version = version + 1} and the upsert bumps the existing row's value atomically in
         * the database. More specific than {@link #set(Column, Object)} for {@code Column}-typed
         * right-hand sides.
         */
        public <T> InsertBuilder set(Column<T> column, Column<T> expression) {
            conflictAction.assignments.put(
                    Objects.requireNonNull(column, "column"),
                    Objects.requireNonNull(expression, "expression"));
            return InsertBuilder.this;
        }

        public String execute() {
            return InsertBuilder.this.execute();
        }

        public String toSql() {
            return InsertBuilder.this.toSql();
        }
    }

    private static final class ConflictAction {
        private final List<Column<?>> columns;
        private final LinkedHashMap<Column<?>, Object> assignments = new LinkedHashMap<>();

        private ConflictAction(List<Column<?>> columns) {
            this.columns = columns;
        }
    }

    private static void appendColumns(SqlWriter writer, List<Column<?>> columns) {
        StringJoiner joiner = new StringJoiner(", ");
        for (Column<?> column : columns) {
            joiner.add(Objects.requireNonNull(column, "column").name());
        }
        writer.append(" (").append(joiner.toString()).append(')');
    }

    private static String qualifiedName(TableLike<?> table) {
        if (table.schema() != null && !table.schema().isBlank()) {
            return table.schema() + '.' + table.name();
        }
        return table.name();
    }

    private void ensureNoValuesOrSelect() {
        if (!batchValues.isEmpty()) {
            throw new IllegalStateException("set(...) cannot be mixed with values(...)");
        }
        if (selectSource != null) {
            throw new IllegalStateException("set(...) cannot be mixed with select(...)");
        }
    }
}
