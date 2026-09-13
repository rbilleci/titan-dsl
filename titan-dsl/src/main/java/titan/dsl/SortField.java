package titan.dsl;

import java.util.Objects;

/**
 * ORDER BY expression with direction and optional explicit NULL ordering (audit D-7).
 */
public record SortField(String expression, boolean ascending, NullOrdering nullOrdering) {

    /** Explicit placement of NULL values within an ordered result. */
    public enum NullOrdering {
        FIRST,
        LAST
    }

    public SortField {
        Objects.requireNonNull(expression, "expression");
    }

    public SortField(String expression, boolean ascending) {
        this(expression, ascending, null);
    }

    /** Returns a copy of this sort field placing NULL values first. */
    public SortField nullsFirst() {
        return new SortField(expression, ascending, NullOrdering.FIRST);
    }

    /** Returns a copy of this sort field placing NULL values last. */
    public SortField nullsLast() {
        return new SortField(expression, ascending, NullOrdering.LAST);
    }

    public String sql() {
        String direction = expression + (ascending ? " ASC" : " DESC");
        if (nullOrdering == null) {
            return direction;
        }
        return direction + (nullOrdering == NullOrdering.FIRST ? " NULLS FIRST" : " NULLS LAST");
    }

    /**
     * Renders this sort field for the writer's dialect. PostgreSQL supports
     * {@code NULLS FIRST/LAST} natively; MySQL does not, so an explicit NULL ordering is
     * emulated with a leading {@code (expr IS NULL)} sort key ({@code DESC} floats NULLs to
     * the front, {@code ASC} sinks them to the back) followed by the requested direction.
     */
    void appendTo(SqlWriter writer) {
        if (nullOrdering == null || writer.dialect() != SqlDialect.MYSQL) {
            writer.append(sql());
            return;
        }
        writer.append('(')
                .append(expression)
                .append(" IS NULL) ")
                .append(nullOrdering == NullOrdering.FIRST ? "DESC" : "ASC")
                .append(", ")
                .append(expression)
                .append(ascending ? " ASC" : " DESC");
    }
}
