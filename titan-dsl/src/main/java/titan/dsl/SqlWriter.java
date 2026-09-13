package titan.dsl;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Rendering accumulator threading the target {@link SqlDialect} and rendering mode through
 * every {@code toSql} path (audit finding D-4).
 */
final class SqlWriter {

    enum Mode {
        /** Values rendered inline as dialect-correct literals (transpiler-facing SQL text). */
        LITERAL,
        /** Values rendered as {@code ?} placeholders and collected for JDBC binding. */
        PARAMETERIZED
    }

    private final StringBuilder sql = new StringBuilder();
    private final List<BindValue> parameters = new ArrayList<>();
    private final Mode mode;
    private final SqlDialect dialect;

    SqlWriter(Mode mode, SqlDialect dialect) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
    }

    SqlWriter append(String text) {
        sql.append(text);
        return this;
    }

    SqlWriter append(char character) {
        sql.append(character);
        return this;
    }

    SqlWriter appendValue(BindValue value) {
        if (mode == Mode.LITERAL) {
            sql.append(value.renderLiteral(dialect));
        } else {
            sql.append('?');
            parameters.add(value);
        }
        return this;
    }

    SqlDialect dialect() {
        return dialect;
    }

    String sql() {
        return sql.toString();
    }

    List<BindValue> parameters() {
        return parameters;
    }

    ParameterizedSql toParameterizedSql() {
        return new ParameterizedSql(sql(), parameters);
    }
}
