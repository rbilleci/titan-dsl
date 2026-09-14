package titan.dsl;

import java.util.List;
import java.util.Map;
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

    FilterPolicy policy() {
        return policy;
    }

    Scope scope() {
        return scope;
    }

    /** The read predicate for one relation, or {@code null} when nothing applies. */
    Condition conditionFor(TableLike<?> relation) {
        return policy.conditionFor(relation, scope);
    }

    List<FilterPolicy.Fill> checkInsert(TableLike<?> table, List<Column<?>> columns, List<List<Object>> rows) {
        return policy.checkInsert(table, scope, columns, rows);
    }

    Condition checkUpdate(TableLike<?> table, Map<Column<?>, Object> assignments) {
        return policy.checkUpdate(table, scope, assignments);
    }

    void checkInsertSelect(TableLike<?> table, List<Column<?>> targetColumns, SelectBuilder source) {
        policy.checkInsertSelect(table, scope, targetColumns, source);
    }
}
