package titan.dsl;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ColumnConditionTest {

    private final Column<Integer> id = new Column<>("id", SQLType.INTEGER, Nullability.NOT_NULL);
    private final Column<String> email = new Column<>("email", SQLType.VARCHAR, Nullability.NULLABLE);
    private final Table<Object> users = new Table<>("users", "public");
    private final Table<Object> orders = new Table<>("orders", "public");

    @Test
    void comparisonOperatorsEmitExpectedSql() {
        assertEquals("id = 42", id.eq(42).sql());
        assertEquals("id <> 42", id.ne(42).sql());
        assertEquals("id < 10", id.lt(10).sql());
        assertEquals("id > 10", id.gt(10).sql());
        assertEquals("id <= 10", id.le(10).sql());
        assertEquals("id >= 10", id.ge(10).sql());
        assertEquals("id BETWEEN 1 AND 9", id.between(1, 9).sql());
    }

    @Test
    void eqColumnRejectsMismatchedSqlTypes() {
        @SuppressWarnings({"rawtypes", "unchecked"})
        Column<Integer> mismatched = (Column<Integer>) (Column) email;

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> id.eqColumn(mismatched));
        assertEquals("Column comparison requires matching SQL types", ex.getMessage());
    }

    @Test
    void nullAndLikeAndInOperatorsEmitExpectedSql() {
        assertEquals("email IS NULL", email.eq(null).sql());
        assertEquals("email IS NOT NULL", email.ne(null).sql());
        assertEquals("email LIKE 'a%@example.com'", email.like("a%@example.com").sql());
        assertEquals("id IN (1, 2, 3)", id.in(List.of(1, 2, 3)).sql());
        assertEquals("id IN (4, 5, 6)", id.in(4, 5, 6).sql());
        assertEquals("1 = 0", id.in(List.of()).sql());
        assertEquals("1 = 0", id.in().sql());
    }

    @Test
    void likeRejectsNonTextColumnTypes() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> id.like("4%"));
        assertEquals("LIKE is only supported for textual column types", ex.getMessage());
    }

    @Test
    void conditionCompositionSupportsAndOrNot() {
        Condition active = Condition.of("active = TRUE");
        Condition recent = Condition.of("last_login > CURRENT_DATE - 7");

        assertEquals("(active = TRUE) AND (last_login > CURRENT_DATE - 7)", active.and(recent).sql());
        assertEquals("(active = TRUE) OR (last_login > CURRENT_DATE - 7)", active.or(recent).sql());
        assertEquals("NOT (active = TRUE)", active.not().sql());
    }

    @Test
    void sortDirectionBuildersEmitAscDesc() {
        assertEquals("email ASC", email.asc().sql());
        assertEquals("email DESC", email.desc().sql());
    }

    @Test
    void subqueryPredicatesEmitExpectedSql() {
        Column<Integer> userId = new Column<>("user_id", SQLType.INTEGER, Nullability.NOT_NULL);
        Column<Integer> orderUserId = new Column<>("orders.user_id", SQLType.INTEGER, Nullability.NOT_NULL);
        Column<Integer> customerId = new Column<>("customer_id", SQLType.INTEGER, Nullability.NOT_NULL);

        SelectBuilder inSubquery = DSL.select(orderUserId)
                .from(orders)
                .where(orderUserId.gt(100));
        assertEquals("user_id IN (SELECT orders.user_id FROM public.orders WHERE orders.user_id > 100)",
                userId.in(inSubquery).sql());

        SelectBuilder existsSubquery = DSL.select(customerId)
                .from(orders)
                .where(Condition.of("orders.customer_id = users.id"));
        assertEquals("EXISTS (SELECT customer_id FROM public.orders WHERE orders.customer_id = users.id)",
                DSL.exists(existsSubquery).sql());
        assertEquals("NOT (EXISTS (SELECT customer_id FROM public.orders WHERE orders.customer_id = users.id))",
                DSL.notExists(existsSubquery).sql());

        Column<Integer> scalar = DSL.scalar(
                DSL.select(orderUserId).from(orders).where(Condition.of("orders.customer_id = users.id")).limit(1),
                SQLType.INTEGER);
        assertEquals("(SELECT orders.user_id FROM public.orders WHERE orders.customer_id = users.id LIMIT 1)", scalar.name());
    }

    @Test
    void scalarSubqueryRejectsMultiColumnProjection() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DSL.scalar(DSL.select(id, email).from(users), SQLType.INTEGER));
        assertEquals("scalar subquery must project exactly one column", ex.getMessage());
    }

    @Test
    void inSubqueryRejectsMultiColumnProjection() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> id.in(DSL.select(id, new Column<>("other", SQLType.INTEGER, Nullability.NOT_NULL)).from(users)));
        assertEquals("IN subquery must project exactly one column", ex.getMessage());
    }

    @Test
    void inSubqueryRejectsTypeMismatch() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> id.in(DSL.select(email).from(users)));
        assertEquals("IN subquery projection type must match column type", ex.getMessage());
    }
}
