package titan.dsl;

import java.util.Objects;

public record WindowFrameBoundary(String sql) {

    public WindowFrameBoundary {
        Objects.requireNonNull(sql, "sql");
        if (sql.isBlank()) {
            throw new IllegalArgumentException("sql must not be blank");
        }
    }
}
