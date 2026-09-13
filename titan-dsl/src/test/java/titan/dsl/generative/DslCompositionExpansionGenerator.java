package titan.dsl.generative;

import java.util.List;
import titan.dsl.Column;
import titan.dsl.CommonTableExpression;
import titan.dsl.DSL;
import titan.dsl.InlineView;
import titan.dsl.Nullability;
import titan.dsl.SQLType;
import titan.dsl.Table;

final class DslCompositionExpansionGenerator {

    GeneratedDslCase generate(long seed) {
        CompositionFamily family = CompositionFamily.values()[(int) Math.floorMod(seed, CompositionFamily.values().length)];
        AccountsTable accounts = new AccountsTable();
        PlansTable plans = new PlansTable();

        return switch (family) {
            case INLINE_VIEW_JOIN_ORDER_FETCH_ONE -> new GeneratedDslCase(
                    seed,
                    DslGenerativeProfile.COMPOSITION_EXPANSION.id(),
                    family.id,
                    "inline-view + join + ordering + fetchOne composition",
                    false,
                    family.expectedFragments,
                    () -> {
                        InlineView<Object> activeAccounts = DSL.defineInlineView(
                                DSL.select(accounts.ID, accounts.EMAIL, accounts.PLAN_ID)
                                        .from(accounts)
                                        .where(accounts.ACTIVE.eq(true)));
                        Column<Integer> accountId = activeAccounts.field("id", SQLType.INTEGER, Nullability.NOT_NULL);
                        Column<Integer> planId = activeAccounts.field("plan_id", SQLType.INTEGER, Nullability.NULLABLE);
                        return DSL.select(accountId, plans.NAME)
                                .from(activeAccounts)
                                .leftJoin(plans).on(planId.eqColumn(plans.ID))
                                .orderBy(plans.NAME.asc())
                                .fetchOne();
                    });
            case CTE_JOIN_GROUP_HAVING_FETCH_COUNT -> new GeneratedDslCase(
                    seed,
                    DslGenerativeProfile.COMPOSITION_EXPANSION.id(),
                    family.id,
                    "cte + join + grouping + having + fetchCount composition",
                    false,
                    family.expectedFragments,
                    () -> {
                        CommonTableExpression<Object> plannedAccounts = DSL.name("planned_accounts")
                                .as(DSL.select(accounts.ID, accounts.PLAN_ID)
                                        .from(accounts)
                                        .where(accounts.PLAN_ID.isNotNull()));
                        Column<Integer> planId = plannedAccounts.field("plan_id", SQLType.INTEGER, Nullability.NULLABLE);
                        return DSL.with(plannedAccounts)
                                .select(planId)
                                .from(plannedAccounts)
                                .join(plans).on(planId.eqColumn(plans.ID))
                                .groupBy(planId)
                                .having(planId.isNotNull())
                                .fetchCount();
                    });
            case CTE_SCALAR_SUBQUERY_FETCH_EXISTS -> new GeneratedDslCase(
                    seed,
                    DslGenerativeProfile.COMPOSITION_EXPANSION.id(),
                    family.id,
                    "cte + scalar subquery + fetchExists composition",
                    false,
                    family.expectedFragments,
                    () -> {
                        CommonTableExpression<Object> activeAccounts = DSL.name("active_accounts")
                                .as(DSL.select(accounts.ID, accounts.EMAIL)
                                        .from(accounts)
                                        .where(accounts.ACTIVE.eq(true)));
                        Column<Integer> activeId = activeAccounts.field("id", SQLType.INTEGER, Nullability.NOT_NULL);
                        Column<String> activeEmail = activeAccounts.field("email", SQLType.VARCHAR, Nullability.NOT_NULL);
                        Column<String> emailLookup = DSL.scalar(
                                DSL.with(activeAccounts)
                                        .select(activeEmail)
                                        .from(activeAccounts)
                                        .where(activeId.eq(1))
                                        .limit(1),
                                SQLType.VARCHAR);
                        return DSL.with(activeAccounts)
                                .select(accounts.ID)
                                .from(accounts)
                                .where(accounts.EMAIL.eqColumn(emailLookup))
                                .fetchExists();
                    });
            case INLINE_VIEW_ORDERED_FETCH_INTO -> new GeneratedDslCase(
                    seed,
                    DslGenerativeProfile.COMPOSITION_EXPANSION.id(),
                    family.id,
                    "inline-view + ordering + fetchInto composition",
                    false,
                    family.expectedFragments,
                    () -> {
                        InlineView<Object> plannedAccounts = DSL.defineInlineView(
                                DSL.select(accounts.ID, accounts.EMAIL, accounts.PLAN_ID)
                                        .from(accounts)
                                        .where(accounts.PLAN_ID.isNotNull()));
                        Column<Integer> id = plannedAccounts.field("id", SQLType.INTEGER, Nullability.NOT_NULL);
                        Column<String> email = plannedAccounts.field("email", SQLType.VARCHAR, Nullability.NOT_NULL);
                        Column<Integer> planId = plannedAccounts.field("plan_id", SQLType.INTEGER, Nullability.NULLABLE);
                        return DSL.select(id, email)
                                .from(plannedAccounts)
                                .where(planId.isNotNull())
                                .orderBy(email.asc())
                                .fetchInto(AccountProjection.class);
                    });
            case CTE_JOIN_EXISTS_FETCH -> new GeneratedDslCase(
                    seed,
                    DslGenerativeProfile.COMPOSITION_EXPANSION.id(),
                    family.id,
                    "cte + join + exists-subquery + fetch composition",
                    false,
                    family.expectedFragments,
                    () -> {
                        CommonTableExpression<Object> plannedAccounts = DSL.name("planned_accounts")
                                .as(DSL.select(accounts.ID, accounts.PLAN_ID)
                                        .from(accounts)
                                        .where(accounts.PLAN_ID.isNotNull()));
                        Column<Integer> accountId = plannedAccounts.field("id", SQLType.INTEGER, Nullability.NOT_NULL);
                        Column<Integer> planId = plannedAccounts.field("plan_id", SQLType.INTEGER, Nullability.NULLABLE);
                        return DSL.with(plannedAccounts)
                                .select(accountId, plans.NAME)
                                .from(plannedAccounts)
                                .join(plans).on(planId.eqColumn(plans.ID))
                                .where(DSL.exists(
                                        DSL.select(accounts.ID)
                                                .from(accounts)
                                                .where(accounts.PLAN_ID.eqColumn(planId))
                                                .limit(1)))
                                .orderBy(plans.NAME.asc())
                                .fetch();
                    });
        };
    }

    record AccountProjection(Integer id, String email) {
    }

    private enum CompositionFamily {
        INLINE_VIEW_JOIN_ORDER_FETCH_ONE(
                "inline-view-join-order-fetch-one",
                List.of("with ", "left join public.plans", "order by name asc", "limit 1")),
        CTE_JOIN_GROUP_HAVING_FETCH_COUNT(
                "cte-join-group-having-fetch-count",
                List.of("select count(*) from (with planned_accounts as", "join public.plans", "group by planned_accounts.plan_id", "having planned_accounts.plan_id is not null")),
        CTE_SCALAR_SUBQUERY_FETCH_EXISTS(
                "cte-scalar-subquery-fetch-exists",
                List.of("select exists (with active_accounts as", "where email = (with active_accounts as", "limit 1")),
        INLINE_VIEW_ORDERED_FETCH_INTO(
                "inline-view-ordered-fetch-into",
                List.of("with ", "order by", "/* fetchInto: AccountProjection by column-name */")),
        CTE_JOIN_EXISTS_FETCH(
                "cte-join-exists-fetch",
                List.of("with planned_accounts as", "join public.plans", "exists (select id from public.accounts", "order by name asc"));

        private final String id;
        private final List<String> expectedFragments;

        CompositionFamily(String id, List<String> expectedFragments) {
            this.id = id;
            this.expectedFragments = expectedFragments;
        }
    }

    private static final class AccountsTable extends Table<Object> {
        private final Column<Integer> ID = column("id", SQLType.INTEGER, Nullability.NOT_NULL);
        private final Column<String> EMAIL = column("email", SQLType.VARCHAR, Nullability.NOT_NULL);
        private final Column<Boolean> ACTIVE = column("active", SQLType.BOOLEAN, Nullability.NOT_NULL);
        private final Column<Integer> PLAN_ID = column("plan_id", SQLType.INTEGER, Nullability.NULLABLE);

        private AccountsTable() {
            super("accounts", "public");
        }
    }

    private static final class PlansTable extends Table<Object> {
        private final Column<Integer> ID = column("id", SQLType.INTEGER, Nullability.NOT_NULL);
        private final Column<String> NAME = column("name", SQLType.VARCHAR, Nullability.NOT_NULL);

        private PlansTable() {
            super("plans", "public");
        }
    }
}
