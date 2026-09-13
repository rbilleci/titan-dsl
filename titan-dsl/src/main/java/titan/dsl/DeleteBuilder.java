package titan.dsl;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Minimal fluent Delete builder for the P1.3 baseline.
 */
public final class DeleteBuilder {

    private final SqlDialect configuredDialect;

    private final Table<?> table;
    private Condition whereCondition;
    private final List<Column<?>> returningColumns = new java.util.ArrayList<>();

    DeleteBuilder(Table<?> table) {
        this(null, table);
    }

    DeleteBuilder(SqlDialect dialect, Table<?> table) {
        this.configuredDialect = dialect;
        this.table = Objects.requireNonNull(table, "table");
    }

    public DeleteBuilder where(Condition condition) {
        this.whereCondition = Objects.requireNonNull(condition, "condition");
        return this;
    }

    public DeleteBuilder returning(Column<?>... columns) {
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
        writer.append("DELETE FROM ").append(qualifiedName(table));

        if (whereCondition != null) {
            writer.append(" WHERE ");
            whereCondition.appendTo(writer);
        }

        if (!returningColumns.isEmpty()) {
            if (writer.dialect() != SqlDialect.POSTGRESQL) {
                throw new IllegalStateException("returning(...) is only supported for PostgreSQL deletes");
            }
            StringJoiner returning = new StringJoiner(", ");
            for (Column<?> column : returningColumns) {
                returning.add(column.name());
            }
            writer.append(" RETURNING ").append(returning.toString());
        }
    }

    private static String qualifiedName(TableLike<?> table) {
        if (table.schema() != null && !table.schema().isBlank()) {
            return table.schema() + '.' + table.name();
        }
        return table.name();
    }
}
