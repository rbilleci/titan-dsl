package titan.dsl;

/**
 * Supported SQL dialects, used by annotation-based entry points, raw SQL blocks, and every DSL
 * rendering path ({@code toSql(SqlDialect)} / {@code render(SqlDialect)}).
 *
 * <p>This is the single dialect enum for the DSL; the former nested
 * {@code InsertBuilder.SqlDialect} duplicate was unified into this type (audit finding D-4).</p>
 */
public enum SqlDialect {
    POSTGRESQL,
    MYSQL
}
