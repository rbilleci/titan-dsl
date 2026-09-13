package titan.dsl;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an entry point as a {@code SECURITY DEFINER} routine. Titan auto-pins {@code search_path} to the
 * routine's schema (ATG-017a). The optional attributes drive reviewable deployment-policy artifacts
 * (ATG-017b): owner, PUBLIC-revoke, and least-privilege execute grants, emitted as SQL separate from the
 * routine body so consumers apply them under their own migration policy.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SecurityDefiner {
    /** Role to own the routine ({@code ALTER … OWNER TO}); empty leaves the deploying role as owner. */
    String ownerRole() default "";

    /** Roles granted {@code EXECUTE} on the routine; empty emits no grant. */
    String[] executeRoles() default {};

    /** When true, emit {@code REVOKE ALL … FROM PUBLIC} so only the granted roles may execute. */
    boolean revokePublic() default false;
}
