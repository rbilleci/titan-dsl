package titan.dsl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class InsertBuilderTest {

    private static final AccountsTable ACCOUNTS = new AccountsTable();

    @Test
    void singleRowSetSyntaxRendersExpectedSql() {
        String sql = DSL.insertInto(ACCOUNTS)
                .set(ACCOUNTS.EMAIL, "ada@example.com")
                .set(ACCOUNTS.ACTIVE, true)
                .execute();

        assertEquals(
                "INSERT INTO public.accounts (email, active) VALUES ('ada@example.com', TRUE)",
                sql);
    }

    @Test
    void columnsValuesBatchSyntaxRendersExpectedSql() {
        String sql = DSL.insertInto(ACCOUNTS)
                .columns(ACCOUNTS.EMAIL, ACCOUNTS.ACTIVE)
                .values("ada@example.com", true)
                .values("linus@example.com", false)
                .execute();

        assertEquals(
                "INSERT INTO public.accounts (email, active) VALUES ('ada@example.com', TRUE), ('linus@example.com', FALSE)",
                sql);
    }

    @Test
    void insertSelectSyntaxRendersExpectedSql() {
        String sql = DSL.insertInto(ACCOUNTS)
                .columns(ACCOUNTS.EMAIL)
                .select(DSL.select(ACCOUNTS.EMAIL)
                        .from(ACCOUNTS)
                        .where(ACCOUNTS.ACTIVE.eq(true)))
                .execute();

        assertEquals(
                "INSERT INTO public.accounts (email) SELECT email FROM public.accounts WHERE active = TRUE",
                sql);
    }

    @Test
    void returningClauseRendersExpectedSql() {
        String sql = DSL.insertInto(ACCOUNTS)
                .set(ACCOUNTS.EMAIL, "ada@example.com")
                .set(ACCOUNTS.ACTIVE, true)
                .returning(ACCOUNTS.ID)
                .execute();

        assertEquals(
                "INSERT INTO public.accounts (email, active) VALUES ('ada@example.com', TRUE) RETURNING id",
                sql);
    }

    @Test
    void valuesCountMismatchFailsFast() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DSL.insertInto(ACCOUNTS)
                        .columns(ACCOUNTS.EMAIL, ACCOUNTS.ACTIVE)
                        .values("ada@example.com"));

        assertEquals("values(...) count must match columns(...) count", ex.getMessage());
    }

    @Test
    void onConflictDoUpdateRendersExpectedSql() {
        String sql = DSL.insertInto(ACCOUNTS)
                .set(ACCOUNTS.EMAIL, "ada@example.com")
                .set(ACCOUNTS.ACTIVE, true)
                .onConflict(ACCOUNTS.EMAIL)
                .doUpdate()
                .set(ACCOUNTS.ACTIVE, false)
                .execute();

        assertEquals(
                "INSERT INTO public.accounts (email, active) VALUES ('ada@example.com', TRUE) ON CONFLICT (email) DO UPDATE SET active = FALSE",
                sql);
    }

    @Test
    void onConflictDoUpdateRendersExpressionValuedSetPostgres() {
        // G3 (spike B3): expression-valued set(column, columnExpression) renders an atomic
        // server-side bump (version = version + 1) instead of binding a caller-supplied value.
        String sql = DSL.insertInto(ACCOUNTS)
                .set(ACCOUNTS.EMAIL, "ada@example.com")
                .set(ACCOUNTS.VERSION, 1)
                .onConflict(ACCOUNTS.EMAIL)
                .doUpdate()
                .set(ACCOUNTS.VERSION, ACCOUNTS.VERSION.add(1))
                .execute();

        assertEquals(
                "INSERT INTO public.accounts (email, version) VALUES ('ada@example.com', 1) "
                        + "ON CONFLICT (email) DO UPDATE SET version = (version + 1)",
                sql);
    }

    @Test
    void onConflictDoUpdateRendersExpressionValuedSetMySql() {
        String sql = DSL.insertInto(ACCOUNTS)
                .set(ACCOUNTS.EMAIL, "ada@example.com")
                .set(ACCOUNTS.VERSION, 1)
                .onConflict(ACCOUNTS.EMAIL)
                .doUpdate()
                .set(ACCOUNTS.VERSION, ACCOUNTS.VERSION.add(1))
                .toSql(SqlDialect.MYSQL);

        assertEquals(
                "INSERT INTO public.accounts (email, version) VALUES ('ada@example.com', 1) "
                        + "ON DUPLICATE KEY UPDATE version = (version + 1)",
                sql);
    }

    @Test
    void onConflictRequiresUpdateAssignments() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> DSL.insertInto(ACCOUNTS)
                        .set(ACCOUNTS.EMAIL, "ada@example.com")
                        .set(ACCOUNTS.ACTIVE, true)
                        .onConflict(ACCOUNTS.EMAIL)
                        .doUpdate()
                        .execute());

        assertEquals("onConflict(...).doUpdate().set(...) requires at least one set(...) assignment", ex.getMessage());
    }

    @Test
    void onConflictRendersMySqlDuplicateKeySyntaxWhenRequested() {
        String sql = DSL.insertInto(ACCOUNTS)
                .set(ACCOUNTS.EMAIL, "ada@example.com")
                .set(ACCOUNTS.ACTIVE, true)
                .onConflict(ACCOUNTS.EMAIL)
                .doUpdate()
                .set(ACCOUNTS.ACTIVE, false)
                .toSql(SqlDialect.MYSQL);

        assertEquals(
                "INSERT INTO public.accounts (email, active) VALUES ('ada@example.com', TRUE) ON DUPLICATE KEY UPDATE active = FALSE",
                sql);
    }

    @Test
    void mysqlReturningRendersPlainInsertWithoutSplicedSelect() {
        // The former "; SELECT LAST_INSERT_ID()" multi-statement splice is gone (audit D-4);
        // executors retrieve generated keys via Statement.RETURN_GENERATED_KEYS instead.
        String sql = DSL.insertInto(ACCOUNTS)
                .set(ACCOUNTS.EMAIL, "ada@example.com")
                .set(ACCOUNTS.ACTIVE, true)
                .returning(ACCOUNTS.ID)
                .toSql(SqlDialect.MYSQL);

        assertEquals(
                "INSERT INTO public.accounts (email, active) VALUES ('ada@example.com', TRUE)",
                sql);
    }

    @Test
    void mysqlReturningRejectsMultipleColumns() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> DSL.insertInto(ACCOUNTS)
                        .set(ACCOUNTS.EMAIL, "ada@example.com")
                        .set(ACCOUNTS.ACTIVE, true)
                        .returning(ACCOUNTS.ID, ACCOUNTS.EMAIL)
                        .toSql(SqlDialect.MYSQL));

        assertEquals("MySQL insert returning supports exactly one auto-increment column", ex.getMessage());
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
