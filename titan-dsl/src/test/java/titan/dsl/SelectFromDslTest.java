package titan.dsl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class SelectFromDslTest {

    private static final class AccountTable extends Table<Object> {
        private static final AccountTable ACCOUNTS = new AccountTable();

        public final Column<Boolean> ACTIVE = column("active", SQLType.BOOLEAN, Nullability.NOT_NULL);
        public final Column<String> EMAIL = column("email", SQLType.VARCHAR, Nullability.NOT_NULL);
        public final Column<Integer> ID = column("id", SQLType.INTEGER, Nullability.NOT_NULL);

        private AccountTable() {
            super("accounts", "public");
        }
    }

    private static final class EmptyTable extends Table<Object> {
        private EmptyTable() {
            super("empty_table", "public");
        }
    }

    @Test
    void selectFromBuildsProjectionFromPublicColumnFields() {
        String sql = DSL.selectFrom(AccountTable.ACCOUNTS)
                .where(AccountTable.ACCOUNTS.ACTIVE.eq(true))
                .toSql();

        assertEquals(
                "SELECT active, email, id FROM public.accounts WHERE active = TRUE",
                sql);
    }

    @Test
    void selectFromRejectsNullTable() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> DSL.selectFrom(null));
        assertEquals("table must not be null", exception.getMessage());
    }

    @Test
    void selectFromRejectsTableWithoutPublicColumnFields() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> DSL.selectFrom(new EmptyTable()));

        assertEquals(
                "selectFrom requires at least one public Column field on table " + EmptyTable.class.getName(),
                exception.getMessage());
    }
}
