package titan.dsl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SelectBuilderTest {

    private static final PlansTable PLANS = new PlansTable();
    private static final AddonsTable ADDONS = new AddonsTable();
    private static final AccountsTable ACCOUNTS = new AccountsTable();
    private static final MismatchedAccountsTable MISMATCHED_ACCOUNTS = new MismatchedAccountsTable();
    private static final AmbiguousMappingTable AMBIGUOUS = new AmbiguousMappingTable();

    @Test
    void selectWhereOrderByLimitRendersExpectedSql() {
        String sql = DSL.select(ACCOUNTS.ID, ACCOUNTS.EMAIL)
                .from(ACCOUNTS)
                .where(ACCOUNTS.ACTIVE.eq(true))
                .orderBy(ACCOUNTS.EMAIL.asc())
                .limit(50)
                .fetch();

        assertEquals(
                "SELECT id, email FROM public.accounts WHERE active = TRUE ORDER BY email ASC LIMIT 50",
                sql);
    }

    @Test
    void selectWithOffsetRendersExpectedSql() {
        String sql = DSL.select(ACCOUNTS.ID, ACCOUNTS.EMAIL)
                .from(ACCOUNTS)
                .orderBy(ACCOUNTS.ID.asc())
                .limit(25)
                .offset(50)
                .fetch();

        assertEquals(
                "SELECT id, email FROM public.accounts ORDER BY id ASC LIMIT 25 OFFSET 50",
                sql);
    }

    @Test
    void negativeOffsetFailsFast() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DSL.select(ACCOUNTS.ID)
                        .from(ACCOUNTS)
                        .offset(-1)
                        .fetch());

        assertEquals("offset must be >= 0", ex.getMessage());
    }

    @Test
    void selectWithJoinConditionRendersExpectedSql() {
        String sql = DSL.select(ACCOUNTS.EMAIL, PLANS.NAME)
                .from(ACCOUNTS)
                .join(PLANS).on(ACCOUNTS.PLAN_ID.eqColumn(PLANS.ID))
                .fetch();

        assertEquals(
                "SELECT email, name FROM public.accounts JOIN public.plans ON plan_id = id",
                sql);
    }

    @Test
    void selectWithTypedJoinColumnsRendersExpectedSql() {
        String sql = DSL.select(ACCOUNTS.EMAIL, PLANS.NAME)
                .from(ACCOUNTS)
                .join(PLANS).on(ACCOUNTS.PLAN_ID, PLANS.ID)
                .fetch();

        assertEquals(
                "SELECT email, name FROM public.accounts JOIN public.plans ON plan_id = id",
                sql);
    }

    @Test
    void selectWithForeignKeyJoinShorthandRendersExpectedSql() {
        String sql = DSL.select(ACCOUNTS.EMAIL, PLANS.NAME)
                .from(ACCOUNTS)
                .join(PLANS).on(ACCOUNTS.FK_PLAN)
                .fetch();

        assertEquals(
                "SELECT email, name FROM public.accounts JOIN public.plans ON plan_id = id",
                sql);
    }

    @Test
    void leftRightFullOuterAndCrossJoinRenderExpectedSql() {
        String sql = DSL.select(ACCOUNTS.EMAIL, PLANS.NAME)
                .from(ACCOUNTS)
                .leftJoin(PLANS).on(ACCOUNTS.FK_PLAN)
                .rightJoin(PLANS).on(ACCOUNTS.PLAN_ID.eqColumn(PLANS.ID))
                .fullOuterJoin(PLANS).on(ACCOUNTS.PLAN_ID.eqColumn(PLANS.ID))
                .crossJoin(PLANS)
                .fetch();

        assertEquals(
                "SELECT email, name FROM public.accounts LEFT JOIN public.plans ON plan_id = id RIGHT JOIN public.plans ON plan_id = id FULL OUTER JOIN public.plans ON plan_id = id CROSS JOIN public.plans",
                sql);
    }

    @Test
    void lateralJoinRendersExpectedSql() {
        String sql = DSL.select(ACCOUNTS.EMAIL)
                .from(ACCOUNTS)
                .lateralJoin(DSL.select(PLANS.NAME)
                        .from(PLANS)
                        .where(PLANS.ID.eqColumn(ACCOUNTS.PLAN_ID))
                        .limit(1))
                .as("plan_lateral")
                .fetch();

        assertEquals(
                "SELECT email FROM public.accounts JOIN LATERAL (SELECT name FROM public.plans WHERE id = plan_id LIMIT 1) AS plan_lateral",
                sql);
    }

    @Test
    void lateralJoinRequiresAlias() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DSL.select(ACCOUNTS.EMAIL)
                        .from(ACCOUNTS)
                        .lateralJoin(DSL.select(PLANS.NAME).from(PLANS))
                        .as(" "));

        assertEquals("lateral join alias must not be blank", ex.getMessage());
    }

    @Test
    void aggregationWithGroupByAndHavingRendersExpectedSql() {
        String sql = DSL.select(PLANS.NAME, DSL.count(), DSL.avg(PLANS.MONTHLY_FEE))
                .from(ACCOUNTS)
                .join(PLANS).on(ACCOUNTS.FK_PLAN)
                .where(ACCOUNTS.ACTIVE.eq(true))
                .groupBy(PLANS.NAME)
                .having(DSL.count().gt(10L))
                .orderBy(DSL.count().desc())
                .fetch();

        assertEquals(
                "SELECT name, COUNT(*), AVG(monthly_fee) FROM public.accounts JOIN public.plans ON plan_id = id "
                        + "WHERE active = TRUE GROUP BY name HAVING COUNT(*) > 10 ORDER BY COUNT(*) DESC",
                sql);
    }

    @Test
    void groupingSetsRendersExpectedSql() {
        String sql = DSL.select(PLANS.NAME, ACCOUNTS.ACTIVE, DSL.count())
                .from(ACCOUNTS)
                .join(PLANS).on(ACCOUNTS.FK_PLAN)
                .groupBy(DSL.groupingSets(
                        DSL.set(PLANS.NAME, ACCOUNTS.ACTIVE),
                        DSL.set(PLANS.NAME),
                        DSL.set()))
                .fetch();

        assertEquals(
                "SELECT name, active, COUNT(*) FROM public.accounts JOIN public.plans ON plan_id = id "
                        + "GROUP BY GROUPING SETS ((name, active), (name), ())",
                sql);
    }

    @Test
    void rollupAndCubeRenderExpectedSql() {
        String rollup = DSL.select(PLANS.NAME, ACCOUNTS.ACTIVE, DSL.count())
                .from(ACCOUNTS)
                .join(PLANS).on(ACCOUNTS.FK_PLAN)
                .groupBy(DSL.rollup(PLANS.NAME, ACCOUNTS.ACTIVE))
                .fetch();

        String cube = DSL.select(PLANS.NAME, ACCOUNTS.ACTIVE, DSL.count())
                .from(ACCOUNTS)
                .join(PLANS).on(ACCOUNTS.FK_PLAN)
                .groupBy(DSL.cube(PLANS.NAME, ACCOUNTS.ACTIVE))
                .fetch();

        assertEquals(
                "SELECT name, active, COUNT(*) FROM public.accounts JOIN public.plans ON plan_id = id "
                        + "GROUP BY ROLLUP (name, active)",
                rollup);
        assertEquals(
                "SELECT name, active, COUNT(*) FROM public.accounts JOIN public.plans ON plan_id = id "
                        + "GROUP BY CUBE (name, active)",
                cube);
    }

    @Test
    void setOperationsRenderExpectedSql() {
        String sql = DSL.select(ACCOUNTS.EMAIL)
                .from(ACCOUNTS)
                .where(ACCOUNTS.ACTIVE.eq(true))
                .unionAll(DSL.select(ACCOUNTS.EMAIL)
                        .from(ACCOUNTS)
                        .where(ACCOUNTS.ACTIVE.eq(false)))
                .intersect(DSL.select(ACCOUNTS.EMAIL)
                        .from(ACCOUNTS)
                        .where(ACCOUNTS.EMAIL.like("%@example.com")))
                .except(DSL.select(ACCOUNTS.EMAIL)
                        .from(ACCOUNTS)
                        .where(ACCOUNTS.EMAIL.like("%blocked%")))
                .orderBy(ACCOUNTS.EMAIL.asc())
                .limit(10)
                .fetch();

        assertEquals(
                "SELECT email FROM public.accounts WHERE active = TRUE "
                        + "UNION ALL SELECT email FROM public.accounts WHERE active = FALSE "
                        + "INTERSECT SELECT email FROM public.accounts WHERE email LIKE '%@example.com' "
                        + "EXCEPT SELECT email FROM public.accounts WHERE email LIKE '%blocked%' "
                        + "ORDER BY email ASC LIMIT 10",
                sql);
    }

    @Test
    void setOperationsRequireMatchingProjectionTypes() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DSL.select(ACCOUNTS.ID)
                        .from(ACCOUNTS)
                        .union(DSL.select(ACCOUNTS.EMAIL)
                                .from(ACCOUNTS)));

        assertEquals("set operation requires matching projection column types", ex.getMessage());
    }

    @Test
    void pessimisticLockingRendersExpectedSql() {
        String forUpdate = DSL.select(ACCOUNTS.ID)
                .from(ACCOUNTS)
                .where(ACCOUNTS.ACTIVE.eq(true))
                .orderBy(ACCOUNTS.ID.asc())
                .forUpdate()
                .skipLocked()
                .fetch();

        String forShare = DSL.select(ACCOUNTS.ID)
                .from(ACCOUNTS)
                .where(ACCOUNTS.ACTIVE.eq(true))
                .forShare()
                .noWait()
                .fetch();

        assertEquals(
                "SELECT id FROM public.accounts WHERE active = TRUE ORDER BY id ASC FOR UPDATE SKIP LOCKED",
                forUpdate);
        assertEquals(
                "SELECT id FROM public.accounts WHERE active = TRUE FOR SHARE NOWAIT",
                forShare);
    }

    @Test
    void lockModifiersRequireLockMode() {
        IllegalStateException skipLocked = assertThrows(IllegalStateException.class,
                () -> DSL.select(ACCOUNTS.ID)
                        .from(ACCOUNTS)
                        .skipLocked()
                        .fetch());

        IllegalStateException noWait = assertThrows(IllegalStateException.class,
                () -> DSL.select(ACCOUNTS.ID)
                        .from(ACCOUNTS)
                        .noWait()
                        .fetch());

        assertEquals("skipLocked() requires forUpdate() or forShare() first", skipLocked.getMessage());
        assertEquals("noWait() requires forUpdate() or forShare() first", noWait.getMessage());

        IllegalStateException mutuallyExclusive = assertThrows(IllegalStateException.class,
                () -> DSL.select(ACCOUNTS.ID)
                        .from(ACCOUNTS)
                        .forUpdate()
                        .skipLocked()
                        .noWait()
                        .fetch());
        assertEquals("noWait() cannot be combined with skipLocked()", mutuallyExclusive.getMessage());
    }

    @Test
    void joinWithoutFromFailsFast() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> DSL.select(ACCOUNTS.ID).join(PLANS).on(ACCOUNTS.FK_PLAN).fetch());
        assertEquals("from(table) must be called before join(...)", ex.getMessage());
    }

    @Test
    void lateralJoinWithoutFromFailsFast() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> DSL.select(ACCOUNTS.ID)
                        .lateralJoin(DSL.select(PLANS.NAME).from(PLANS))
                        .as("plan_lateral")
                        .fetch());

        assertEquals("from(table) must be called before join(...)", ex.getMessage());
    }

    @Test
    void selectWithTypedJoinColumnsAndExtraPredicateRendersExpectedSql() {
        String sql = DSL.select(ACCOUNTS.EMAIL, PLANS.NAME)
                .from(ACCOUNTS)
                .leftJoin(PLANS)
                .on(ACCOUNTS.PLAN_ID, PLANS.ID, PLANS.MONTHLY_FEE.gt(10.0))
                .fetch();

        assertEquals(
                "SELECT email, name FROM public.accounts LEFT JOIN public.plans ON plan_id = id AND monthly_fee > 10.0",
                sql);
    }

    @Test
    void typedJoinRequiresCompatibleColumnTypes() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DSL.select(ACCOUNTS.ID)
                        .from(ACCOUNTS)
                        .join(PLANS)
                        .on((Column) ACCOUNTS.EMAIL, (Column) PLANS.ID)
                        .fetch());

        assertEquals("join column type mismatch: email is VARCHAR but id is INTEGER", ex.getMessage());
    }

    @Test
    void typedJoinWithExtraPredicateRequiresCompatibleColumnTypes() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DSL.select(ACCOUNTS.ID)
                        .from(ACCOUNTS)
                        .join(PLANS)
                        .on((Column) ACCOUNTS.EMAIL, (Column) PLANS.ID, PLANS.MONTHLY_FEE.gt(10.0))
                        .fetch());

        assertEquals("join column type mismatch: email is VARCHAR but id is INTEGER", ex.getMessage());
    }

    @Test
    void foreignKeyJoinRequiresMatchingJoinedTable() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DSL.select(ACCOUNTS.ID)
                        .from(ACCOUNTS)
                        .join(ADDONS)
                        .on(ACCOUNTS.FK_PLAN)
                        .fetch());
        assertEquals("foreign key referenced table must match the joined table", ex.getMessage());
    }

    @Test
    void foreignKeyJoinRequiresCompatibleColumnTypes() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DSL.select(MISMATCHED_ACCOUNTS.ID)
                        .from(MISMATCHED_ACCOUNTS)
                        .join(PLANS)
                        .on(MISMATCHED_ACCOUNTS.FK_PLAN)
                        .fetch());

        assertEquals(
                "foreign key column type mismatch at position 0: local plan_id is BIGINT but referenced id is INTEGER",
                ex.getMessage());
    }

    @Test
    void selectWithoutFromFailsFast() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> DSL.select(ACCOUNTS.ID).fetch());
        assertEquals("from(table) must be called before fetch()", ex.getMessage());
    }

    @Test
    void fetchIntoRejectsAmbiguousNormalizedColumnNames() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DSL.select(AMBIGUOUS.PLAN_ID, AMBIGUOUS.PLANID)
                        .from(AMBIGUOUS)
                        .fetchInto(AmbiguousPlanProjection.class));

        assertEquals(
                "fetchInto column-name mapping is ambiguous for record component 'planId' (multiple projected columns normalize to the same identifier)",
                ex.getMessage());
    }

    private record AmbiguousPlanProjection(Integer planId, Integer planid) {
    }

    private static final class AccountsTable extends Table<Object> {
        private final Column<Integer> ID = column("id", SQLType.INTEGER, Nullability.NOT_NULL);
        private final Column<String> EMAIL = column("email", SQLType.VARCHAR, Nullability.NOT_NULL);
        private final Column<Boolean> ACTIVE = column("active", SQLType.BOOLEAN, Nullability.NOT_NULL);
        private final Column<Integer> PLAN_ID = column("plan_id", SQLType.INTEGER, Nullability.NULLABLE);
        private final ForeignKey<Object, Object> FK_PLAN =
                foreignKey(PLAN_ID, PLANS, PLANS.ID);

        private AccountsTable() {
            super("accounts", "public");
        }
    }

    private static final class AddonsTable extends Table<Object> {
        private AddonsTable() {
            super("addons", "public");
        }
    }

    private static final class MismatchedAccountsTable extends Table<Object> {
        private final Column<Integer> ID = column("id", SQLType.INTEGER, Nullability.NOT_NULL);
        private final Column<Long> PLAN_ID = column("plan_id", SQLType.BIGINT, Nullability.NULLABLE);
        private final ForeignKey<Object, Object> FK_PLAN =
                foreignKey(PLAN_ID, PLANS, PLANS.ID);

        private MismatchedAccountsTable() {
            super("mismatched_accounts", "public");
        }
    }

    private static final class PlansTable extends Table<Object> {
        private final Column<Integer> ID = column("id", SQLType.INTEGER, Nullability.NOT_NULL);
        private final Column<String> NAME = column("name", SQLType.VARCHAR, Nullability.NOT_NULL);
        private final Column<Double> MONTHLY_FEE = column("monthly_fee", SQLType.DOUBLE, Nullability.NOT_NULL);

        private PlansTable() {
            super("plans", "public");
        }
    }

    private static final class AmbiguousMappingTable extends Table<Object> {
        private final Column<Integer> PLAN_ID = column("plan_id", SQLType.INTEGER, Nullability.NOT_NULL);
        private final Column<Integer> PLANID = column("planid", SQLType.INTEGER, Nullability.NOT_NULL);

        private AmbiguousMappingTable() {
            super("ambiguous_mapping", "public");
        }
    }
}
