package titan.dsl;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Minimal fluent Update builder for the P1.3 baseline.
 */
public final class UpdateBuilder {

    private final SqlDialect configuredDialect;
    private final QueryFilters filters;

    private final Table<?> table;
    private final LinkedHashMap<Column<?>, Object> assignments = new LinkedHashMap<>();
    private Condition whereCondition;
    private final List<Column<?>> returningColumns = new java.util.ArrayList<>();

    UpdateBuilder(Table<?> table) {
        this(null, null, table);
    }

    UpdateBuilder(SqlDialect dialect, QueryFilters filters, Table<?> table) {
        this.configuredDialect = dialect;
        this.filters = filters;
        this.table = Objects.requireNonNull(table, "table");
    }

    public <T> UpdateBuilder set(Column<T> column, T value) {
        assignments.put(Objects.requireNonNull(column, "column"), value);
        return this;
    }

    /**
     * Expression-valued assignment (G3): the SET right-hand side is a column EXPRESSION rather
     * than a caller-supplied literal/parameter, so an atomic server-side mutation such as
     * {@code set(VERSION, VERSION.add(1))} renders {@code version = version + 1} and the database
     * computes the new value in place. Selected over {@link #set(Column, Object)} for
     * {@code Column}-typed right-hand sides because {@code Column<T>} is more specific than the
     * literal overload's {@code T}.
     */
    public <T> UpdateBuilder set(Column<T> column, Column<T> expression) {
        assignments.put(Objects.requireNonNull(column, "column"), Objects.requireNonNull(expression, "expression"));
        return this;
    }

    public UpdateBuilder where(Condition condition) {
        this.whereCondition = Objects.requireNonNull(condition, "condition");
        return this;
    }

    public UpdateBuilder returning(Column<?>... columns) {
        if (columns == null || columns.length == 0) {
            throw new IllegalArgumentException("returning(...) requires at least one column");
        }
        returningColumns.addAll(Arrays.asList(columns));
        return this;
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
        if (assignments.isEmpty()) {
            throw new IllegalStateException("update requires at least one set(column, value)");
        }

        writer.append("UPDATE ").append(qualifiedName(table)).append(" SET ");

        boolean first = true;
        for (Map.Entry<Column<?>, Object> entry : assignments.entrySet()) {
            if (!first) {
                writer.append(", ");
            }
            writer.append(entry.getKey().name()).append(" = ");
            appendAssignmentValue(writer, entry.getKey(), entry.getValue());
            first = false;
        }

        Condition effectiveWhere = whereCondition;
        if (filters != null) {
            Condition filter = filters.conditionFor(table);
            if (filter != null) {
                effectiveWhere = effectiveWhere == null ? filter : effectiveWhere.and(filter);
            }
            Condition stillVisible = filters.checkUpdate(table, assignments);
            if (stillVisible != null) {
                effectiveWhere = effectiveWhere == null ? stillVisible : effectiveWhere.and(stillVisible);
            }
        }
        if (effectiveWhere != null) {
            writer.append(" WHERE ");
            effectiveWhere.appendTo(writer);
        }

        if (!returningColumns.isEmpty()) {
            if (writer.dialect() != SqlDialect.POSTGRESQL) {
                throw new IllegalStateException("returning(...) is only supported for PostgreSQL updates");
            }
            StringJoiner returning = new StringJoiner(", ");
            for (Column<?> column : returningColumns) {
                returning.add(column.name());
            }
            writer.append(" RETURNING ").append(returning.toString());
        }
    }

    /**
     * Renders a SET right-hand side: a {@link Column} value is an expression assignment (G3) so it
     * renders as its column expression text (for example {@code version + 1}); any other value is a
     * typed literal/parameter bind.
     */
    static void appendAssignmentValue(SqlWriter writer, Column<?> column, Object value) {
        if (value instanceof Column<?> expression) {
            writer.append(expression.name());
        } else {
            writer.appendValue(BindValue.of(value, column.sqlType()));
        }
    }

    private static String qualifiedName(TableLike<?> table) {
        if (table.schema() != null && !table.schema().isBlank()) {
            return table.schema() + '.' + table.name();
        }
        return table.name();
    }
}
