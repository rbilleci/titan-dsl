package titan.dsl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CommonTableExpressionTest {

    private static final AccountsTable ACCOUNTS = new AccountsTable();
    private static final NumberSeedTable NUMBER_SEED = new NumberSeedTable();

    @Test
    void withClauseRendersForNonRecursiveCte() {
        CommonTableExpression<Object> activeAccounts = DSL.name("active_accounts")
                .as(DSL.select(ACCOUNTS.ID, ACCOUNTS.EMAIL)
                        .from(ACCOUNTS)
                        .where(ACCOUNTS.ACTIVE.eq(true)));

        Column<Integer> activeId = activeAccounts.field("id", SQLType.INTEGER, Nullability.NOT_NULL);
        Column<String> activeEmail = activeAccounts.field("email", SQLType.VARCHAR, Nullability.NOT_NULL);

        String sql = DSL.with(activeAccounts)
                .select(activeId, activeEmail)
                .from(activeAccounts)
                .orderBy(activeEmail.asc())
                .fetch();

        assertEquals(
                "WITH active_accounts AS (SELECT id, email FROM public.accounts WHERE active = TRUE) "
                        + "SELECT active_accounts.id, active_accounts.email FROM active_accounts ORDER BY active_accounts.email ASC",
                sql);
    }

    @Test
    void withRecursiveClauseRendersForSelfReferencingCteSql() {
        CommonTableExpression<Object> orgTree = DSL.name("org_tree")
                .fields("id", "parent_id", "depth")
                .asSql("SELECT id, parent_id, 0 AS depth FROM public.org_nodes WHERE id = 42 "
                        + "UNION ALL "
                        + "SELECT n.id, n.parent_id, t.depth + 1 FROM public.org_nodes n "
                        + "JOIN org_tree t ON n.parent_id = t.id");

        Column<Integer> id = orgTree.field("id", SQLType.INTEGER, Nullability.NOT_NULL);
        Column<Integer> depth = orgTree.field("depth", SQLType.INTEGER, Nullability.NOT_NULL);

        String sql = DSL.withRecursive(orgTree)
                .select(id, depth)
                .from(orgTree)
                .orderBy(depth.asc())
                .fetch();

        assertEquals(
                "WITH RECURSIVE org_tree (id, parent_id, depth) AS (SELECT id, parent_id, 0 AS depth FROM public.org_nodes WHERE id = 42 "
                        + "UNION ALL "
                        + "SELECT n.id, n.parent_id, t.depth + 1 FROM public.org_nodes n "
                        + "JOIN org_tree t ON n.parent_id = t.id) "
                        + "SELECT org_tree.id, org_tree.depth FROM org_tree ORDER BY org_tree.depth ASC",
                sql);
    }

    @Test
    void recursiveBuilderSupportsSelfReferenceInUnionAllBranch() {
        CommonTableExpression<Object> orgTree = DSL.name("org_tree")
                .fields("id")
                .asRecursive(self -> DSL.select(NUMBER_SEED.ID)
                        .from(NUMBER_SEED)
                        .unionAll(
                                DSL.select(self.field("id", SQLType.INTEGER, Nullability.NOT_NULL))
                                        .from(self)
                                        .where(self.field("id", SQLType.INTEGER, Nullability.NOT_NULL).lt(10))));

        Column<Integer> id = orgTree.field("id", SQLType.INTEGER, Nullability.NOT_NULL);

        String sql = DSL.withRecursive(orgTree)
                .select(id)
                .from(orgTree)
                .fetch();

        assertEquals(
                "WITH RECURSIVE org_tree (id) AS (SELECT id FROM public.number_seed UNION ALL SELECT org_tree.id FROM org_tree WHERE org_tree.id < 10) "
                        + "SELECT org_tree.id FROM org_tree",
                sql);
    }

    @Test
    void inlineViewAutoEmitsAsCteWhenSelectedFrom() {
        InlineView<Object> highValueAccounts = DSL.defineInlineView(
                DSL.select(ACCOUNTS.ID, ACCOUNTS.EMAIL)
                        .from(ACCOUNTS)
                        .where(ACCOUNTS.ACTIVE.eq(true))
                        .limit(10));

        Column<Integer> id = highValueAccounts.field("id", SQLType.INTEGER, Nullability.NOT_NULL);
        Column<String> email = highValueAccounts.field("email", SQLType.VARCHAR, Nullability.NOT_NULL);

        String sql = DSL.select(id, email)
                .from(highValueAccounts)
                .orderBy(email.asc())
                .fetch();

        assertEquals(
                "WITH " + highValueAccounts.name() + " AS (SELECT id, email FROM public.accounts WHERE active = TRUE LIMIT 10) "
                        + "SELECT " + highValueAccounts.name() + ".id, " + highValueAccounts.name() + ".email "
                        + "FROM " + highValueAccounts.name() + " ORDER BY " + highValueAccounts.name() + ".email ASC",
                sql);
    }

    @Test
    void defineViewCapturesSelectSqlWithoutInlineCteEmission() {
        ViewDef<Object> highValueAccounts = DSL.defineView(
                DSL.select(ACCOUNTS.ID, ACCOUNTS.EMAIL)
                        .from(ACCOUNTS)
                        .where(ACCOUNTS.ACTIVE.eq(true)));

        assertEquals("SELECT id, email FROM public.accounts WHERE active = TRUE", highValueAccounts.querySql());

        Column<Integer> id = highValueAccounts.column("id", SQLType.INTEGER, Nullability.NOT_NULL);
        String sql = DSL.select(id)
                .from(highValueAccounts)
                .fetch();

        assertTrue(sql.startsWith("SELECT id FROM public." + highValueAccounts.name()));
        assertTrue(!sql.startsWith("WITH "));
    }

    @Test
    void cteAsSqlRejectsBlankSql() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> DSL.name("broken_cte").asSql("   "));

        assertEquals("sql must not be blank", error.getMessage());
    }

    @Test
    void cteFieldsRejectsBlankNames() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> DSL.name("bad_cte").fields("id", " "));

        assertEquals("fieldNames must not contain blank values", error.getMessage());
    }

    @Test
    void cteFieldSupportsJavaTypeOverload() {
        CommonTableExpression<Object> activeAccounts = DSL.name("active_accounts")
                .as(DSL.select(ACCOUNTS.ID, ACCOUNTS.EMAIL)
                        .from(ACCOUNTS)
                        .where(ACCOUNTS.ACTIVE.eq(true)));

        Column<Integer> activeId = activeAccounts.field("id", Integer.class);
        Column<String> activeEmail = activeAccounts.field("email", String.class);

        assertEquals(SQLType.INTEGER, activeId.sqlType());
        assertEquals(Nullability.NULLABLE, activeId.nullability());
        assertEquals(SQLType.VARCHAR, activeEmail.sqlType());
        assertEquals(Nullability.NULLABLE, activeEmail.nullability());
    }

    @Test
    void cteFieldRejectsUnknownFieldWhenNamesDeclared() {
        CommonTableExpression<Object> cte = DSL.name("active_accounts")
                .fields("id", "email")
                .asSql("SELECT id, email FROM public.accounts");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> cte.field("missing", SQLType.INTEGER, Nullability.NULLABLE));

        assertTrue(error.getMessage().contains("CTE field 'missing' is not declared in active_accounts"));
    }

    @Test
    void cteFieldAllowsAnyNameWhenNoDeclaredFieldsProvided() {
        CommonTableExpression<Object> cte = DSL.name("active_accounts")
                .asSql("SELECT id, email FROM public.accounts");

        Column<Integer> derived = cte.field("computed_rank", Integer.class);

        assertEquals("active_accounts.computed_rank", derived.name());
    }

    private static final class AccountsTable extends Table<Object> {
        private final Column<Integer> ID = column("id", SQLType.INTEGER, Nullability.NOT_NULL);
        private final Column<String> EMAIL = column("email", SQLType.VARCHAR, Nullability.NOT_NULL);
        private final Column<Boolean> ACTIVE = column("active", SQLType.BOOLEAN, Nullability.NOT_NULL);

        private AccountsTable() {
            super("accounts", "public");
        }
    }

    private static final class NumberSeedTable extends Table<Object> {
        private final Column<Integer> ID = column("id", SQLType.INTEGER, Nullability.NOT_NULL);

        private NumberSeedTable() {
            super("number_seed", "public");
        }
    }
}
