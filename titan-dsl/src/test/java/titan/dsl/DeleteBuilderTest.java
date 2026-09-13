package titan.dsl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DeleteBuilderTest {

    private static final AccountsTable ACCOUNTS = new AccountsTable();

    @Test
    void deleteWhereRendersExpectedSql() {
        String sql = DSL.deleteFrom(ACCOUNTS)
                .where(ACCOUNTS.ACTIVE.eq(false))
                .execute();

        assertEquals("DELETE FROM public.accounts WHERE active = FALSE", sql);
    }

    @Test
    void deleteWithReturningRendersExpectedSql() {
        String sql = DSL.deleteFrom(ACCOUNTS)
                .where(ACCOUNTS.ACTIVE.eq(false))
                .returning(ACCOUNTS.ID, ACCOUNTS.EMAIL)
                .execute();

        assertEquals(
                "DELETE FROM public.accounts WHERE active = FALSE RETURNING id, email",
                sql);
    }

    @Test
    void deleteWithReturningRequiresAtLeastOneColumn() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DSL.deleteFrom(ACCOUNTS).returning());
        assertEquals("returning(...) requires at least one column", ex.getMessage());
    }

    @Test
    void returningIsRejectedForMySqlDialect() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> DSL.deleteFrom(ACCOUNTS)
                        .where(ACCOUNTS.ID.eq(1))
                        .returning(ACCOUNTS.ID)
                        .toSql(SqlDialect.MYSQL));
        assertEquals("returning(...) is only supported for PostgreSQL deletes", ex.getMessage());
    }

    private static final class AccountsTable extends Table<Object> {
        private final Column<Integer> ID = column("id", SQLType.INTEGER, Nullability.NOT_NULL);
        private final Column<String> EMAIL = column("email", SQLType.VARCHAR, Nullability.NOT_NULL);
        private final Column<Boolean> ACTIVE = column("active", SQLType.BOOLEAN, Nullability.NOT_NULL);

        private AccountsTable() {
            super("accounts", "public");
        }
    }
}
