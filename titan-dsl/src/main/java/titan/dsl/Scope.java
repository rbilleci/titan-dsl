package titan.dsl;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable per-request values for automatic filters. Attach one to a context with
 * {@link DSLContext#scoped(Scope)}.
 *
 * <p>Every value filter a query needs must either have a value or be explicitly
 * {@link #skip(Filter) skipped}; a missing value is a rendering error, never a silently wider
 * query.</p>
 */
public final class Scope {

    private static final Scope EMPTY = new Scope(Map.of(), Set.of());

    private final Map<Filter<?>, Object> values;
    private final Set<Filter<?>> skipped;

    private Scope(Map<Filter<?>, Object> values, Set<Filter<?>> skipped) {
        this.values = values;
        this.skipped = skipped;
    }

    public static Scope empty() {
        return EMPTY;
    }

    public static <T> Scope of(Filter<T> filter, T value) {
        return EMPTY.with(filter, value);
    }

    /** Binds a single value; renders as {@code column = ?}. */
    public <T> Scope with(Filter<T> filter, T value) {
        requireValueFilter(filter);
        Objects.requireNonNull(value, "value");
        return copyWith(filter, value);
    }

    /**
     * Binds several accepted values; renders as {@code column IN (?, ...)}. Supported by column
     * bindings only: a lambda binding receives a single value of its filter's type.
     */
    public <T> Scope withAny(Filter<T> filter, Collection<? extends T> values) {
        requireValueFilter(filter);
        Objects.requireNonNull(values, "values");
        for (T value : values) {
            Objects.requireNonNull(value, "values must not contain null");
        }
        List<Object> copy = List.copyOf(values);
        return copyWith(filter, new Values(copy));
    }

    /** Declares that this request intentionally applies no constraint for the filter. */
    public Scope skip(Filter<?> filter) {
        Objects.requireNonNull(filter, "filter");
        Map<Filter<?>, Object> newValues = new LinkedHashMap<>(values);
        newValues.remove(filter);
        Set<Filter<?>> newSkipped = new HashSet<>(skipped);
        newSkipped.add(filter);
        return new Scope(Map.copyOf(newValues), Set.copyOf(newSkipped));
    }

    public boolean has(Filter<?> filter) {
        return values.containsKey(filter);
    }

    public boolean isSkipped(Filter<?> filter) {
        return skipped.contains(filter);
    }

    /** The bound value: a single object or, after {@link #withAny}, a {@link Values} set. */
    Object valueOf(Filter<?> filter) {
        return values.get(filter);
    }

    /** Distinguishes a {@link #withAny} set from a single value whose type happens to be a list. */
    record Values(List<Object> values) {
    }

    private Scope copyWith(Filter<?> filter, Object value) {
        Map<Filter<?>, Object> newValues = new LinkedHashMap<>(values);
        newValues.put(filter, value);
        Set<Filter<?>> newSkipped = new HashSet<>(skipped);
        newSkipped.remove(filter);
        return new Scope(Map.copyOf(newValues), Set.copyOf(newSkipped));
    }

    private static void requireValueFilter(Filter<?> filter) {
        Objects.requireNonNull(filter, "filter");
        if (!filter.requiresValue()) {
            throw new IllegalArgumentException("predicate filter '" + filter.name() + "' takes no value");
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Scope scope && values.equals(scope.values) && skipped.equals(scope.skipped);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values, skipped);
    }

    @Override
    public String toString() {
        return "Scope" + values + (skipped.isEmpty() ? "" : " skip" + skipped);
    }
}
