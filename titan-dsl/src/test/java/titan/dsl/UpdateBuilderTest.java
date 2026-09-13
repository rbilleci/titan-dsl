package titan.dsl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class UpdateBuilderTest {

    private static final AccountsTable ACCOUNTS = new AccountsTable();

    @Test
    void updateSetWhereRendersExpectedSql() {
        String sql = DSL.update(ACCOUNTS)
                .set(ACCOUNTS.ACTIVE, false)
                .set(ACCOUNTS.EMAIL, "updated@example.com")
                .where(ACCOUNTS.ID.eq(42))
                .execute();

        assertEquals(
                "UPDATE public.accounts SET active = FALSE, email = 'updated@example.com' WHERE id = 42",
                sql);
    }

    @Test
    void updateSetExpressionValuedRendersServerSideArithmetic() {
        // G3 (spike B3): set(column, columnExpression) is an atomic server-side mutation
        // (version = version + 1); the value is not bound from the caller.
        String sql = DSL.update(ACCOUNTS)
                .set(ACCOUNTS.VERSION, ACCOUNTS.VERSION.add(1))
                .where(ACCOUNTS.ID.eq(42))
                .execute();

        assertEquals(
                "UPDATE public.accounts SET version = (version + 1) WHERE id = 42",
                sql);
    }

    @Test
    void updateSetExpressionValuedRendersForMySql() {
        String sql = DSL.update(ACCOUNTS)
                .set(ACCOUNTS.VERSION, ACCOUNTS.VERSION.subtract(2))
                .where(ACCOUNTS.ID.eq(7))
                .toSql(SqlDialect.MYSQL);

        assertEquals(
                "UPDATE public.accounts SET version = (version - 2) WHERE id = 7",
                sql);
    }

    @Test
    void updateWithReturningRendersExpectedSql() {
        String sql = DSL.update(ACCOUNTS)
                .set(ACCOUNTS.ACTIVE, false)
                .where(ACCOUNTS.ACTIVE.eq(true))
                .returning(ACCOUNTS.ID, ACCOUNTS.EMAIL)
                .execute();

        assertEquals(
                "UPDATE public.accounts SET active = FALSE WHERE active = TRUE RETURNING id, email",
                sql);
    }

    @Test
    void updateWithoutSetFailsFast() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> DSL.update(ACCOUNTS).where(ACCOUNTS.ID.eq(1)).execute());
        assertEquals("update requires at least one set(column, value)", ex.getMessage());
    }

    @Test
    void returningIsRejectedForMySqlDialect() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> DSL.update(ACCOUNTS)
                        .set(ACCOUNTS.ACTIVE, false)
                        .returning(ACCOUNTS.ID)
                        .toSql(SqlDialect.MYSQL));
        assertEquals("returning(...) is only supported for PostgreSQL updates", ex.getMessage());
    }

    private static final class AccountsTable extends Table<Object> {
        private final Column<Integer> ID = column("id", SQLType.INTEGER, Nullability.NOT_NULL);
        private final Column<String> EMAIL = column("email", SQLType.VARCHAR, Nullability.NOT_NULL);
        private final Column<Boolean> ACTIVE = column("active", SQLType.BOOLEAN, Nullability.NOT_NULL);
        private final Column<Integer> VERSION = column("version", SQLType.INTEGER, Nullability.NOT_NULL);

        private AccountsTable() {
            super("accounts", "public");
        }
    }
}
