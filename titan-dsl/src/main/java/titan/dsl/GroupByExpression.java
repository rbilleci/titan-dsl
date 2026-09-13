package titan.dsl;

import java.util.Objects;

/**
 * Represents advanced GROUP BY expression fragments (e.g. GROUPING SETS/ROLLUP/CUBE).
 */
public final class GroupByExpression {

    private final String sql;

    GroupByExpression(String sql) {
        this.sql = Objects.requireNonNull(sql, "sql");
    }

    String sql() {
        return sql;
    }
}
