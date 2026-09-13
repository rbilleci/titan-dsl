package titan.dsl;

import java.util.Objects;

/**
 * A captured query value paired with its declared {@link SQLType}.
 *
 * <p>Conditions and assignments hold {@code BindValue}s instead of pre-rendered literal text
 * (audit findings D-1/D-2). A {@code BindValue} renders in one of two modes:</p>
 *
 * <ul>
 *   <li><b>Parameterized</b> — emitted as a {@code ?} placeholder and collected in order, so a
 *       JDBC executor can bind it via {@code PreparedStatement.setObject}.</li>
 *   <li><b>Literal</b> — rendered as dialect-correct SQL literal text via
 *       {@link #renderLiteral(SqlDialect)} for consumers that need SQL text (the transpiler).</li>
 * </ul>
 */
public final class BindValue {

    private final Object value;
    private final SQLType sqlType;

    private BindValue(Object value, SQLType sqlType) {
        this.value = value;
        this.sqlType = Objects.requireNonNull(sqlType, "sqlType");
    }

    public static BindValue of(Object value, SQLType sqlType) {
        return new BindValue(value, sqlType);
    }

    /** The captured value; may be {@code null}. */
    public Object value() {
        return value;
    }

    public SQLType sqlType() {
        return sqlType;
    }

    /**
     * Renders this value as dialect-correct SQL literal text: quoted ISO strings with
     * {@code DATE}/{@code TIME}/{@code TIMESTAMP} prefixes for temporals, quoted UUIDs,
     * numeric passthrough, and per-dialect string escaping (backslash-safe on MySQL).
     */
    public String renderLiteral(SqlDialect dialect) {
        return SqlLiterals.render(value, sqlType, dialect);
    }

    @Override
    public String toString() {
        return "BindValue[" + value + ", " + sqlType + "]";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BindValue bindValue)) {
            return false;
        }
        return Objects.equals(value, bindValue.value) && sqlType == bindValue.sqlType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, sqlType);
    }
}
