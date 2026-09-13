package titan.dsl.generative;

import java.util.List;
import titan.dsl.Column;
import titan.dsl.CommonTableExpression;
import titan.dsl.DSL;
import titan.dsl.InlineView;
import titan.dsl.Nullability;
import titan.dsl.SQLType;
import titan.dsl.Table;

final class DslSourceFieldGenerator {

    GeneratedDslCase generate(long seed) {
        SourceFieldFamily family = SourceFieldFamily.values()[(int) Math.floorMod(seed, SourceFieldFamily.values().length)];
        AccountsTable accounts = new AccountsTable();
        PlansTable plans = new PlansTable();

        return switch (family) {
            case INLINE_VIEW_FIELD_SELECTION -> new GeneratedDslCase(
                    seed,
                    DslGenerativeProfile.SOURCE_FIELDS.id(),
                    family.id,
                    "inline-view field selection",
                    false,
                    family.expectedFragments,
                    () -> {
                        InlineView<Object> activeAccounts = DSL.defineInlineView(
                                DSL.select(accounts.ID, accounts.EMAIL)
                                        .from(accounts)
                                        .where(accounts.ACTIVE.eq(true))
                        );
                        Column<Integer> id = activeAccounts.field("id", SQLType.INTEGER, Nullability.NOT_NULL);
                        Column<String> email = activeAccounts.field("email", SQLType.VARCHAR, Nullability.NOT_NULL);
                        return DSL.select(id, email)
                                .from(activeAccounts)
                                .orderBy(email.asc())
                                .fetch();
                    });
            case NAMED_CTE_FIELD_SELECTION -> new GeneratedDslCase(
                    seed,
                    DslGenerativeProfile.SOURCE_FIELDS.id(),
                    family.id,
                    "named cte field selection",
                    false,
                    family.expectedFragments,
                    () -> {
                        CommonTableExpression<Object> activeAccounts = DSL.name("active_accounts")
                                .as(DSL.select(accounts.ID, accounts.EMAIL)
                                        .from(accounts)
                                        .where(accounts.ACTIVE.eq(true)));
                        Column<Integer> id = activeAccounts.field("id", SQLType.INTEGER, Nullability.NOT_NULL);
                        Column<String> email = activeAccounts.field("email", SQLType.VARCHAR, Nullability.NOT_NULL);
                        return DSL.with(activeAccounts)
                                .select(id, email)
                                .from(activeAccounts)
                                .orderBy(email.asc())
                                .fetch();
                    });
            case INLINE_VIEW_JOIN_PROPAGATION -> new GeneratedDslCase(
                    seed,
                    DslGenerativeProfile.SOURCE_FIELDS.id(),
                    family.id,
                    "inline-view field propagation through join",
                    false,
                    family.expectedFragments,
                    () -> {
                        InlineView<Object> activeAccounts = DSL.defineInlineView(
                                DSL.select(accounts.ID, accounts.PLAN_ID)
                                        .from(accounts)
                                        .where(accounts.ACTIVE.eq(true))
                        );
                        Column<Integer> accountId = activeAccounts.field("id", SQLType.INTEGER, Nullability.NOT_NULL);
                        Column<Integer> planId = activeAccounts.field("plan_id", SQLType.INTEGER, Nullability.NULLABLE);
                        return DSL.select(accountId, plans.NAME)
                                .from(activeAccounts)
                                .leftJoin(plans).on(planId.eqColumn(plans.ID))
                                .fetch();
                    });
            case CTE_JOIN_PROPAGATION -> new GeneratedDslCase(
                    seed,
                    DslGenerativeProfile.SOURCE_FIELDS.id(),
                    family.id,
                    "cte field propagation through join",
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
                                .fetch();
                    });
        };
    }

    private enum SourceFieldFamily {
        INLINE_VIEW_FIELD_SELECTION("inline-view-field-selection", List.of("with ", "from ", "order by")),
        NAMED_CTE_FIELD_SELECTION("named-cte-field-selection", List.of("with active_accounts as", "from active_accounts")),
        INLINE_VIEW_JOIN_PROPAGATION("inline-view-join-propagation", List.of("left join", "plans", "plan_id")),
        CTE_JOIN_PROPAGATION("cte-join-propagation", List.of("with planned_accounts as", "join", "plans"));

        private final String id;
        private final List<String> expectedFragments;

        SourceFieldFamily(String id, List<String> expectedFragments) {
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
