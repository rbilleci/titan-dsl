package titan.dsl;

import java.util.Objects;

/**
 * The automatic-filter configuration captured by a builder when it is created: the policy plus
 * the request scope resolved at that moment (audit D-6: never re-read at render time).
 */
final class QueryFilters {

    private final FilterPolicy policy;
    private final Scope scope;

    QueryFilters(FilterPolicy policy, Scope scope) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.scope = scope;
    }

    /** The filter condition for one relation, or {@code null} when nothing applies. */
    Condition conditionFor(TableLike<?> relation) {
        return policy.conditionFor(relation, scope);
    }
}
