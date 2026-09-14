package titan.dsl;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Immutable, reusable SQL configuration. Holds no connection or mutable query state.
 * Each factory creates a fresh builder carrying this context's dialect.
 * Structured child queries render with the outer query's dialect; raw SQL is never translated.
 *
 * <p>Automatic filters: {@link #filters(FilterPolicy)} attaches a policy; {@link #scoped(Scope)}
 * derives a per-request context with the values to apply. Builders capture the scope when they
 * are created and add the policy's conditions when they render.</p>
 */
public final class DSLContext {
    private final SqlDialect dialect;
    private final FilterPolicy policy;
    private final Scope scope;
    private final Supplier<Scope> scopeSource;

    DSLContext(SqlDialect dialect) {
        this(dialect, null, null, null);
    }

    private DSLContext(SqlDialect dialect, FilterPolicy policy, Scope scope, Supplier<Scope> scopeSource) {
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        this.policy = policy;
        this.scope = scope;
        this.scopeSource = scopeSource;
    }

    public SqlDialect dialect() { return dialect; }

    /** Attaches a policy; every query then needs a {@link #scoped(Scope) scope} or {@link #unscoped()}. */
    public DSLContext filters(FilterPolicy policy) {
        return new DSLContext(dialect, Objects.requireNonNull(policy, "policy"), null, null);
    }

    /**
     * Attaches a policy whose scope is read from {@code scopeSource} each time a builder is
     * created, for applications that keep the request scope in a thread-local or request context.
     */
    public DSLContext filters(FilterPolicy policy, Supplier<Scope> scopeSource) {
        return new DSLContext(dialect, Objects.requireNonNull(policy, "policy"), null,
                Objects.requireNonNull(scopeSource, "scopeSource"));
    }

    /** A context for one request; the explicit scope takes precedence over any scope source. */
    public DSLContext scoped(Scope scope) {
        if (policy == null) {
            throw new IllegalStateException("scoped(...) requires a FilterPolicy; call filters(policy) first");
        }
        return new DSLContext(dialect, policy, Objects.requireNonNull(scope, "scope"), scopeSource);
    }

    /** A context that applies no automatic filters. Use it deliberately, for cross-scope work. */
    public DSLContext unscoped() {
        return new DSLContext(dialect, null, null, null);
    }

    /** The attached policy, or {@code null} when this context applies no automatic filters. */
    public FilterPolicy policy() { return policy; }

    private QueryFilters queryFilters() {
        if (policy == null) {
            return null;
        }
        Scope resolved = scope != null ? scope : scopeSource != null ? scopeSource.get() : null;
        return new QueryFilters(policy, resolved);
    }

    public SelectBuilder select(Column<?>... columns) {
        return new SelectBuilder(dialect, queryFilters(), columns);
    }

    public SelectBuilder selectFrom(TableLike<?> table) {
        return DSL.selectFrom(dialect, queryFilters(), table);
    }

    public <T1> SelectBuilder1<T1> select(Column<T1> column1) {
        return new SelectBuilder1<>(dialect, queryFilters(), column1);
    }

    public <T1, T2> SelectBuilder2<T1, T2> select(Column<T1> column1, Column<T2> column2) {
        return new SelectBuilder2<>(dialect, queryFilters(), column1, column2);
    }

    public <T1, T2, T3> SelectBuilder3<T1, T2, T3> select(Column<T1> column1, Column<T2> column2, Column<T3> column3) {
        return new SelectBuilder3<>(dialect, queryFilters(), column1, column2, column3);
    }

    public <T1, T2, T3, T4> SelectBuilder4<T1, T2, T3, T4> select(Column<T1> column1, Column<T2> column2, Column<T3> column3, Column<T4> column4) {
        return new SelectBuilder4<>(dialect, queryFilters(), column1, column2, column3, column4);
    }

    public <T1, T2, T3, T4, T5> SelectBuilder5<T1, T2, T3, T4, T5> select(Column<T1> column1, Column<T2> column2, Column<T3> column3, Column<T4> column4, Column<T5> column5) {
        return new SelectBuilder5<>(dialect, queryFilters(), column1, column2, column3, column4, column5);
    }

    public <T1, T2, T3, T4, T5, T6> SelectBuilder6<T1, T2, T3, T4, T5, T6> select(Column<T1> column1, Column<T2> column2, Column<T3> column3, Column<T4> column4, Column<T5> column5, Column<T6> column6) {
        return new SelectBuilder6<>(dialect, queryFilters(), column1, column2, column3, column4, column5, column6);
    }

    public <T1, T2, T3, T4, T5, T6, T7> SelectBuilder7<T1, T2, T3, T4, T5, T6, T7> select(Column<T1> column1, Column<T2> column2, Column<T3> column3, Column<T4> column4, Column<T5> column5, Column<T6> column6, Column<T7> column7) {
        return new SelectBuilder7<>(dialect, queryFilters(), column1, column2, column3, column4, column5, column6, column7);
    }

    public <T1, T2, T3, T4, T5, T6, T7, T8> SelectBuilder8<T1, T2, T3, T4, T5, T6, T7, T8> select(Column<T1> column1, Column<T2> column2, Column<T3> column3, Column<T4> column4, Column<T5> column5, Column<T6> column6, Column<T7> column7, Column<T8> column8) {
        return new SelectBuilder8<>(dialect, queryFilters(), column1, column2, column3, column4, column5, column6, column7, column8);
    }

    public <T1, T2, T3, T4, T5, T6, T7, T8, T9> SelectBuilder9<T1, T2, T3, T4, T5, T6, T7, T8, T9> select(Column<T1> column1, Column<T2> column2, Column<T3> column3, Column<T4> column4, Column<T5> column5, Column<T6> column6, Column<T7> column7, Column<T8> column8, Column<T9> column9) {
        return new SelectBuilder9<>(dialect, queryFilters(), column1, column2, column3, column4, column5, column6, column7, column8, column9);
    }

    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> SelectBuilder10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> select(Column<T1> column1, Column<T2> column2, Column<T3> column3, Column<T4> column4, Column<T5> column5, Column<T6> column6, Column<T7> column7, Column<T8> column8, Column<T9> column9, Column<T10> column10) {
        return new SelectBuilder10<>(dialect, queryFilters(), column1, column2, column3, column4, column5, column6, column7, column8, column9, column10);
    }

    public InsertBuilder insertInto(Table<?> table) { return new InsertBuilder(dialect, table); }
    public UpdateBuilder update(Table<?> table) { return new UpdateBuilder(dialect, queryFilters(), table); }
    public DeleteBuilder deleteFrom(Table<?> table) { return new DeleteBuilder(dialect, queryFilters(), table); }

    public WithBuilder with(CommonTableExpression<?>... expressions) {
        return new WithBuilder(dialect, queryFilters(), false, expressions);
    }

    public WithBuilder withRecursive(CommonTableExpression<?>... expressions) {
        return new WithBuilder(dialect, queryFilters(), true, expressions);
    }

    /** Creates a CTE step that retains a structured query snapshot, not pre-rendered SQL. */
    public DSL.CteNameStep name(String name) { return new DSL.CteNameStep(name, true); }
}
