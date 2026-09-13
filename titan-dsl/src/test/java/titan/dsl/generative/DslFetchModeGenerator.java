package titan.dsl.generative;

import java.util.List;
import titan.dsl.Column;
import titan.dsl.DSL;
import titan.dsl.Nullability;
import titan.dsl.SQLType;
import titan.dsl.Table;

final class DslFetchModeGenerator {

    GeneratedDslCase generate(long seed) {
        FetchFamily family = FetchFamily.values()[(int) Math.floorMod(seed, FetchFamily.values().length)];
        AccountsTable accounts = new AccountsTable();

        return switch (family) {
            case FETCH -> new GeneratedDslCase(
                    seed,
                    DslGenerativeProfile.FETCH_MODES.id(),
                    family.id,
                    "basic fetch rendering",
                    false,
                    family.expectedFragments,
                    () -> DSL.select(accounts.ID, accounts.EMAIL)
                            .from(accounts)
                            .where(accounts.ACTIVE.eq(true))
                            .fetch());
            case FETCH_ONE -> new GeneratedDslCase(
                    seed,
                    DslGenerativeProfile.FETCH_MODES.id(),
                    family.id,
                    "fetchOne rendering",
                    false,
                    family.expectedFragments,
                    () -> DSL.select(accounts.EMAIL)
                            .from(accounts)
                            .where(accounts.ID.eq(1))
                            .fetchOne());
            case FETCH_COUNT -> new GeneratedDslCase(
                    seed,
                    DslGenerativeProfile.FETCH_MODES.id(),
                    family.id,
                    "fetchCount rendering",
                    false,
                    family.expectedFragments,
                    () -> DSL.select(accounts.ID)
                            .from(accounts)
                            .where(accounts.ACTIVE.eq(true))
                            .fetchCount());
            case FETCH_EXISTS -> new GeneratedDslCase(
                    seed,
                    DslGenerativeProfile.FETCH_MODES.id(),
                    family.id,
                    "fetchExists rendering",
                    false,
                    family.expectedFragments,
                    () -> DSL.select(accounts.ID)
                            .from(accounts)
                            .where(accounts.ACTIVE.eq(true))
                            .fetchExists());
            case FETCH_INTO -> new GeneratedDslCase(
                    seed,
                    DslGenerativeProfile.FETCH_MODES.id(),
                    family.id,
                    "fetchInto rendering",
                    false,
                    family.expectedFragments,
                    () -> DSL.select(accounts.ID, accounts.EMAIL)
                            .from(accounts)
                            .fetchInto(AccountProjection.class));
            case FETCH_INTO_WITH_INLINE_VIEW -> new GeneratedDslCase(
                    seed,
                    DslGenerativeProfile.FETCH_MODES.id(),
                    family.id,
                    "fetchInto rendering with inline-view source",
                    false,
                    family.expectedFragments,
                    () -> {
                        var view = DSL.defineInlineView(
                                DSL.select(accounts.ID, accounts.EMAIL)
                                        .from(accounts)
                                        .where(accounts.ACTIVE.eq(true))
                        );
                        Column<Integer> id = view.field("id", SQLType.INTEGER, Nullability.NOT_NULL);
                        Column<String> email = view.field("email", SQLType.VARCHAR, Nullability.NOT_NULL);
                        return DSL.select(id, email)
                                .from(view)
                                .fetchInto(AccountProjection.class);
                    });
        };
    }

    record AccountProjection(Integer id, String email) {
    }

    private enum FetchFamily {
        FETCH("fetch", List.of("select id, email", "from public.accounts")),
        FETCH_ONE("fetch-one", List.of("select email", "where id = 1")),
        FETCH_COUNT("fetch-count", List.of("count(", "from public.accounts")),
        FETCH_EXISTS("fetch-exists", List.of("select exists", "from public.accounts")),
        FETCH_INTO("fetch-into", List.of("/* fetchInto: AccountProjection by column-name */")),
        FETCH_INTO_WITH_INLINE_VIEW("fetch-into-inline-view", List.of("with ", "/* fetchInto: AccountProjection by column-name */"));

        private final String id;
        private final List<String> expectedFragments;

        FetchFamily(String id, List<String> expectedFragments) {
            this.id = id;
            this.expectedFragments = expectedFragments;
        }
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
