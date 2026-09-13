package titan.dsl.generative;

import java.util.List;
import titan.dsl.Column;
import titan.dsl.CommonTableExpression;
import titan.dsl.DSL;
import titan.dsl.InlineView;
import titan.dsl.Nullability;
import titan.dsl.SQLType;
import titan.dsl.Table;

final class DslRenderingGenerator {

    GeneratedDslCase generate(long seed) {
        RenderingFamily family = RenderingFamily.values()[(int) Math.floorMod(seed, RenderingFamily.values().length)];
        AccountsTable accounts = new AccountsTable();
        PlansTable plans = new PlansTable();

        return switch (family) {
            case SIMPLE_FILTER_ORDER -> new GeneratedDslCase(
                    seed,
                    DslGenerativeProfile.RENDERING_BASIC.id(),
                    family.id,
                    "simple filter + order rendering",
                    false,
                    family.expectedFragments,
                    () -> DSL.select(accounts.ID, accounts.EMAIL)
                            .from(accounts)
                            .where(accounts.ACTIVE.eq(true))
                            .orderBy(accounts.EMAIL.asc())
                            .fetch());
            case JOIN_RENDERING -> new GeneratedDslCase(
                    seed,
                    DslGenerativeProfile.RENDERING_BASIC.id(),
                    family.id,
                    "join + projection rendering",
                    false,
                    family.expectedFragments,
                    () -> DSL.select(accounts.ID, plans.NAME)
                            .from(accounts)
                            .leftJoin(plans)
                            .on(accounts.PLAN_ID.eqColumn(plans.ID))
                            .fetch());
            case INLINE_VIEW_RENDERING -> new GeneratedDslCase(
                    seed,
                    DslGenerativeProfile.RENDERING_BASIC.id(),
                    family.id,
                    "inline view auto-cte rendering",
                    false,
                    family.expectedFragments,
                    () -> {
                        InlineView<Object> activeAccounts = DSL.defineInlineView(
                                DSL.select(accounts.ID, accounts.EMAIL)
                                        .from(accounts)
                                        .where(accounts.ACTIVE.eq(true))
                                        .limit(5));
                        Column<Integer> id = activeAccounts.field("id", SQLType.INTEGER, Nullability.NOT_NULL);
                        Column<String> email = activeAccounts.field("email", SQLType.VARCHAR, Nullability.NOT_NULL);
                        return DSL.select(id, email)
                                .from(activeAccounts)
                                .orderBy(email.asc())
                                .fetch();
                    });
            case SCALAR_SUBQUERY_RENDERING -> new GeneratedDslCase(
                    seed,
                    DslGenerativeProfile.RENDERING_BASIC.id(),
                    family.id,
                    "scalar subquery rendering",
                    false,
                    family.expectedFragments,
                    () -> {
                        Column<Integer> scalarId = DSL.scalar(
                                DSL.select(accounts.ID)
                                        .from(accounts)
                                        .where(accounts.ACTIVE.eq(true))
                                        .limit(1));
                        return DSL.select(accounts.EMAIL)
                                .from(accounts)
                                .where(accounts.ID.eqColumn(scalarId))
                                .fetch();
                    });
            case CTE_RENDERING -> new GeneratedDslCase(
                    seed,
                    DslGenerativeProfile.RENDERING_BASIC.id(),
                    family.id,
                    "named cte rendering",
                    false,
                    family.expectedFragments,
                    () -> {
                        CommonTableExpression<Object> activeAccounts = DSL.name("active_accounts")
                                .as(DSL.select(accounts.ID, accounts.EMAIL)
                                        .from(accounts)
                                        .where(accounts.ACTIVE.eq(true)));
                        Column<Integer> activeId = activeAccounts.field("id", SQLType.INTEGER, Nullability.NOT_NULL);
                        Column<String> activeEmail = activeAccounts.field("email", SQLType.VARCHAR, Nullability.NOT_NULL);
                        return DSL.with(activeAccounts)
                                .select(activeId, activeEmail)
                                .from(activeAccounts)
                                .fetch();
                    });
            case FETCH_INTO_RENDERING -> new GeneratedDslCase(
                    seed,
                    DslGenerativeProfile.RENDERING_BASIC.id(),
                    family.id,
                    "fetchInto rendering hint",
                    false,
                    family.expectedFragments,
                    () -> DSL.select(accounts.ID, accounts.EMAIL)
                            .from(accounts)
                            .fetchInto(AccountProjection.class));
        };
    }

    record AccountProjection(Integer id, String email) {
    }

    private enum RenderingFamily {
        SIMPLE_FILTER_ORDER("simple-filter-order", List.of("select", "from public.accounts", "order by email asc")),
        JOIN_RENDERING("join-rendering", List.of("left join public.plans", "on plan_id = id")),
        INLINE_VIEW_RENDERING("inline-view-rendering", List.of("with ", "from ", "order by")),
        SCALAR_SUBQUERY_RENDERING("scalar-subquery-rendering", List.of("(SELECT id FROM public.accounts", "where id = (SELECT")),
        CTE_RENDERING("cte-rendering", List.of("with active_accounts as", "from active_accounts")),
        FETCH_INTO_RENDERING("fetch-into-rendering", List.of("/* fetchInto: AccountProjection by column-name */"));

        private final String id;
        private final List<String> expectedFragments;

        RenderingFamily(String id, List<String> expectedFragments) {
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
