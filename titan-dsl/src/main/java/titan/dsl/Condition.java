package titan.dsl;

import java.util.Objects;

/**
 * Immutable boolean predicate node used by the Titan DSL.
 *
 * <p>Conditions capture typed values ({@link BindValue}) instead of pre-rendered literal text;
 * rendering is deferred until a dialect and rendering mode are known.</p>
 */
public final class Condition {

    private final SqlFragment fragment;

    private Condition(SqlFragment fragment) {
        this.fragment = Objects.requireNonNull(fragment, "fragment");
    }

    /**
     * Raw-SQL escape hatch: the given text is rendered verbatim in every dialect and mode.
     * Values interpolated into this string are <b>not</b> escaped or bound; prefer the typed
     * column condition factories.
     */
    public static Condition of(String sql) {
        return new Condition(SqlFragment.of(Objects.requireNonNull(sql, "sql")));
    }

    static Condition fromFragment(SqlFragment fragment) {
        return new Condition(fragment);
    }

    public Condition and(Condition other) {
        return new Condition(SqlFragment.of("(", this, ") AND (", other, ")"));
    }

    public Condition or(Condition other) {
        return new Condition(SqlFragment.of("(", this, ") OR (", other, ")"));
    }

    public Condition not() {
        return new Condition(SqlFragment.of("NOT (", this, ")"));
    }

    /**
     * Renders this condition as literal SQL text for the default dialect
     * ({@link SqlDialect#POSTGRESQL}).
     *
     * <p><b>Deprecated:</b> dialect-blind; prefer {@link #sql(SqlDialect)} so MySQL escaping
     * rules apply. Kept for source compatibility with existing consumers.</p>
     */
    public String sql() {
        return sql(SqlDialect.POSTGRESQL);
    }

    /** Renders this condition as literal SQL text for the given dialect. */
    public String sql(SqlDialect dialect) {
        SqlWriter writer = new SqlWriter(SqlWriter.Mode.LITERAL, Objects.requireNonNull(dialect, "dialect"));
        appendTo(writer);
        return writer.sql();
    }

    void appendTo(SqlWriter writer) {
        fragment.appendTo(writer);
    }

    @Override
    public String toString() {
        return sql(SqlDialect.POSTGRESQL);
    }
}
