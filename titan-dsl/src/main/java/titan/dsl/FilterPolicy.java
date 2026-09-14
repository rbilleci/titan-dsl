package titan.dsl;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Automatic row filters resolved once, at startup, for every relation in a catalog.
 *
 * <p>A policy is a conjunction of {@link Filter}s. Each filter reaches a table through a direct
 * column, a foreign-key path (rendered as a correlated {@code EXISTS}), or a lambda written in
 * this DSL. {@link Builder#anyOf} groups filters that are alternatives ({@code OR}).
 * {@link Builder#build()} validates every relation in the catalog and reports all gaps at once:
 * a value filter must be bound, derived, or exempt for every relation, so a new table cannot
 * silently escape a tenant filter.</p>
 *
 * <p>Filters are applied when a query is rendered; see {@link DSLContext#filters(FilterPolicy)}.
 * Synthesized subqueries alias their tables {@code _tf1}, {@code _tf2}, ...; do not reuse that
 * prefix for your own aliases.</p>
 */
public final class FilterPolicy {

    static final String HOP_ALIAS_PREFIX = "_tf";
    private static final Column<Integer> ONE = new Column<>("1", SQLType.INTEGER, Nullability.NOT_NULL);

    private final Map<TableKey, TablePlan> plans;

    private FilterPolicy(Map<TableKey, TablePlan> plans) {
        this.plans = plans;
    }

    public static Builder builder(TableLike<?>... catalog) {
        return new Builder(Arrays.asList(Objects.requireNonNull(catalog, "catalog")));
    }

    public static Builder builder(Collection<? extends TableLike<?>> catalog) {
        return new Builder(Objects.requireNonNull(catalog, "catalog"));
    }

    /**
     * The condition to add for one relation of a query, or {@code null} when nothing applies.
     * CTEs and inline views are skipped: their bodies render with their own filters.
     */
    Condition conditionFor(TableLike<?> relation, Scope scope) {
        if (relation instanceof AliasedTable<?> aliased
                && aliased.alias().toLowerCase(Locale.ROOT).startsWith(HOP_ALIAS_PREFIX)) {
            // A user alias that shadows a hop alias would break the EXISTS correlation silently;
            // compared case-insensitively because unquoted identifiers fold case in both dialects.
            throw new IllegalStateException("Alias '" + aliased.alias() + "' uses the reserved prefix '"
                    + HOP_ALIAS_PREFIX + "' of automatic-filter subqueries; choose another alias.");
        }
        TableRef ref = TableRef.of(relation);
        if (ref == null) {
            return null;
        }
        TableKey key = TableKey.of(ref.relation());
        TablePlan plan = plans.get(key);
        if (plan == null) {
            throw new IllegalStateException("Relation " + key + " is not in the FilterPolicy catalog. Add it to the "
                    + "catalog and bind or exempt it, or query it through DSLContext.unscoped().");
        }
        return plan.condition(ref, scope);
    }

    /** Human-readable resolution of every filter for one relation. */
    public String explain(TableLike<?> relation) {
        TableKey key = TableKey.of(Objects.requireNonNull(relation, "relation"));
        TablePlan plan = plans.get(key);
        if (plan == null) {
            return key + ": not in catalog";
        }
        return plan.describe(key);
    }

    /** {@link #explain(TableLike)} for every relation in the catalog, in catalog order. */
    public String explain() {
        StringJoiner joiner = new StringJoiner("\n");
        for (Map.Entry<TableKey, TablePlan> entry : plans.entrySet()) {
            joiner.add(entry.getValue().describe(entry.getKey()));
        }
        return joiner.toString();
    }

    // ---------------------------------------------------------------- plan model

    record TableKey(String schema, String name) {
        static TableKey of(TableLike<?> relation) {
            return new TableKey(relation.schema(), relation.name());
        }

        @Override
        public String toString() {
            return schema == null || schema.isBlank() ? name : schema + '.' + name;
        }
    }

    /**
     * Everything resolved for one relation: the read predicate terms plus the write-side view of
     * them. {@code checked} are standalone value filters bound to a direct column (verifiable and
     * fillable on INSERT/UPDATE); {@code groupChecks} are {@code anyOf} groups whose bound members
     * on this relation are all direct columns; {@code uncheckedFilters} are value filters bound
     * through a path or a lambda, which writes cannot verify.
     */
    private record TablePlan(List<Term> terms, boolean exempt, List<String> exemptFilters,
                             List<Checked> checked, List<List<Checked>> groupChecks,
                             List<Filter<?>> uncheckedFilters, List<String> writeNotes) {

        static TablePlan exemptPlan() {
            return new TablePlan(List.of(), true, List.of(), List.of(), List.of(), List.of(), List.of());
        }

        Condition condition(TableRef ref, Scope scope) {
            Condition result = null;
            for (Term term : terms) {
                Condition condition = term.condition(ref, scope);
                if (condition != null) {
                    result = result == null ? condition : result.and(condition);
                }
            }
            return result;
        }

        String describe(TableKey key) {
            if (exempt) {
                return key + ": exempt";
            }
            if (terms.isEmpty() && exemptFilters.isEmpty()) {
                return key + ": no filters";
            }
            StringBuilder text = new StringBuilder(key.toString());
            for (Term term : terms) {
                text.append("\n  ").append(term.describe());
            }
            for (String name : exemptFilters) {
                text.append("\n  ").append(name).append(": exempt");
            }
            if (!writeNotes.isEmpty()) {
                text.append("\n  write: ").append(String.join("; ", writeNotes));
            }
            return text.toString();
        }
    }

    record Checked(Filter<?> filter, Column<?> column) {
    }

    /** A column an INSERT must add because the scope determines its only admissible value. */
    record Fill(Column<?> column, Object value) {
    }

    private sealed interface Term permits Leaf, Group {
        Condition condition(TableRef ref, Scope scope);

        String describe();
    }

    private record Leaf(Filter<?> filter, Binding binding) implements Term {
        @Override
        public Condition condition(TableRef ref, Scope scope) {
            if (scope != null && scope.isSkipped(filter)) {
                return null;
            }
            if (!filter.requiresValue()) {
                return binding.apply(ref, null);
            }
            Object value = scopeValue(filter, scope, ref);
            return value == null ? null : binding.apply(ref, value);
        }

        @Override
        public String describe() {
            return filter.name() + ": " + binding.describe();
        }
    }

    private record Group(List<Leaf> members) implements Term {
        @Override
        public Condition condition(TableRef ref, Scope scope) {
            Condition result = null;
            for (Leaf member : members) {
                Condition condition = member.condition(ref, scope);
                if (condition != null) {
                    result = result == null ? condition : result.or(condition);
                }
            }
            return result;
        }

        @Override
        public String describe() {
            StringJoiner joiner = new StringJoiner("; ", "anyOf: ", "");
            for (Leaf member : members) {
                joiner.add(member.describe());
            }
            return joiner.toString();
        }
    }

    // ---------------------------------------------------------------- bindings

    private sealed interface Binding permits ColumnBinding, ValueBinding, PredicateBinding, PathBinding {
        Condition apply(TableRef ref, Object value);

        String describe();
    }

    private record ColumnBinding(Column<?> column) implements Binding {
        @Override
        @SuppressWarnings("unchecked")
        public Condition apply(TableRef ref, Object value) {
            requireCompatible(value, column, ref);
            Column<Object> qualified = (Column<Object>) ref.col(column);
            if (value instanceof Scope.Values multi) {
                return qualified.in(multi.values());
            }
            return qualified.eq(value);
        }

        @Override
        public String describe() {
            return "column " + column.name();
        }
    }

    private record ValueBinding(BiFunction<TableRef, Object, Condition> function) implements Binding {
        @Override
        public Condition apply(TableRef ref, Object value) {
            if (value instanceof Scope.Values) {
                throw new IllegalStateException("Scope.withAny(...) is only supported by column bindings; the custom "
                        + "binding on " + ref + " needs a single value from Scope.with(filter, value).");
            }
            return Objects.requireNonNull(function.apply(ref, value), "binding returned null condition");
        }

        @Override
        public String describe() {
            return "custom condition";
        }
    }

    private record PredicateBinding(Function<TableRef, Condition> function) implements Binding {
        @Override
        public Condition apply(TableRef ref, Object value) {
            return Objects.requireNonNull(function.apply(ref), "binding returned null condition");
        }

        @Override
        public String describe() {
            return "custom predicate";
        }
    }

    private record Hop(List<Column<?>> localColumns, Table<?> target, List<Column<?>> referencedColumns) {
    }

    private record PathBinding(List<Hop> hops, Binding terminal, boolean derived) implements Binding {
        @Override
        public Condition apply(TableRef outer, Object value) {
            Hop first = hops.get(0);
            AliasedTable<?> firstAlias = first.target().as(HOP_ALIAS_PREFIX + 1);
            SelectBuilder subquery = new SelectBuilder(ONE).from(firstAlias);
            TableRef previous = TableRef.of(firstAlias);
            Condition correlation = join(previous, first.referencedColumns(), outer, first.localColumns());
            for (int i = 1; i < hops.size(); i++) {
                Hop hop = hops.get(i);
                AliasedTable<?> alias = hop.target().as(HOP_ALIAS_PREFIX + (i + 1));
                TableRef current = TableRef.of(alias);
                subquery.join(alias).on(join(current, hop.referencedColumns(), previous, hop.localColumns()));
                previous = current;
            }
            subquery.where(correlation.and(terminal.apply(previous, value)));
            return DSL.exists(subquery);
        }

        private static Condition join(TableRef left, List<Column<?>> leftColumns,
                                      TableRef right, List<Column<?>> rightColumns) {
            Condition result = null;
            for (int i = 0; i < leftColumns.size(); i++) {
                Condition equal = Condition.of(left.col(leftColumns.get(i)).name() + " = "
                        + right.col(rightColumns.get(i)).name());
                result = result == null ? equal : result.and(equal);
            }
            return result;
        }

        @Override
        public String describe() {
            StringBuilder text = new StringBuilder("EXISTS via");
            for (Hop hop : hops) {
                text.append(' ').append(hop.localColumns().get(0).name())
                        .append(" -> ").append(TableKey.of(hop.target()));
            }
            text.append(" (").append(terminal.describe()).append(')');
            if (derived) {
                text.append(" [derived]");
            }
            return text.toString();
        }
    }

    // ---------------------------------------------------------------- write-side checks

    /**
     * Validates an INSERT's values against the scope and returns the columns to fill. A value
     * assigned to a checked column must be within the scope; an unassigned checked column is
     * filled when the scope holds one value. Group columns need at least one match per row.
     */
    List<Fill> checkInsert(TableLike<?> table, Scope scope, List<Column<?>> columns, List<List<Object>> rows) {
        TablePlan plan = writePlan(table);
        if (plan.exempt()) {
            return List.of();
        }
        TableRef ref = TableRef.of(table);
        List<Fill> fills = new ArrayList<>();
        for (Checked check : plan.checked()) {
            Object scopeValue = scopeValue(check.filter(), scope, ref);
            if (scopeValue == null) {
                continue;
            }
            requireCompatible(scopeValue, check.column(), ref);
            int index = indexOf(columns, check.column());
            if (index < 0) {
                if (scopeValue instanceof Scope.Values) {
                    throw new IllegalStateException("INSERT into " + ref + " must assign " + check.column().name()
                            + " explicitly: the Scope has several values for filter '" + check.filter().name() + "'.");
                }
                fills.add(new Fill(check.column(), scopeValue));
                continue;
            }
            for (List<Object> row : rows) {
                requireWithinScope(row.get(index), check, scopeValue, ref, "INSERT into ");
            }
        }
        for (List<Checked> group : plan.groupChecks()) {
            List<Checked> active = new ArrayList<>();
            List<Object> scopeValues = new ArrayList<>();
            for (Checked member : group) {
                Object scopeValue = scopeValue(member.filter(), scope, ref);
                if (scopeValue != null) {
                    requireCompatible(scopeValue, member.column(), ref);
                    active.add(member);
                    scopeValues.add(scopeValue);
                }
            }
            if (active.isEmpty()) {
                continue;
            }
            for (List<Object> row : rows) {
                boolean matched = false;
                for (int i = 0; i < active.size() && !matched; i++) {
                    int index = indexOf(columns, active.get(i).column());
                    if (index < 0) {
                        continue;
                    }
                    rejectExpression(row.get(index), active.get(i), ref, "INSERT into ");
                    matched = withinScope(row.get(index), scopeValues.get(i), active.get(i).column().sqlType());
                }
                if (!matched) {
                    throw new IllegalStateException("INSERT into " + ref + " must assign at least one of "
                            + columnNames(active) + " to a value within the current scope.");
                }
            }
        }
        return fills;
    }

    /**
     * Validates UPDATE assignments. A checked column may only be set to a value within the scope.
     * For a group, an assignment that no longer matches is allowed only for rows that stay
     * visible through an unassigned member; the returned condition selects those rows.
     */
    Condition checkUpdate(TableLike<?> table, Scope scope, Map<Column<?>, Object> assignments) {
        TablePlan plan = writePlan(table);
        if (plan.exempt()) {
            return null;
        }
        TableRef ref = TableRef.of(table);
        List<Column<?>> columns = new ArrayList<>(assignments.keySet());
        List<Object> values = new ArrayList<>(assignments.values());
        for (Checked check : plan.checked()) {
            Object scopeValue = scopeValue(check.filter(), scope, ref);
            if (scopeValue == null) {
                continue;
            }
            requireCompatible(scopeValue, check.column(), ref);
            int index = indexOf(columns, check.column());
            if (index >= 0) {
                requireWithinScope(values.get(index), check, scopeValue, ref, "UPDATE of ");
            }
        }
        Condition extra = null;
        for (List<Checked> group : plan.groupChecks()) {
            boolean anyAssigned = false;
            boolean matched = false;
            List<Checked> unassigned = new ArrayList<>();
            List<Object> unassignedValues = new ArrayList<>();
            for (Checked member : group) {
                Object scopeValue = scopeValue(member.filter(), scope, ref);
                if (scopeValue == null) {
                    continue;
                }
                requireCompatible(scopeValue, member.column(), ref);
                int index = indexOf(columns, member.column());
                if (index < 0) {
                    unassigned.add(member);
                    unassignedValues.add(scopeValue);
                    continue;
                }
                anyAssigned = true;
                rejectExpression(values.get(index), member, ref, "UPDATE of ");
                matched |= withinScope(values.get(index), scopeValue, member.column().sqlType());
            }
            if (!anyAssigned || matched) {
                continue;
            }
            if (unassigned.isEmpty()) {
                throw new IllegalStateException("UPDATE of " + ref + " assigns " + columnNames(group)
                        + " outside the current scope; the rows would become invisible.");
            }
            Condition stillVisible = null;
            for (int i = 0; i < unassigned.size(); i++) {
                Condition member = new ColumnBinding(unassigned.get(i).column()).apply(ref, unassignedValues.get(i));
                stillVisible = stillVisible == null ? member : stillVisible.or(member);
            }
            extra = extra == null ? stillVisible : extra.and(stillVisible);
        }
        return extra;
    }

    /**
     * MySQL's {@code ON DUPLICATE KEY UPDATE} takes no WHERE, so the existing row can only be
     * kept in scope by the unique key itself: every checked column must be part of the declared
     * conflict target, and filters that are not direct columns cannot be guarded at all.
     */
    void checkMysqlUpsert(TableLike<?> table, Scope scope, List<Column<?>> conflictColumns) {
        TablePlan plan = writePlan(table);
        if (plan.exempt()) {
            return;
        }
        TableRef ref = TableRef.of(table);
        for (Checked check : plan.checked()) {
            if (scopeValue(check.filter(), scope, ref) != null && indexOf(conflictColumns, check.column()) < 0) {
                throw new IllegalStateException("MySQL ON DUPLICATE KEY UPDATE on " + ref + " needs "
                        + check.column().name() + " in onConflict(...) and in the matching unique key; "
                        + "the existing row cannot be checked otherwise.");
            }
        }
        for (Filter<?> filter : plan.uncheckedFilters()) {
            if (scopeValue(filter, scope, ref) != null) {
                throw new IllegalStateException("MySQL ON DUPLICATE KEY UPDATE on " + ref + " cannot check the "
                        + "existing row for filter '" + filter.name() + "' (not a direct column binding); use a "
                        + "direct column, PostgreSQL, or unscoped() deliberately.");
            }
        }
    }

    /**
     * {@code INSERT ... SELECT} is accepted only in the copy-within-scope shape: each checked
     * target column is fed by the same filter's checked column of the source FROM relation, and
     * the source carries the same scope, so its rows already passed the read predicate.
     */
    void checkInsertSelect(TableLike<?> table, Scope scope, List<Column<?>> targetColumns, SelectBuilder source) {
        TablePlan plan = writePlan(table);
        if (plan.exempt()) {
            return;
        }
        TableRef ref = TableRef.of(table);
        for (List<Checked> group : plan.groupChecks()) {
            for (Checked member : group) {
                if (scopeValue(member.filter(), scope, ref) != null) {
                    throw new IllegalStateException("INSERT ... SELECT into " + ref + " cannot check anyOf"
                            + columnNames(group) + "; use set(...) or values(...).");
                }
            }
        }
        List<Checked> active = new ArrayList<>();
        for (Checked check : plan.checked()) {
            if (scopeValue(check.filter(), scope, ref) != null) {
                active.add(check);
            }
        }
        if (active.isEmpty()) {
            return;
        }
        if (targetColumns.isEmpty()) {
            throw new IllegalStateException("INSERT ... SELECT into " + ref + " under a scope requires columns(...).");
        }
        QueryFilters sourceFilters = source.filters();
        if (sourceFilters == null || sourceFilters.policy() != this || !Objects.equals(sourceFilters.scope(), scope)) {
            throw new IllegalStateException("INSERT ... SELECT into " + ref
                    + " requires a source query built from the same scoped context.");
        }
        TableLike<?> from = source.fromRelation();
        TableRef fromRef = from == null ? null : TableRef.of(from);
        TablePlan sourcePlan = fromRef == null ? null : plans.get(TableKey.of(fromRef.relation()));
        for (Checked check : active) {
            int index = indexOf(targetColumns, check.column());
            if (index < 0) {
                throw new IllegalStateException("INSERT ... SELECT into " + ref + " must include "
                        + check.column().name() + " in columns(...) and project the source column bound to filter '"
                        + check.filter().name() + "'.");
            }
            Column<?> projected = source.projectedColumn(index);
            Checked sourceChecked = null;
            if (sourcePlan != null) {
                for (Checked candidate : sourcePlan.checked()) {
                    if (candidate.filter().equals(check.filter())) {
                        sourceChecked = candidate;
                    }
                }
            }
            String projectedName = TableRef.terminalIdentifier(projected.name());
            if (sourceChecked == null || !projectedName.equals(TableRef.terminalIdentifier(sourceChecked.column().name()))) {
                throw new IllegalStateException("INSERT ... SELECT into " + ref + ": projection " + (index + 1)
                        + " must be the source FROM relation's column bound to filter '" + check.filter().name() + "'.");
            }
            int dot = projected.name().lastIndexOf('.');
            if (dot >= 0) {
                String qualifier = projected.name().substring(0, dot);
                if (!(from instanceof AliasedTable<?> aliased) || !aliased.alias().equals(qualifier)) {
                    throw new IllegalStateException("INSERT ... SELECT into " + ref + ": " + projected.name()
                            + " must be qualified by the source FROM alias.");
                }
            } else if (source.hasJoins()) {
                throw new IllegalStateException("INSERT ... SELECT into " + ref + ": qualify " + projectedName
                        + " with the source FROM alias when the source has joins.");
            }
        }
    }

    private TablePlan writePlan(TableLike<?> table) {
        TableKey key = TableKey.of(table);
        TablePlan plan = plans.get(key);
        if (plan == null) {
            throw new IllegalStateException("Relation " + key + " is not in the FilterPolicy catalog. Add it to the "
                    + "catalog and bind or exempt it, or query it through DSLContext.unscoped().");
        }
        return plan;
    }

    /** The scope's value for a filter; {@code null} when the filter is skipped, otherwise never null. */
    private static Object scopeValue(Filter<?> filter, Scope scope, TableRef ref) {
        if (scope == null) {
            throw new IllegalStateException("Filter '" + filter.name() + "' applies to " + ref
                    + " but the context has no Scope. Call DSLContext.scoped(Scope) or unscoped().");
        }
        if (scope.isSkipped(filter)) {
            return null;
        }
        Object value = scope.valueOf(filter);
        if (value == null) {
            throw new IllegalStateException("Filter '" + filter.name() + "' has no value in the current Scope "
                    + "for " + ref + ". Add Scope.with(" + filter.name() + ", value) or Scope.skip("
                    + filter.name() + ").");
        }
        return value;
    }

    private static void requireCompatible(Object value, Column<?> column, TableRef ref) {
        if (value instanceof Scope.Values multi) {
            for (Object element : multi.values()) {
                requireCompatible(element, column, ref);
            }
            return;
        }
        if (!SqlTypes.acceptsValue(value, column.sqlType())) {
            throw new IllegalStateException("Scope value of type " + value.getClass().getSimpleName()
                    + " cannot bind to column " + TableRef.terminalIdentifier(column.name()) + " (" + column.sqlType()
                    + ") of " + ref + ".");
        }
    }

    private static void requireWithinScope(Object assigned, Checked check, Object scopeValue, TableRef ref, String verb) {
        rejectExpression(assigned, check, ref, verb);
        if (!withinScope(assigned, scopeValue, check.column().sqlType())) {
            throw new IllegalStateException(verb + ref + " assigns " + check.column().name()
                    + " a value outside the current scope for filter '" + check.filter().name() + "'.");
        }
    }

    private static void rejectExpression(Object assigned, Checked check, TableRef ref, String verb) {
        if (assigned instanceof Column<?>) {
            throw new IllegalStateException(verb + ref + " assigns " + check.column().name()
                    + " an expression; a column bound to filter '" + check.filter().name() + "' needs a value.");
        }
    }

    private static boolean withinScope(Object assigned, Object scopeValue, SQLType sqlType) {
        if (assigned == null) {
            return false;
        }
        if (scopeValue instanceof Scope.Values multi) {
            for (Object candidate : multi.values()) {
                if (SqlTypes.sameValue(assigned, candidate, sqlType)) {
                    return true;
                }
            }
            return false;
        }
        return SqlTypes.sameValue(assigned, scopeValue, sqlType);
    }

    private static int indexOf(List<Column<?>> columns, Column<?> target) {
        String name = TableRef.terminalIdentifier(target.name());
        for (int i = 0; i < columns.size(); i++) {
            if (TableRef.terminalIdentifier(columns.get(i).name()).equals(name)) {
                return i;
            }
        }
        return -1;
    }

    private static String columnNames(List<Checked> checks) {
        StringJoiner joiner = new StringJoiner(", ", "(", ")");
        for (Checked check : checks) {
            joiner.add(check.column().name());
        }
        return joiner.toString();
    }

    // ---------------------------------------------------------------- builder

    public static final class Builder {

        private final Map<TableKey, TableLike<?>> catalog = new LinkedHashMap<>();
        private final Set<Filter<?>> filters = new LinkedHashSet<>();
        private final List<List<Filter<?>>> groups = new ArrayList<>();
        private final Map<Filter<?>, String> conventions = new HashMap<>();
        private final Set<Filter<?>> derived = new HashSet<>();
        private final Map<TableKey, Map<Filter<?>, Object>> explicit = new HashMap<>();
        private final Set<TableKey> exemptRelations = new HashSet<>();
        private final Map<TableKey, Set<Filter<?>>> exemptFilters = new HashMap<>();
        private int maxDepth = 2;

        private Builder(Collection<? extends TableLike<?>> relations) {
            for (TableLike<?> relation : relations) {
                Objects.requireNonNull(relation, "catalog relation");
                if (TableRef.of(relation) == null || relation instanceof AliasedTable<?>) {
                    throw new IllegalArgumentException("catalog entries must be Table or View descriptors: " + relation);
                }
                TableKey key = TableKey.of(relation);
                if (catalog.putIfAbsent(key, relation) != null) {
                    throw new IllegalArgumentException("catalog lists " + key + " twice");
                }
            }
        }

        /** Binds the filter on every catalog relation that has a column with this physical name. */
        public Builder byColumn(Filter<?> filter, String columnName) {
            register(filter);
            if (columnName == null || columnName.isBlank()) {
                throw new IllegalArgumentException("columnName must not be blank");
            }
            if (conventions.putIfAbsent(filter, columnName) != null) {
                throw new IllegalArgumentException("byColumn already declared for filter '" + filter.name() + "'");
            }
            return this;
        }

        /**
         * Lets unbound tables reach the filter through the shortest unambiguous foreign-key path
         * to a bound table, within {@link #maxDepth(int)}.
         */
        public Builder derive(Filter<?>... filters) {
            for (Filter<?> filter : Objects.requireNonNull(filters, "filters")) {
                register(filter);
                derived.add(filter);
            }
            return this;
        }

        public <T> Builder bind(TableLike<?> relation, Filter<T> filter, Column<T> column) {
            return putExplicit(relation, filter, new ColumnBinding(Objects.requireNonNull(column, "column")));
        }

        public <T> Builder bind(TableLike<?> relation, Filter<T> filter, FilterPath<T> path) {
            return putExplicit(relation, filter, Objects.requireNonNull(path, "path"));
        }

        @SuppressWarnings("unchecked")
        public <T> Builder bind(TableLike<?> relation, Filter<T> filter, BiFunction<TableRef, T, Condition> condition) {
            Objects.requireNonNull(condition, "condition");
            return putExplicit(relation, filter, new ValueBinding((BiFunction<TableRef, Object, Condition>) condition));
        }

        public Builder bind(TableLike<?> relation, Filter<Void> filter, Function<TableRef, Condition> condition) {
            Objects.requireNonNull(condition, "condition");
            return putExplicit(relation, filter, new PredicateBinding(condition));
        }

        /** Declares alternatives: a row passes when any member's condition holds. */
        public Builder anyOf(Filter<?>... members) {
            Objects.requireNonNull(members, "members");
            if (members.length < 2) {
                throw new IllegalArgumentException("anyOf requires at least two filters");
            }
            List<Filter<?>> group = new ArrayList<>(members.length);
            for (Filter<?> member : members) {
                register(member);
                if (!member.requiresValue()) {
                    throw new IllegalArgumentException("anyOf members must be value filters: '" + member.name() + "'");
                }
                for (List<Filter<?>> existing : groups) {
                    if (existing.contains(member)) {
                        throw new IllegalArgumentException("filter '" + member.name() + "' is already in an anyOf group");
                    }
                }
                if (group.contains(member)) {
                    throw new IllegalArgumentException("anyOf lists '" + member.name() + "' twice");
                }
                group.add(member);
            }
            groups.add(List.copyOf(group));
            return this;
        }

        /** Excludes relations from every filter, for reference data such as countries. */
        public Builder exempt(TableLike<?>... relations) {
            for (TableLike<?> relation : Objects.requireNonNull(relations, "relations")) {
                exemptRelations.add(catalogKey(relation));
            }
            return this;
        }

        /** Excludes one relation from specific filters only. */
        public Builder exemptFrom(TableLike<?> relation, Filter<?>... filters) {
            TableKey key = catalogKey(relation);
            Objects.requireNonNull(filters, "filters");
            if (filters.length == 0) {
                throw new IllegalArgumentException("exemptFrom(relation, filters...) requires at least one filter");
            }
            for (Filter<?> filter : filters) {
                register(filter);
                exemptFilters.computeIfAbsent(key, k -> new HashSet<>()).add(filter);
            }
            return this;
        }

        /** Maximum number of foreign-key hops a derived path may take (default 2). */
        public Builder maxDepth(int depth) {
            if (depth < 1) {
                throw new IllegalArgumentException("maxDepth must be >= 1");
            }
            this.maxDepth = depth;
            return this;
        }

        public FilterPolicy build() {
            List<String> problems = new ArrayList<>();
            Map<TableKey, Map<Filter<?>, Binding>> resolved = new LinkedHashMap<>();
            for (TableKey key : catalog.keySet()) {
                resolved.put(key, new LinkedHashMap<>());
            }

            // Pass 1: explicit non-path bindings and column conventions.
            for (TableKey key : catalog.keySet()) {
                TableLike<?> relation = catalog.get(key);
                Map<Filter<?>, Object> declared = explicit.getOrDefault(key, Map.of());
                for (Filter<?> filter : filters) {
                    if (isExempt(key, filter)) {
                        if (declared.containsKey(filter)) {
                            problems.add(key + ": filter '" + filter.name() + "' is both bound and exempt");
                        }
                        continue;
                    }
                    Object binding = declared.get(filter);
                    if (binding instanceof Binding direct) {
                        resolved.get(key).put(filter, direct);
                        continue;
                    }
                    if (binding != null) {
                        continue; // path, pass 2
                    }
                    String columnName = conventions.get(filter);
                    if (columnName != null) {
                        Column<?> column = findColumn(relation, columnName);
                        if (column != null) {
                            resolved.get(key).put(filter, new ColumnBinding(column));
                        }
                    }
                }
            }

            // Pass 2: explicit paths, whose terminals may be pass-1 bindings of other tables.
            Map<TableKey, Set<Filter<?>>> alreadyReported = new HashMap<>();
            for (TableKey key : catalog.keySet()) {
                for (Map.Entry<Filter<?>, Object> entry : explicit.getOrDefault(key, Map.of()).entrySet()) {
                    if (entry.getValue() instanceof FilterPath<?> path && !isExempt(key, entry.getKey())) {
                        Binding binding = resolvePath(key, entry.getKey(), path, resolved, problems);
                        if (binding != null) {
                            resolved.get(key).put(entry.getKey(), binding);
                        } else {
                            alreadyReported.computeIfAbsent(key, k -> new HashSet<>()).add(entry.getKey());
                        }
                    }
                }
            }

            // Pass 3: derive paths for what is still unbound.
            Map<TableKey, Map<Filter<?>, Binding>> derivedBindings = new HashMap<>();
            for (TableKey key : catalog.keySet()) {
                for (Filter<?> filter : derived) {
                    if (isExempt(key, filter) || resolved.get(key).containsKey(filter)) {
                        continue;
                    }
                    int reported = problems.size();
                    Binding binding = deriveBinding(key, filter, resolved, problems);
                    if (binding != null) {
                        derivedBindings.computeIfAbsent(key, k -> new HashMap<>()).put(filter, binding);
                    } else if (problems.size() > reported) {
                        alreadyReported.computeIfAbsent(key, k -> new HashSet<>()).add(filter);
                    }
                }
            }
            for (Map.Entry<TableKey, Map<Filter<?>, Binding>> entry : derivedBindings.entrySet()) {
                resolved.get(entry.getKey()).putAll(entry.getValue());
            }

            // A filter bound to columns of different SQL types cannot carry one scope value.
            Map<Filter<?>, Map.Entry<TableKey, Column<?>>> firstColumns = new HashMap<>();
            for (TableKey key : catalog.keySet()) {
                for (Map.Entry<Filter<?>, Binding> entry : resolved.get(key).entrySet()) {
                    if (entry.getValue() instanceof ColumnBinding column) {
                        Map.Entry<TableKey, Column<?>> first = firstColumns.putIfAbsent(entry.getKey(), Map.entry(key, column.column()));
                        if (first != null && first.getValue().sqlType() != column.column().sqlType()) {
                            problems.add("filter '" + entry.getKey().name() + "' binds columns of different SQL types: "
                                    + first.getKey() + "." + first.getValue().name() + " " + first.getValue().sqlType()
                                    + ", " + key + "." + column.column().name() + " " + column.column().sqlType());
                        }
                    }
                }
            }

            // Validation: standalone value filters are fail-closed; groups need one member per table.
            Set<Filter<?>> grouped = new HashSet<>();
            groups.forEach(grouped::addAll);
            Map<TableKey, TablePlan> plans = new LinkedHashMap<>();
            for (TableKey key : catalog.keySet()) {
                if (exemptRelations.contains(key)) {
                    plans.put(key, TablePlan.exemptPlan());
                    continue;
                }
                Map<Filter<?>, Binding> bindings = resolved.get(key);
                List<Term> terms = new ArrayList<>();
                List<String> exemptNames = new ArrayList<>();
                for (Filter<?> filter : filters) {
                    if (exemptFilters.getOrDefault(key, Set.of()).contains(filter)) {
                        exemptNames.add(filter.name());
                    }
                }
                for (Filter<?> filter : filters) {
                    if (grouped.contains(filter)) {
                        continue;
                    }
                    Binding binding = bindings.get(filter);
                    if (binding != null) {
                        terms.add(new Leaf(filter, binding));
                    } else if (filter.requiresValue() && !isExempt(key, filter)
                            && !alreadyReported.getOrDefault(key, Set.of()).contains(filter)) {
                        problems.add(key + ": no binding for filter '" + filter.name() + "'"
                                + (derived.contains(filter) ? " within maxDepth " + maxDepth + " hops" : "")
                                + ". Bind it, derive it, or exempt the relation.");
                    }
                }
                for (List<Filter<?>> group : groups) {
                    List<Leaf> members = new ArrayList<>();
                    boolean allExempt = true;
                    for (Filter<?> member : group) {
                        Binding binding = bindings.get(member);
                        if (binding != null) {
                            members.add(new Leaf(member, binding));
                        }
                        allExempt &= isExempt(key, member);
                    }
                    if (!members.isEmpty()) {
                        terms.add(new Group(List.copyOf(members)));
                    } else if (!allExempt) {
                        problems.add(key + ": none of anyOf" + names(group) + " is bound. Bind one member or exempt them.");
                    }
                }
                List<Checked> checked = new ArrayList<>();
                List<List<Checked>> groupChecks = new ArrayList<>();
                List<Filter<?>> uncheckedFilters = new ArrayList<>();
                List<String> writeNotes = new ArrayList<>();
                for (Filter<?> filter : filters) {
                    if (!filter.requiresValue() || grouped.contains(filter)) {
                        continue;
                    }
                    Binding binding = bindings.get(filter);
                    if (binding instanceof ColumnBinding column) {
                        checked.add(new Checked(filter, column.column()));
                        writeNotes.add(column.column().name() + " checked, filled when unset");
                    } else if (binding != null) {
                        uncheckedFilters.add(filter);
                        writeNotes.add(filter.name() + " unchecked (" + (binding instanceof PathBinding ? "path" : "custom") + ")");
                    }
                }
                for (List<Filter<?>> group : groups) {
                    List<Checked> members = new ArrayList<>();
                    List<Filter<?>> bound = new ArrayList<>();
                    for (Filter<?> member : group) {
                        Binding binding = bindings.get(member);
                        if (binding == null) {
                            continue;
                        }
                        bound.add(member);
                        if (binding instanceof ColumnBinding column) {
                            members.add(new Checked(member, column.column()));
                        }
                    }
                    if (bound.isEmpty()) {
                        continue;
                    }
                    if (members.size() == bound.size()) {
                        groupChecks.add(List.copyOf(members));
                        writeNotes.add("anyOf" + names(group) + ": " + columnNames(members) + " checked");
                    } else {
                        uncheckedFilters.addAll(bound);
                        writeNotes.add("anyOf" + names(group) + " unchecked (custom)");
                    }
                }
                plans.put(key, new TablePlan(List.copyOf(terms), false, List.copyOf(exemptNames),
                        List.copyOf(checked), List.copyOf(groupChecks), List.copyOf(uncheckedFilters), List.copyOf(writeNotes)));
            }

            if (!problems.isEmpty()) {
                StringBuilder message = new StringBuilder("FilterPolicy has " + problems.size() + " problem(s):");
                for (String problem : problems) {
                    message.append("\n  - ").append(problem);
                }
                throw new IllegalStateException(message.toString());
            }
            return new FilterPolicy(Collections.unmodifiableMap(plans));
        }

        private Builder putExplicit(TableLike<?> relation, Filter<?> filter, Object binding) {
            TableKey key = catalogKey(relation);
            register(filter);
            Map<Filter<?>, Object> bindings = explicit.computeIfAbsent(key, k -> new LinkedHashMap<>());
            if (bindings.putIfAbsent(filter, binding) != null) {
                throw new IllegalArgumentException(key + ": filter '" + filter.name() + "' is already bound");
            }
            return this;
        }

        private void register(Filter<?> filter) {
            filters.add(Objects.requireNonNull(filter, "filter"));
        }

        private TableKey catalogKey(TableLike<?> relation) {
            Objects.requireNonNull(relation, "relation");
            TableKey key = TableKey.of(relation);
            if (!catalog.containsKey(key)) {
                throw new IllegalArgumentException(key + " is not in the policy catalog");
            }
            return key;
        }

        private boolean isExempt(TableKey key, Filter<?> filter) {
            return exemptRelations.contains(key) || exemptFilters.getOrDefault(key, Set.of()).contains(filter);
        }

        private Binding resolvePath(TableKey start, Filter<?> filter, FilterPath<?> path,
                                    Map<TableKey, Map<Filter<?>, Binding>> resolved, List<String> problems) {
            List<Hop> hops = new ArrayList<>();
            TableKey current = start;
            for (Column<?> hopColumn : path.hops()) {
                ForeignKey<?, ?> foreignKey = findForeignKey(catalog.get(current), hopColumn, problems, start, filter);
                if (foreignKey == null) {
                    return null;
                }
                TableKey next = TableKey.of(foreignKey.referencedTable());
                if (!catalog.containsKey(next)) {
                    problems.add(start + ": path for '" + filter.name() + "' reaches " + next + ", which is not in the catalog");
                    return null;
                }
                hops.add(new Hop(foreignKey.localColumns(), foreignKey.referencedTable(), foreignKey.referencedColumns()));
                current = next;
            }
            Binding terminal;
            if (path.target() != null) {
                if (findColumn(catalog.get(current), path.target().name()) == null) {
                    problems.add(start + ": path for '" + filter.name() + "' ends at " + current
                            + ", which has no column " + path.target().name());
                    return null;
                }
                terminal = new ColumnBinding(path.target());
            } else {
                terminal = resolved.get(current).get(filter);
                if (terminal == null || terminal instanceof PathBinding) {
                    problems.add(start + ": path for '" + filter.name() + "' ends at " + current
                            + ", which has no direct binding for it; add .to(column)");
                    return null;
                }
            }
            return new PathBinding(List.copyOf(hops), terminal, false);
        }

        /**
         * Breadth-first search over foreign keys for the nearest table with a non-derived binding.
         * Every route is kept so that two foreign keys to the same table surface as an ambiguity
         * instead of silently choosing one.
         */
        private Binding deriveBinding(TableKey start, Filter<?> filter,
                                      Map<TableKey, Map<Filter<?>, Binding>> resolved, List<String> problems) {
            Map<TableKey, List<List<Hop>>> frontier = new LinkedHashMap<>();
            frontier.put(start, List.of(List.of()));
            Set<TableKey> visited = new HashSet<>(Set.of(start));
            for (int depth = 1; depth <= maxDepth; depth++) {
                Map<TableKey, List<List<Hop>>> next = new LinkedHashMap<>();
                for (Map.Entry<TableKey, List<List<Hop>>> entry : frontier.entrySet()) {
                    for (ForeignKey<?, ?> foreignKey : publicForeignKeys(catalog.get(entry.getKey()))) {
                        TableKey target = TableKey.of(foreignKey.referencedTable());
                        if (visited.contains(target) || !catalog.containsKey(target)) {
                            continue;
                        }
                        Hop hop = new Hop(foreignKey.localColumns(), foreignKey.referencedTable(), foreignKey.referencedColumns());
                        for (List<Hop> route : entry.getValue()) {
                            List<Hop> extended = new ArrayList<>(route);
                            extended.add(hop);
                            next.computeIfAbsent(target, k -> new ArrayList<>()).add(extended);
                        }
                    }
                }
                List<TableKey> candidates = new ArrayList<>();
                for (TableKey target : next.keySet()) {
                    Binding terminal = resolved.get(target).get(filter);
                    if (terminal != null && !isExempt(target, filter)) {
                        candidates.add(target);
                    }
                }
                if (candidates.size() > 1) {
                    candidates.sort(Comparator.comparing(TableKey::toString));
                    problems.add(start + ": derived path for '" + filter.name() + "' is ambiguous between "
                            + candidates + "; bind it explicitly with via(...)");
                    return null;
                }
                if (candidates.size() == 1) {
                    TableKey target = candidates.get(0);
                    List<List<Hop>> paths = next.get(target);
                    if (paths.size() > 1) {
                        problems.add(start + ": derived path for '" + filter.name() + "' has " + paths.size()
                                + " routes to " + target + "; bind it explicitly with via(...)");
                        return null;
                    }
                    Binding terminal = resolved.get(target).get(filter);
                    List<Hop> hops = new ArrayList<>(paths.get(0));
                    if (terminal instanceof PathBinding explicitPath) {
                        hops.addAll(explicitPath.hops());
                        terminal = explicitPath.terminal();
                    }
                    return new PathBinding(List.copyOf(hops), terminal, true);
                }
                visited.addAll(next.keySet());
                frontier = next;
                if (frontier.isEmpty()) {
                    break;
                }
            }
            return null;
        }

        private static ForeignKey<?, ?> findForeignKey(TableLike<?> relation, Column<?> localColumn,
                                                       List<String> problems, TableKey start, Filter<?> filter) {
            List<ForeignKey<?, ?>> matches = new ArrayList<>();
            for (ForeignKey<?, ?> foreignKey : publicForeignKeys(relation)) {
                if (foreignKey.localColumns().size() == 1 && foreignKey.localColumns().get(0).equals(localColumn)) {
                    matches.add(foreignKey);
                }
            }
            if (matches.size() == 1) {
                return matches.get(0);
            }
            problems.add(start + ": path for '" + filter.name() + "' " + (matches.isEmpty() ? "found no" : "found several")
                    + " single-column foreign key on " + TableKey.of(relation) + " for column " + localColumn.name());
            return null;
        }

        private static String names(List<Filter<?>> group) {
            StringJoiner joiner = new StringJoiner(", ", "(", ")");
            for (Filter<?> filter : group) {
                joiner.add(filter.name());
            }
            return joiner.toString();
        }
    }

    // ---------------------------------------------------------------- descriptor reflection

    static Column<?> findColumn(TableLike<?> relation, String physicalName) {
        for (Column<?> column : publicFields(relation, Column.class)) {
            if (TableRef.terminalIdentifier(column.name()).equals(physicalName)) {
                return column;
            }
        }
        return null;
    }

    static List<ForeignKey<?, ?>> publicForeignKeys(TableLike<?> relation) {
        List<ForeignKey<?, ?>> keys = new ArrayList<>();
        for (ForeignKey<?, ?> key : publicFields(relation, ForeignKey.class)) {
            keys.add(key);
        }
        return keys;
    }

    /** Public instance fields of the given type, in field-name order: reflection order is unspecified. */
    private static <F> List<F> publicFields(TableLike<?> relation, Class<F> type) {
        Field[] fields = relation.getClass().getFields();
        Arrays.sort(fields, Comparator.comparing(Field::getName));
        List<F> values = new ArrayList<>();
        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers()) || !type.isAssignableFrom(field.getType())) {
                continue;
            }
            try {
                values.add(type.cast(field.get(relation)));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Unable to read field '" + field.getName() + "' on "
                        + relation.getClass().getName(), e);
            }
        }
        return values;
    }
}
