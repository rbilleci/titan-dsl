package titan.dsl;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SelectTupleTypingTest {

    @Test
    void selectOverloadsExposeTupleTypedBuilders() {
        Table<Object> accounts = new Table<>("public", "accounts");
        Column<Long> id = accounts.column("id", SQLType.BIGINT, Nullability.NOT_NULL);
        Column<String> email = accounts.column("email", SQLType.VARCHAR, Nullability.NULLABLE);
        Column<Boolean> active = accounts.column("active", SQLType.BOOLEAN, Nullability.NOT_NULL);
        Column<Integer> age = accounts.column("age", SQLType.INTEGER, Nullability.NULLABLE);
        Column<String> role = accounts.column("role", SQLType.VARCHAR, Nullability.NULLABLE);
        Column<Short> level = accounts.column("level", SQLType.SMALLINT, Nullability.NULLABLE);
        Column<String> region = accounts.column("region", SQLType.VARCHAR, Nullability.NULLABLE);
        Column<Integer> score = accounts.column("score", SQLType.INTEGER, Nullability.NULLABLE);
        Column<String> segment = accounts.column("segment", SQLType.VARCHAR, Nullability.NULLABLE);
        Column<Integer> rank = accounts.column("rank", SQLType.INTEGER, Nullability.NULLABLE);

        SelectBuilder1<Long> one = DSL.select(id);
        SelectBuilder2<Long, String> two = DSL.select(id, email);
        SelectBuilder3<Long, String, Boolean> three = DSL.select(id, email, active);
        SelectBuilder4<Long, String, Boolean, Integer> four = DSL.select(id, email, active, age);
        SelectBuilder5<Long, String, Boolean, Integer, String> five = DSL.select(id, email, active, age, role);
        SelectBuilder6<Long, String, Boolean, Integer, String, Short> six = DSL.select(id, email, active, age, role, level);
        SelectBuilder7<Long, String, Boolean, Integer, String, Short, String> seven =
                DSL.select(id, email, active, age, role, level, region);
        SelectBuilder8<Long, String, Boolean, Integer, String, Short, String, Integer> eight =
                DSL.select(id, email, active, age, role, level, region, score);
        SelectBuilder9<Long, String, Boolean, Integer, String, Short, String, Integer, String> nine =
                DSL.select(id, email, active, age, role, level, region, score, segment);
        SelectBuilder10<Long, String, Boolean, Integer, String, Short, String, Integer, String, Integer> ten =
                DSL.select(id, email, active, age, role, level, region, score, segment, rank);

        assertSame(Tuple1.class, one.tupleType());
        assertSame(Tuple2.class, two.tupleType());
        assertSame(Tuple3.class, three.tupleType());
        assertSame(Tuple4.class, four.tupleType());
        assertSame(Tuple5.class, five.tupleType());
        assertSame(Tuple6.class, six.tupleType());
        assertSame(Tuple7.class, seven.tupleType());
        assertSame(Tuple8.class, eight.tupleType());
        assertSame(Tuple9.class, nine.tupleType());
        assertSame(Tuple10.class, ten.tupleType());

        String sql = two.from(accounts).fetch();
        assertTrue(sql.startsWith("SELECT id, email FROM"));
        assertTrue(sql.contains("accounts"));
    }
}
