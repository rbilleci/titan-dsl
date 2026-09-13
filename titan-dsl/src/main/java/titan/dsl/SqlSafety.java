package titan.dsl;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Per-scope raw-SQL safety override for the JDBC front-end
 * (WS-C / the titan transpiler's {@code docs/transpilable-jdbc-subset.md} §4, §5 I-R1).
 *
 * <p>{@code @SqlSafety(PERMISSIVE)} opts just the annotated method or class out of strict mode
 * (localized, greppable raw-SQL passthrough); {@code @SqlSafety(STRICT)} re-tightens a scope when
 * the build is globally permissive. Precedence is <b>narrowest scope wins</b>: a method's own
 * {@code @SqlSafety} overrides its nearest-enclosing class's {@code @SqlSafety}, which overrides the
 * build-level {@code sqlSafety} setting (default {@code strict}). A TYPE annotation does <b>not</b>
 * transitively cover nested classes unless they (or an enclosing class) are themselves annotated.</p>
 *
 * <p>Retention is {@link RetentionPolicy#SOURCE}: the transpiler reads it from the javac AST/{@code
 * Element} during parsing; there is no runtime need.</p>
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface SqlSafety {
    SqlSafetyMode value();
}
