package titan.dsl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class SelectBuilderFetchModesTest {

    private static final AccountsTable ACCOUNTS = new AccountsTable();

    @Test
    void fetchOneAppendsLimitWhenMissing() {
        String sql = DSL.select(ACCOUNTS.ID)
                .from(ACCOUNTS)
                .where(ACCOUNTS.ACTIVE.eq(true))
                .fetchOne();

        assertEquals("SELECT id FROM public.accounts WHERE active = TRUE LIMIT 1", sql);
    }

    @Test
    void fetchOneKeepsExplicitLimit() {
        String sql = DSL.select(ACCOUNTS.ID)
                .from(ACCOUNTS)
                .limit(5)
                .fetchOne();

        assertEquals("SELECT id FROM public.accounts LIMIT 5", sql);
    }

    @Test
    void fetchOneWithLockingKeepsLimitBeforeLockClause() {
        String sql = DSL.select(ACCOUNTS.ID)
                .from(ACCOUNTS)
                .forUpdate()
                .fetchOne();

        assertEquals("SELECT id FROM public.accounts LIMIT 1 FOR UPDATE", sql);
    }

    @Test
    void fetchCountWrapsBaseSelectWithoutOrderAndLimit() {
        String sql = DSL.select(ACCOUNTS.ID)
                .from(ACCOUNTS)
                .where(ACCOUNTS.ACTIVE.eq(true))
                .orderBy(ACCOUNTS.ID.desc())
                .limit(100)
                .fetchCount();

        assertEquals("SELECT COUNT(*) FROM (SELECT id FROM public.accounts WHERE active = TRUE) titan_count", sql);
    }

    @Test
    void fetchExistsWrapsBaseSelectWithoutOrderAndLimit() {
        String sql = DSL.select(ACCOUNTS.ID)
                .from(ACCOUNTS)
                .where(ACCOUNTS.ACTIVE.eq(true))
                .orderBy(ACCOUNTS.ID.desc())
                .limit(100)
                .fetchExists();

        assertEquals("SELECT EXISTS (SELECT id FROM public.accounts WHERE active = TRUE)", sql);
    }

    @Test
    void fetchExistsValueReturnsTypedBooleanForReadIntoLocal() {
        // Phase A4 / G2: the typed read-into-local existence terminal returns boolean (not the SQL
        // skeleton String of fetchExists()), so `boolean x = ...fetchExistsValue()` compiles and
        // can be branched on in a transpiled routine. The runtime value of a non-executing DSL
        // build is well-defined (false); the transpiler reconstructs the SQL from the parsed chain.
        boolean present = DSL.select(ACCOUNTS.ID)
                .from(ACCOUNTS)
                .where(ACCOUNTS.ACTIVE.eq(true))
                .fetchExistsValue();

        assertEquals(false, present);
    }

    @Test
    void fetchScalarReturnsTypedColumnValueForReadIntoLocal() {
        // Phase A4 / G2: the single-column scalar read terminal is typed to the column's Java type
        // (Integer here), so `int v = ...fetchScalar()` compiles for a precondition check. Only the
        // single-column SelectBuilder1 exposes it; the runtime value of a non-executing build is null.
        Integer version = DSL.select(ACCOUNTS.PLAN_ID)
                .from(ACCOUNTS)
                .where(ACCOUNTS.ID.eq(1))
                .fetchScalar();

        assertEquals(null, version);
    }

    @Test
    void forEachEmitsCursorLoopSkeleton() {
        String sql = DSL.select(ACCOUNTS.ID, ACCOUNTS.EMAIL)
                .from(ACCOUNTS)
                .forEach();

        assertEquals("FOR rec IN SELECT id, email FROM public.accounts LOOP\n"
                + "  -- user callback body\n"
                + "END LOOP", sql);
    }

    @Test
    void typedForEachLambdaOverloadCompilesAndEmitsCursorLoop() {
        String sql = DSL.select(ACCOUNTS.ID, ACCOUNTS.EMAIL)
                .from(ACCOUNTS)
                .forEach((id, email) -> {
                    // compile-time callback placeholder
                });

        assertEquals("FOR rec IN SELECT id, email FROM public.accounts LOOP\n"
                + "  -- user callback body\n"
                + "END LOOP", sql);
    }

    @Test
    void typedForEachLambdaRejectsProjectionCountMismatch() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DSL.select(ACCOUNTS.ID)
                        .from(ACCOUNTS)
                        .forEach((id, email) -> {
                            // compile-time callback placeholder
                        }));

        assertEquals(
                "forEach requires projected column count to match callback arity: projected 1 columns for callback arity 2",
                ex.getMessage());
    }

    @Test
    void singleArgumentRowForEachLambdaCanUseTupleAccessors() {
        String sql = DSL.select(ACCOUNTS.ID)
                .from(ACCOUNTS)
                .forEach(row -> {
                    int id = row.value1();
                });

        assertEquals("FOR rec IN SELECT id FROM public.accounts LOOP\n"
                + "  -- user callback body\n"
                + "END LOOP", sql);
    }

    @Test
    void singleArgumentRowForEachLambdaRemainsTypedAfterWhereChain() {
        String sql = DSL.select(ACCOUNTS.ID)
                .from(ACCOUNTS)
                .where(ACCOUNTS.ACTIVE.eq(true))
                .forEach(row -> {
                    int id = row.value1();
                });

        assertEquals("FOR rec IN SELECT id FROM public.accounts WHERE active = TRUE LOOP\n"
                + "  -- user callback body\n"
                + "END LOOP", sql);
    }

    @Test
    void twoColumnRowForEachLambdaCanUseTupleAccessors() {
        String sql = DSL.select(ACCOUNTS.ID, ACCOUNTS.EMAIL)
                .from(ACCOUNTS)
                .forEach(row -> {
                    int id = row.value1();
                    String email = row.value2();
                });

        assertEquals("FOR rec IN SELECT id, email FROM public.accounts LOOP\n"
                + "  -- user callback body\n"
                + "END LOOP", sql);
    }

    @Test
    void twoColumnRowForEachLambdaRemainsTypedAfterWhereChain() {
        String sql = DSL.select(ACCOUNTS.ID, ACCOUNTS.EMAIL)
                .from(ACCOUNTS)
                .where(ACCOUNTS.ACTIVE.eq(true))
                .forEach(row -> {
                    int id = row.value1();
                    String email = row.value2();
                });

        assertEquals("FOR rec IN SELECT id, email FROM public.accounts WHERE active = TRUE LOOP\n"
                + "  -- user callback body\n"
                + "END LOOP", sql);
    }

    @Test
    void twoColumnRowForEachLambdaRemainsTypedAfterOrderByAndLimitChain() {
        String sql = DSL.select(ACCOUNTS.ID, ACCOUNTS.EMAIL)
                .from(ACCOUNTS)
                .orderBy(ACCOUNTS.ID.desc())
                .limit(10)
                .forEach(row -> {
                    int id = row.value1();
                    String email = row.value2();
                });

        assertEquals("FOR rec IN SELECT id, email FROM public.accounts ORDER BY id DESC LIMIT 10 LOOP\n"
                + "  -- user callback body\n"
                + "END LOOP", sql);
    }

    @Test
    void twoColumnRowForEachLambdaRemainsTypedAfterGroupByAndHavingChain() {
        String sql = DSL.select(ACCOUNTS.ID, ACCOUNTS.EMAIL)
                .from(ACCOUNTS)
                .groupBy(ACCOUNTS.ID, ACCOUNTS.EMAIL)
                .having(ACCOUNTS.ID.gt(10))
                .forEach(row -> {
                    int id = row.value1();
                    String email = row.value2();
                });

        assertEquals("FOR rec IN SELECT id, email FROM public.accounts GROUP BY id, email HAVING id > 10 LOOP\n"
                + "  -- user callback body\n"
                + "END LOOP", sql);
    }

    @Test
    void twoColumnRowForEachLambdaRemainsTypedAfterLockingChain() {
        String sql = DSL.select(ACCOUNTS.ID, ACCOUNTS.EMAIL)
                .from(ACCOUNTS)
                .forUpdate()
                .skipLocked()
                .forEach(row -> {
                    int id = row.value1();
                    String email = row.value2();
                });

        assertEquals("FOR rec IN SELECT id, email FROM public.accounts FOR UPDATE SKIP LOCKED LOOP\n"
                + "  -- user callback body\n"
                + "END LOOP", sql);
    }

    @Test
    void twoColumnRowForEachLambdaRemainsTypedAfterSetOperationChain() {
        String sql = DSL.select(ACCOUNTS.ID, ACCOUNTS.EMAIL)
                .from(ACCOUNTS)
                .unionAll(DSL.select(ACCOUNTS.ID, ACCOUNTS.EMAIL).from(ACCOUNTS))
                .forEach(row -> {
                    int id = row.value1();
                    String email = row.value2();
                });

        assertEquals("FOR rec IN SELECT id, email FROM public.accounts UNION ALL SELECT id, email FROM public.accounts LOOP\n"
                + "  -- user callback body\n"
                + "END LOOP", sql);
    }

    @Test
    void triArgumentForEachLambdaCompilesAndEmitsCursorLoop() {
        String sql = DSL.select(ACCOUNTS.ID, ACCOUNTS.EMAIL, ACCOUNTS.LAST_LOGIN)
                .from(ACCOUNTS)
                .forEach((id, email, lastLogin) -> {
                    // compile-time callback placeholder
                });

        assertEquals("FOR rec IN SELECT id, email, last_login FROM public.accounts LOOP\n"
                + "  -- user callback body\n"
                + "END LOOP", sql);
    }

    @Test
    void fourArgumentForEachLambdaCompilesAndEmitsCursorLoop() {
        String sql = DSL.select(ACCOUNTS.ID, ACCOUNTS.EMAIL, ACCOUNTS.LAST_LOGIN, ACCOUNTS.ACTIVE)
                .from(ACCOUNTS)
                .forEach((id, email, lastLogin, active) -> {
                    // compile-time callback placeholder
                });

        assertEquals("FOR rec IN SELECT id, email, last_login, active FROM public.accounts LOOP\n"
                + "  -- user callback body\n"
                + "END LOOP", sql);
    }

    @Test
    void fiveArgumentForEachLambdaCompilesAndEmitsCursorLoop() {
        String sql = DSL.select(ACCOUNTS.ID, ACCOUNTS.EMAIL, ACCOUNTS.LAST_LOGIN, ACCOUNTS.ACTIVE, ACCOUNTS.CREATED_AT)
                .from(ACCOUNTS)
                .forEach((id, email, lastLogin, active, createdAt) -> {
                    // compile-time callback placeholder
                });

        assertEquals("FOR rec IN SELECT id, email, last_login, active, created_at FROM public.accounts LOOP\n"
                + "  -- user callback body\n"
                + "END LOOP", sql);
    }

    @Test
    void sixArgumentForEachLambdaCompilesAndEmitsCursorLoop() {
        String sql = DSL.select(ACCOUNTS.ID, ACCOUNTS.EMAIL, ACCOUNTS.LAST_LOGIN, ACCOUNTS.ACTIVE,
                        ACCOUNTS.CREATED_AT, ACCOUNTS.STATUS)
                .from(ACCOUNTS)
                .forEach((id, email, lastLogin, active, createdAt, status) -> {
                    // compile-time callback placeholder
                });

        assertEquals("FOR rec IN SELECT id, email, last_login, active, created_at, status FROM public.accounts LOOP\n"
                + "  -- user callback body\n"
                + "END LOOP", sql);
    }

    @Test
    void sevenArgumentForEachLambdaCompilesAndEmitsCursorLoop() {
        String sql = DSL.select(ACCOUNTS.ID, ACCOUNTS.EMAIL, ACCOUNTS.LAST_LOGIN, ACCOUNTS.ACTIVE,
                        ACCOUNTS.CREATED_AT, ACCOUNTS.STATUS, ACCOUNTS.PLAN_ID)
                .from(ACCOUNTS)
                .forEach((id, email, lastLogin, active, createdAt, status, planId) -> {
                    // compile-time callback placeholder
                });

        assertEquals("FOR rec IN SELECT id, email, last_login, active, created_at, status, plan_id FROM public.accounts LOOP\n"
                + "  -- user callback body\n"
                + "END LOOP", sql);
    }

    @Test
    void eightArgumentForEachLambdaCompilesAndEmitsCursorLoop() {
        String sql = DSL.select(ACCOUNTS.ID, ACCOUNTS.EMAIL, ACCOUNTS.LAST_LOGIN, ACCOUNTS.ACTIVE,
                        ACCOUNTS.CREATED_AT, ACCOUNTS.STATUS, ACCOUNTS.PLAN_ID, ACCOUNTS.REGION)
                .from(ACCOUNTS)
                .forEach((id, email, lastLogin, active, createdAt, status, planId, region) -> {
                    // compile-time callback placeholder
                });

        assertEquals("FOR rec IN SELECT id, email, last_login, active, created_at, status, plan_id, region FROM public.accounts LOOP\n"
                + "  -- user callback body\n"
                + "END LOOP", sql);
    }

    @Test
    void nineArgumentForEachLambdaCompilesAndEmitsCursorLoop() {
        String sql = DSL.select(ACCOUNTS.ID, ACCOUNTS.EMAIL, ACCOUNTS.LAST_LOGIN, ACCOUNTS.ACTIVE,
                        ACCOUNTS.CREATED_AT, ACCOUNTS.STATUS, ACCOUNTS.PLAN_ID, ACCOUNTS.REGION, ACCOUNTS.TENANT_ID)
                .from(ACCOUNTS)
                .forEach((id, email, lastLogin, active, createdAt, status, planId, region, tenantId) -> {
                    // compile-time callback placeholder
                });

        assertEquals("FOR rec IN SELECT id, email, last_login, active, created_at, status, plan_id, region, tenant_id FROM public.accounts LOOP\n"
                + "  -- user callback body\n"
                + "END LOOP", sql);
    }

    @Test
    void tenArgumentForEachLambdaCompilesAndEmitsCursorLoop() {
        String sql = DSL.select(ACCOUNTS.ID, ACCOUNTS.EMAIL, ACCOUNTS.LAST_LOGIN, ACCOUNTS.ACTIVE,
                        ACCOUNTS.CREATED_AT, ACCOUNTS.STATUS, ACCOUNTS.PLAN_ID, ACCOUNTS.REGION,
                        ACCOUNTS.TENANT_ID, ACCOUNTS.ACCOUNT_TYPE)
                .from(ACCOUNTS)
                .forEach((id, email, lastLogin, active, createdAt, status, planId, region, tenantId, accountType) -> {
                    // compile-time callback placeholder
                });

        assertEquals("FOR rec IN SELECT id, email, last_login, active, created_at, status, plan_id, region, tenant_id, account_type FROM public.accounts LOOP\n"
                + "  -- user callback body\n"
                + "END LOOP", sql);
    }

    @Test
    void triArgumentForEachRejectsProjectionCountMismatch() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DSL.select(ACCOUNTS.ID, ACCOUNTS.EMAIL)
                        .from(ACCOUNTS)
                        .forEach((id, email, lastLogin) -> {
                            // compile-time callback placeholder
                        }));

        assertEquals(
                "forEach requires projected column count to match callback arity: projected 2 columns for callback arity 3",
                ex.getMessage());
    }

    @Test
    void fetchIntoAddsRecordMappingHint() {
        String sql = DSL.select(ACCOUNTS.ID, ACCOUNTS.EMAIL)
                .from(ACCOUNTS)
                .fetchInto(AccountProjection.class);

        assertEquals(
                "SELECT id, email FROM public.accounts /* fetchInto: AccountProjection by column-name */",
                sql);
    }

    @Test
    void fetchIntoMatchesSnakeCaseColumnsToCamelCaseRecordComponents() {
        String sql = DSL.select(ACCOUNTS.ID, ACCOUNTS.LAST_LOGIN)
                .from(ACCOUNTS)
                .fetchInto(AccountWithLastLoginProjection.class);

        assertEquals(
                "SELECT id, last_login FROM public.accounts /* fetchInto: AccountWithLastLoginProjection by column-name */",
                sql);
    }

    @Test
    void fetchIntoMatchesQualifiedInlineViewColumnsToRecordComponents() {
        InlineView<Object> activeAccounts = DSL.defineInlineView(
                DSL.select(ACCOUNTS.ID, ACCOUNTS.EMAIL)
                        .from(ACCOUNTS)
                        .where(ACCOUNTS.ACTIVE.eq(true)));
        Column<Integer> id = activeAccounts.field("id", SQLType.INTEGER, Nullability.NOT_NULL);
        Column<String> email = activeAccounts.field("email", SQLType.VARCHAR, Nullability.NOT_NULL);

        String sql = DSL.select(id, email)
                .from(activeAccounts)
                .fetchInto(AccountProjection.class);

        assertEquals(
                "WITH " + activeAccounts.name() + " AS (SELECT id, email FROM public.accounts WHERE active = TRUE) "
                        + "SELECT " + activeAccounts.name() + ".id, " + activeAccounts.name() + ".email FROM " + activeAccounts.name()
                        + " /* fetchInto: AccountProjection by column-name */",
                sql);
    }

    @Test
    void fetchIntoFailsWhenRecordComponentCannotBeMapped() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DSL.select(ACCOUNTS.ID, ACCOUNTS.EMAIL)
                        .from(ACCOUNTS)
                        .fetchInto(UnmappedProjection.class));

        assertEquals("fetchInto could not map record component 'missingField' by column name", ex.getMessage());
    }

    @Test
    void fetchIntoFailsOnTypeMismatch() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DSL.select(ACCOUNTS.ID)
                        .from(ACCOUNTS)
                        .fetchInto(IdAsStringProjection.class));

        assertEquals("fetchInto type mismatch for component 'id': String is not compatible with INTEGER", ex.getMessage());
    }

    @Test
    void fetchIntoAcceptsTimestampTzProjectionAsZonedDateTime() {
        String sql = DSL.select(ACCOUNTS.LAST_SEEN_AT)
                .from(ACCOUNTS)
                .fetchInto(LastSeenProjection.class);

        assertEquals(
                "SELECT last_seen_at FROM public.accounts /* fetchInto: LastSeenProjection by column-name */",
                sql);
    }

    @Test
    void fetchIntoRejectsProjectionCountMismatch() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DSL.select(ACCOUNTS.ID, ACCOUNTS.EMAIL, ACCOUNTS.ACTIVE)
                        .from(ACCOUNTS)
                        .fetchInto(AccountProjection.class));

        assertEquals(
                "fetchInto requires projection column count to match record component count: projected 3 columns for record AccountProjection with 2 components",
                ex.getMessage());
    }

    @Test
    void fetchIntoRejectsNonIdentifierShapedProjectedColumns() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DSL.select(DSL.count())
                        .from(ACCOUNTS)
                        .fetchInto(CountProjection.class));

        assertEquals(
                "fetchInto requires identifier-shaped projected columns, but found 'COUNT(*)'",
                ex.getMessage());
    }

    record AccountProjection(int id, String email) {
    }

    record AccountWithLastLoginProjection(int id, String lastLogin) {
    }

    record UnmappedProjection(int id, String missingField) {
    }

    record IdAsStringProjection(String id) {
    }

    record LastSeenProjection(ZonedDateTime lastSeenAt) {
    }

    record CountProjection(long count) {
    }

    private static final class AccountsTable extends Table<Object> {
        private final Column<Integer> ID = column("id", SQLType.INTEGER, Nullability.NOT_NULL);
        private final Column<String> EMAIL = column("email", SQLType.VARCHAR, Nullability.NOT_NULL);
        private final Column<String> LAST_LOGIN = column("last_login", SQLType.VARCHAR, Nullability.NULLABLE);
        private final Column<Boolean> ACTIVE = column("active", SQLType.BOOLEAN, Nullability.NOT_NULL);
        private final Column<String> CREATED_AT = column("created_at", SQLType.VARCHAR, Nullability.NULLABLE);
        private final Column<String> STATUS = column("status", SQLType.VARCHAR, Nullability.NULLABLE);
        private final Column<Integer> PLAN_ID = column("plan_id", SQLType.INTEGER, Nullability.NULLABLE);
        private final Column<String> REGION = column("region", SQLType.VARCHAR, Nullability.NULLABLE);
        private final Column<Integer> TENANT_ID = column("tenant_id", SQLType.INTEGER, Nullability.NULLABLE);
        private final Column<String> ACCOUNT_TYPE = column("account_type", SQLType.VARCHAR, Nullability.NULLABLE);
        private final Column<ZonedDateTime> LAST_SEEN_AT = column("last_seen_at", SQLType.TIMESTAMP_TZ, Nullability.NULLABLE);

        private AccountsTable() {
            super("accounts", "public");
        }
    }
}
