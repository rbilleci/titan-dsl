package titan.dsl;

import java.util.Objects;

/**
 * Immutable, reusable SQL configuration. Holds no connection or mutable query state.
 * Each factory creates a fresh builder carrying this context's dialect.
 * Structured child queries render with the outer query's dialect; raw SQL is never translated.
 */
public final class DSLContext {
    private final SqlDialect dialect;

    DSLContext(SqlDialect dialect) {
        this.dialect = Objects.requireNonNull(dialect, "dialect");
    }

    public SqlDialect dialect() { return dialect; }

    public SelectBuilder select(Column<?>... columns) {
        return new SelectBuilder(dialect, columns);
    }

    public SelectBuilder selectFrom(TableLike<?> table) {
        return DSL.selectFrom(dialect, table);
    }

    public <T1> SelectBuilder1<T1> select(Column<T1> column1) {
        return new SelectBuilder1<>(dialect, column1);
    }

    public <T1, T2> SelectBuilder2<T1, T2> select(Column<T1> column1, Column<T2> column2) {
        return new SelectBuilder2<>(dialect, column1, column2);
    }

    public <T1, T2, T3> SelectBuilder3<T1, T2, T3> select(Column<T1> column1, Column<T2> column2, Column<T3> column3) {
        return new SelectBuilder3<>(dialect, column1, column2, column3);
    }

    public <T1, T2, T3, T4> SelectBuilder4<T1, T2, T3, T4> select(Column<T1> column1, Column<T2> column2, Column<T3> column3, Column<T4> column4) {
        return new SelectBuilder4<>(dialect, column1, column2, column3, column4);
    }

    public <T1, T2, T3, T4, T5> SelectBuilder5<T1, T2, T3, T4, T5> select(Column<T1> column1, Column<T2> column2, Column<T3> column3, Column<T4> column4, Column<T5> column5) {
        return new SelectBuilder5<>(dialect, column1, column2, column3, column4, column5);
    }

    public <T1, T2, T3, T4, T5, T6> SelectBuilder6<T1, T2, T3, T4, T5, T6> select(Column<T1> column1, Column<T2> column2, Column<T3> column3, Column<T4> column4, Column<T5> column5, Column<T6> column6) {
        return new SelectBuilder6<>(dialect, column1, column2, column3, column4, column5, column6);
    }

    public <T1, T2, T3, T4, T5, T6, T7> SelectBuilder7<T1, T2, T3, T4, T5, T6, T7> select(Column<T1> column1, Column<T2> column2, Column<T3> column3, Column<T4> column4, Column<T5> column5, Column<T6> column6, Column<T7> column7) {
        return new SelectBuilder7<>(dialect, column1, column2, column3, column4, column5, column6, column7);
    }

    public <T1, T2, T3, T4, T5, T6, T7, T8> SelectBuilder8<T1, T2, T3, T4, T5, T6, T7, T8> select(Column<T1> column1, Column<T2> column2, Column<T3> column3, Column<T4> column4, Column<T5> column5, Column<T6> column6, Column<T7> column7, Column<T8> column8) {
        return new SelectBuilder8<>(dialect, column1, column2, column3, column4, column5, column6, column7, column8);
    }

    public <T1, T2, T3, T4, T5, T6, T7, T8, T9> SelectBuilder9<T1, T2, T3, T4, T5, T6, T7, T8, T9> select(Column<T1> column1, Column<T2> column2, Column<T3> column3, Column<T4> column4, Column<T5> column5, Column<T6> column6, Column<T7> column7, Column<T8> column8, Column<T9> column9) {
        return new SelectBuilder9<>(dialect, column1, column2, column3, column4, column5, column6, column7, column8, column9);
    }

    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> SelectBuilder10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> select(Column<T1> column1, Column<T2> column2, Column<T3> column3, Column<T4> column4, Column<T5> column5, Column<T6> column6, Column<T7> column7, Column<T8> column8, Column<T9> column9, Column<T10> column10) {
        return new SelectBuilder10<>(dialect, column1, column2, column3, column4, column5, column6, column7, column8, column9, column10);
    }

    public InsertBuilder insertInto(Table<?> table) { return new InsertBuilder(dialect, table); }
    public UpdateBuilder update(Table<?> table) { return new UpdateBuilder(dialect, table); }
    public DeleteBuilder deleteFrom(Table<?> table) { return new DeleteBuilder(dialect, table); }

    public WithBuilder with(CommonTableExpression<?>... expressions) {
        return new WithBuilder(dialect, false, expressions);
    }

    public WithBuilder withRecursive(CommonTableExpression<?>... expressions) {
        return new WithBuilder(dialect, true, expressions);
    }

    /** Creates a CTE step that retains a structured query snapshot, not pre-rendered SQL. */
    public DSL.CteNameStep name(String name) { return new DSL.CteNameStep(name, true); }
}
