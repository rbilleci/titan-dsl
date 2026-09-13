package titan.dsl;

import java.util.List;
import java.util.Objects;

/**
 * Result of rendering a DSL statement in parameterized mode: SQL text containing {@code ?}
 * placeholders plus the ordered values to bind (audit findings D-1/R-1).
 */
public final class ParameterizedSql {

    private final String sql;
    private final List<BindValue> parameters;

    ParameterizedSql(String sql, List<BindValue> parameters) {
        this.sql = Objects.requireNonNull(sql, "sql");
        this.parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters"));
    }

    /** SQL text with one {@code ?} placeholder per parameter, in order. */
    public String sql() {
        return sql;
    }

    /** Ordered bind values; index 0 binds to JDBC parameter 1. */
    public List<BindValue> parameters() {
        return parameters;
    }

    @Override
    public String toString() {
        return sql + " " + parameters;
    }
}
