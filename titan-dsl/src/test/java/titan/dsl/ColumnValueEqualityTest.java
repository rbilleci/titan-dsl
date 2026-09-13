package titan.dsl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Audit D-9: {@link Column} value equality. Identity equality broke the LinkedHashMap-keyed
 * builder assignments — two equal-but-distinct instances of the same column (e.g. from two
 * {@code column("id", ...)} calls or two generated-catalog instances) produced two map entries,
 * rendering the same column twice in one INSERT/UPDATE. Also covers the ForeignKey array→List
 * migration (arrays broke record value-equality).
 */
class ColumnValueEqualityTest {

    private static final EventsTable EVENTS = new EventsTable();

    @Test
    void equalButDistinctColumnsAreEqual() {
        Column<Integer> first = EVENTS.column("id", SQLType.INTEGER, Nullability.NOT_NULL);
        Column<Integer> second = EVENTS.column("id", SQLType.INTEGER, Nullability.NOT_NULL);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, EVENTS.column("id", SQLType.BIGINT, Nullability.NOT_NULL));
        assertNotEquals(first, EVENTS.column("other", SQLType.INTEGER, Nullability.NOT_NULL));
        assertNotEquals(first, EVENTS.column("id", SQLType.INTEGER, Nullability.NULLABLE));
    }

    @Test
    void updateSetWithEqualButDistinctColumnOverwritesInsteadOfDuplicating() {
        Column<String> name = EVENTS.column("name", SQLType.VARCHAR, Nullability.NULLABLE);
        Column<String> equalName = EVENTS.column("name", SQLType.VARCHAR, Nullability.NULLABLE);

        String sql = DSL.update(EVENTS)
                .set(name, "first")
                .set(equalName, "second")
                .toSql(SqlDialect.POSTGRESQL);

        assertEquals("UPDATE public.events SET name = 'second'", sql);
    }

    @Test
    void insertSetWithEqualButDistinctColumnOverwritesInsteadOfDuplicating() {
        Column<String> name = EVENTS.column("name", SQLType.VARCHAR, Nullability.NULLABLE);
        Column<String> equalName = EVENTS.column("name", SQLType.VARCHAR, Nullability.NULLABLE);

        String sql = DSL.insertInto(EVENTS)
                .set(name, "first")
                .set(equalName, "second")
                .toSql(SqlDialect.POSTGRESQL);

        assertEquals("INSERT INTO public.events (name) VALUES ('second')", sql);
    }

    @Test
    void foreignKeyColumnsAreValueEqualLists() {
        ForeignKey<Object, Object> first = EVENTS.fkToSelf();
        ForeignKey<Object, Object> second = EVENTS.fkToSelf();

        assertEquals(first, second, "ForeignKey records with equal column lists must be equal (D-9)");
        assertEquals(List.of(EVENTS.PARENT_ID), first.localColumns());
        assertEquals(List.of(EVENTS.ID), first.referencedColumns());
    }

    private static final class EventsTable extends Table<Object> {
        private final Column<Integer> ID = column("id", SQLType.INTEGER, Nullability.NOT_NULL);
        private final Column<Integer> PARENT_ID = column("parent_id", SQLType.INTEGER, Nullability.NULLABLE);

        private EventsTable() {
            super("events", "public");
        }

        private ForeignKey<Object, Object> fkToSelf() {
            return foreignKey(PARENT_ID, this, ID);
        }
    }
}
