package titan.dsl;

import java.util.Objects;

/**
 * Represents one grouping set item inside GROUPING SETS(...).
 */
public final class GroupingSet {

    private final String sql;

    GroupingSet(String sql) {
        this.sql = Objects.requireNonNull(sql, "sql");
    }

    String sql() {
        return sql;
    }
}
