package titan.dsl.generative;

import java.util.List;
import titan.dsl.Column;
import titan.dsl.DSL;
import titan.dsl.Nullability;
import titan.dsl.SQLType;
import titan.dsl.Table;

final class DslInvalidRenderingGenerator {

    GeneratedDslCase generate(long seed) {
        InvalidFamily family = InvalidFamily.values()[(int) Math.floorMod(seed, InvalidFamily.values().length)];
        AccountsTable accounts = new AccountsTable();
        UsersTable users = new UsersTable();

        return switch (family) {
            case SCALAR_MULTI_COLUMN -> new GeneratedDslCase(
                    seed,
                    DslGenerativeProfile.INVALID_RENDERING.id(),
                    family.id,
                    "scalar subquery with multiple projected columns",
                    true,
                    family.expectedFragments,
                    () -> {
                        DSL.scalar(DSL.select(users.ID, users.EMAIL).from(users), SQLType.INTEGER);
                        return "unreachable";
                    });
            case CTE_BLANK_FIELD_NAME -> new GeneratedDslCase(
                    seed,
                    DslGenerativeProfile.INVALID_RENDERING.id(),
                    family.id,
                    "cte fields reject blank names",
                    true,
                    family.expectedFragments,
                    () -> {
                        DSL.name("bad_cte").fields("id", " ");
                        return "unreachable";
                    });
            case CTE_BLANK_SQL -> new GeneratedDslCase(
                    seed,
                    DslGenerativeProfile.INVALID_RENDERING.id(),
                    family.id,
                    "cte asSql rejects blank sql",
                    true,
                    family.expectedFragments,
                    () -> {
                        DSL.name("broken_cte").asSql("   ");
                        return "unreachable";
                    });
            case FETCH_INTO_MISSING_COMPONENT -> new GeneratedDslCase(
                    seed,
                    DslGenerativeProfile.INVALID_RENDERING.id(),
                    family.id,
                    "fetchInto missing record component mapping",
                    true,
                    family.expectedFragments,
                    () -> {
                        DSL.select(accounts.ID, accounts.EMAIL)
                                .from(accounts)
                                .fetchInto(UnmappedProjection.class);
                        return "unreachable";
                    });
            case FETCH_INTO_TYPE_MISMATCH -> new GeneratedDslCase(
                    seed,
                    DslGenerativeProfile.INVALID_RENDERING.id(),
                    family.id,
                    "fetchInto type mismatch",
                    true,
                    family.expectedFragments,
                    () -> {
                        DSL.select(accounts.ID)
                                .from(accounts)
                                .fetchInto(IdAsStringProjection.class);
                        return "unreachable";
                    });
            case FETCH_INTO_PROJECTION_COUNT_MISMATCH -> new GeneratedDslCase(
                    seed,
                    DslGenerativeProfile.INVALID_RENDERING.id(),
                    family.id,
                    "fetchInto projection count mismatch",
                    true,
                    family.expectedFragments,
                    () -> {
                        DSL.select(accounts.ID, accounts.EMAIL)
                                .from(accounts)
                                .fetchInto(IdOnlyProjection.class);
                        return "unreachable";
                    });
            case FETCH_INTO_NON_IDENTIFIER_PROJECTION -> new GeneratedDslCase(
                    seed,
                    DslGenerativeProfile.INVALID_RENDERING.id(),
                    family.id,
                    "fetchInto non-identifier projection",
                    true,
                    family.expectedFragments,
                    () -> {
                        DSL.select(DSL.count())
                                .from(accounts)
                                .fetchInto(CountProjection.class);
                        return "unreachable";
                    });
            case FOR_EACH_CALLBACK_ARITY_MISMATCH -> new GeneratedDslCase(
                    seed,
                    DslGenerativeProfile.INVALID_RENDERING.id(),
                    family.id,
                    "forEach callback arity mismatch",
                    true,
                    family.expectedFragments,
                    () -> {
                        DSL.select(accounts.ID)
                                .from(accounts)
                                .forEach((id, email) -> {
                                    // unreachable
                                });
                        return "unreachable";
                    });
        };
    }

    record UnmappedProjection(Integer id, String missingField) {}
    record IdAsStringProjection(String id) {}
    record IdOnlyProjection(Integer id) {}
    record CountProjection(Long count) {}

    private enum InvalidFamily {
        SCALAR_MULTI_COLUMN("scalar-multi-column", List.of("scalar subquery must project exactly one column")),
        CTE_BLANK_FIELD_NAME("cte-blank-field-name", List.of("fieldNames must not contain blank values")),
        CTE_BLANK_SQL("cte-blank-sql", List.of("sql must not be blank")),
        FETCH_INTO_MISSING_COMPONENT("fetch-into-missing-component", List.of("fetchInto could not map record component 'missingField' by column name")),
        FETCH_INTO_TYPE_MISMATCH("fetch-into-type-mismatch", List.of("fetchInto type mismatch for component 'id': String is not compatible with INTEGER")),
        FETCH_INTO_PROJECTION_COUNT_MISMATCH(
                "fetch-into-projection-count-mismatch",
                List.of("fetchInto requires projection column count to match record component count")),
        FETCH_INTO_NON_IDENTIFIER_PROJECTION(
                "fetch-into-non-identifier-projection",
                List.of("fetchInto requires identifier-shaped projected columns, but found 'COUNT(*)'")),
        FOR_EACH_CALLBACK_ARITY_MISMATCH(
                "for-each-callback-arity-mismatch",
                List.of("forEach requires projected column count to match callback arity"));

        private final String id;
        private final List<String> expectedFragments;

        InvalidFamily(String id, List<String> expectedFragments) {
            this.id = id;
            this.expectedFragments = expectedFragments;
        }
    }

    private static final class AccountsTable extends Table<Object> {
        private final Column<Integer> ID = column("id", SQLType.INTEGER, Nullability.NOT_NULL);
        private final Column<String> EMAIL = column("email", SQLType.VARCHAR, Nullability.NOT_NULL);

        private AccountsTable() {
            super("accounts", "public");
        }
    }

    private static final class UsersTable extends Table<Object> {
        private final Column<Integer> ID = column("id", SQLType.INTEGER, Nullability.NOT_NULL);
        private final Column<String> EMAIL = column("email", SQLType.VARCHAR, Nullability.NOT_NULL);

        private UsersTable() {
            super("users", "public");
        }
    }
}
