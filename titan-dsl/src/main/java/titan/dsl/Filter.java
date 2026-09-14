package titan.dsl;

/**
 * A named, typed key for an automatic filter such as a tenant, organization, or user scope.
 *
 * <p>Filters are identity-equal: declare each one once as a constant and reuse it in a
 * {@link FilterPolicy} and in every {@link Scope}. The SQL type of the value comes from the
 * column a filter is bound to, not from the filter itself.</p>
 *
 * <p>A value filter ({@link #of(String)}) requires a value in the {@link Scope} of every query
 * and is fail-closed: a table without a binding or exemption is rejected when the policy is
 * built. A predicate filter ({@link #predicate(String)}) carries no value and applies only to
 * tables it is bound to, for cases like soft-delete flags.</p>
 */
public final class Filter<T> {

    private final String name;
    private final boolean valueless;

    private Filter(String name, boolean valueless) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("filter name must not be blank");
        }
        this.name = name;
        this.valueless = valueless;
    }

    public static <T> Filter<T> of(String name) {
        return new Filter<>(name, false);
    }

    public static Filter<Void> predicate(String name) {
        return new Filter<>(name, true);
    }

    public String name() {
        return name;
    }

    boolean requiresValue() {
        return !valueless;
    }

    @Override
    public String toString() {
        return name;
    }
}
