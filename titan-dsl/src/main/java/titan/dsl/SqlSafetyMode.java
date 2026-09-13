package titan.dsl;

/**
 * Raw-SQL safety mode for a {@link SqlSafety} scope
 * (WS-C / the titan transpiler's {@code docs/transpilable-jdbc-subset.md} §4).
 *
 * <ul>
 *   <li>{@link #STRICT} — injection-proof by construction: SQL text passed to
 *       {@code prepareStatement}/{@code createStatement(...).execute(...)} must be a compile-time
 *       constant. Splicing a runtime value or identifier is a hard {@code TITAN-E004}.</li>
 *   <li>{@link #PERMISSIVE} — raw SQL that strict mode would reject transpiles faithfully as a
 *       {@code RawSql} passthrough, for compatibility with existing codebases. The value bindings
 *       remain parameterized either way.</li>
 * </ul>
 */
public enum SqlSafetyMode {
    STRICT,
    PERMISSIVE
}
