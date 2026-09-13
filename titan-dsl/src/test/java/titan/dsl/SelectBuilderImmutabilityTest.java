package titan.dsl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Phase 3.3 (audit D-6): builder immutability — render-time fetchOne limit, set-operation
 * operand snapshots, recursive-WITH repeat calls, inline-CTE name-collision detection, and
 * deterministic inline-view/view names.
 */
class SelectBuilderImmutabilityTest {

    private static final AccountsTable ACCOUNTS = new AccountsTable();

    @Test
    void fetchOneDoesNotMutateBuilderLimit() {
        SelectBuilder query = DSL.select(ACCOUNTS.ID).from(ACCOUNTS);

        assertEquals("SELECT id FROM public.accounts LIMIT 1", query.fetchOne());
        // The render-time default must not leak into subsequent renders (audit D-6: the old
        // implementation temporarily mutated the limit field).
        assertEquals("SELECT id FROM public.accounts", query.fetch());
        assertEquals("SELECT id FROM public.accounts LIMIT 1", query.fetchOne());
    }

    @Test
    void fetchOneConcurrentRendersSeeNoSharedMutableLimit() throws Exception {
        SelectBuilder query = DSL.select(ACCOUNTS.ID).from(ACCOUNTS);
        java.util.List<Thread> threads = new java.util.ArrayList<>();
        java.util.List<String> failures = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        for (int i = 0; i < 4; i++) {
            boolean one = i % 2 == 0;
            threads.add(Thread.ofPlatform().start(() -> {
                for (int run = 0; run < 200; run++) {
                    String sql = one ? query.fetchOne() : query.fetch();
                    String expected = one
                            ? "SELECT id FROM public.accounts LIMIT 1"
                            : "SELECT id FROM public.accounts";
                    if (!expected.equals(sql)) {
                        failures.add(sql);
                    }
                }
            }));
        }
        for (Thread thread : threads) {
            thread.join();
        }
        assertTrue(failures.isEmpty(), "renders observed a mutated limit: " + failures);
    }

    @Test
    void setOperationOperandIsSnapshottedAtCompositionTime() {
        SelectBuilder operand = DSL.select(ACCOUNTS.ID).from(ACCOUNTS);
        SelectBuilder union = DSL.select(ACCOUNTS.ID).from(ACCOUNTS).union(operand);
        String composed = union.fetch();

        // Mutating the operand after union() must not change the composed query (audit D-6:
        // operands used to be held by reference).
        operand.where(ACCOUNTS.ACTIVE.eq(true)).limit(7);

        assertEquals(composed, union.fetch());
        assertEquals("SELECT id FROM public.accounts UNION SELECT id FROM public.accounts", composed);
    }

    @Test
    void repeatWithCallsDoNotDowngradeRecursiveRegistration() {
        CommonTableExpression<Object> recursiveCte = DSL.name("numbers")
                .as(DSL.select(ACCOUNTS.ID).from(ACCOUNTS));
        CommonTableExpression<Object> plainCte = DSL.name("actives")
                .as(DSL.select(ACCOUNTS.ID).from(ACCOUNTS).where(ACCOUNTS.ACTIVE.eq(true)));

        SelectBuilder query = DSL.select(ACCOUNTS.ID)
                .from(ACCOUNTS)
                .with(true, recursiveCte)
                .with(false, plainCte);

        assertTrue(query.fetch().startsWith("WITH RECURSIVE "),
                "a later non-recursive with(...) call must not clear the RECURSIVE flag (audit D-6): "
                        + query.fetch());
    }

    @Test
    void inlineCteNameCollisionWithDifferentBodyThrows() {
        CommonTableExpression<Object> first = DSL.name("active_accounts")
                .as(DSL.select(ACCOUNTS.ID).from(ACCOUNTS).where(ACCOUNTS.ACTIVE.eq(true)));
        CommonTableExpression<Object> conflicting = DSL.name("active_accounts")
                .as(DSL.select(ACCOUNTS.EMAIL).from(ACCOUNTS).where(ACCOUNTS.ACTIVE.eq(false)));

        SelectBuilder query = DSL.select(ACCOUNTS.ID).from(first);
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> query.join(conflicting));
        assertTrue(exception.getMessage().contains("active_accounts"), exception.getMessage());
        assertTrue(exception.getMessage().contains("different query body"), exception.getMessage());
    }

    @Test
    void inlineCteReRegistrationWithSameBodyIsDeduplicated() {
        CommonTableExpression<Object> cte = DSL.name("active_accounts")
                .as(DSL.select(ACCOUNTS.ID).from(ACCOUNTS).where(ACCOUNTS.ACTIVE.eq(true)));
        CommonTableExpression<Object> sameBody = DSL.name("active_accounts")
                .as(DSL.select(ACCOUNTS.ID).from(ACCOUNTS).where(ACCOUNTS.ACTIVE.eq(true)));

        String sql = DSL.select(ACCOUNTS.ID)
                .from(cte)
                .join(sameBody).on(Condition.of("1 = 1"))
                .fetch();

        assertEquals(1, countOccurrences(sql, "active_accounts AS ("),
                "identical re-registration must emit the CTE exactly once: " + sql);
    }

    @Test
    void inlineViewNamesAreDerivedDeterministicallyFromContent() {
        SelectBuilder queryA = DSL.select(ACCOUNTS.ID).from(ACCOUNTS).where(ACCOUNTS.ACTIVE.eq(true));
        SelectBuilder queryB = DSL.select(ACCOUNTS.ID).from(ACCOUNTS).where(ACCOUNTS.ACTIVE.eq(true));
        SelectBuilder different = DSL.select(ACCOUNTS.EMAIL).from(ACCOUNTS);

        InlineView<Object> viewA = DSL.defineInlineView(queryA);
        InlineView<Object> viewB = DSL.defineInlineView(queryB);
        InlineView<Object> viewC = DSL.defineInlineView(different);

        assertEquals(viewA.name(), viewB.name(),
                "identical bodies must yield identical names regardless of call order (audit D-6)");
        assertNotEquals(viewA.name(), viewC.name(), "different bodies must not collide");
        assertTrue(viewA.name().matches("inline_view_[0-9a-f]{12}"), viewA.name());
    }

    @Test
    void viewDefinitionNamesAreDerivedDeterministicallyFromContent() {
        ViewDef<Object> viewA = DSL.defineView(DSL.select(ACCOUNTS.ID).from(ACCOUNTS));
        ViewDef<Object> viewB = DSL.defineView(DSL.select(ACCOUNTS.ID).from(ACCOUNTS));
        ViewDef<Object> viewC = DSL.defineView(DSL.select(ACCOUNTS.EMAIL).from(ACCOUNTS));

        assertEquals(viewA.name(), viewB.name());
        assertNotEquals(viewA.name(), viewC.name());
        assertTrue(viewA.name().matches("view_def_[0-9a-f]{12}"), viewA.name());
    }

    @Test
    void fetchCountIgnoresOrderLimitAndOffsetByDocumentedDecision() {
        String sql = DSL.select(ACCOUNTS.ID)
                .from(ACCOUNTS)
                .where(ACCOUNTS.ACTIVE.eq(true))
                .orderBy(ACCOUNTS.ID.desc())
                .limit(25)
                .offset(50)
                .fetchCount();

        assertEquals("SELECT COUNT(*) FROM (SELECT id FROM public.accounts WHERE active = TRUE) titan_count", sql);
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = haystack.indexOf(needle);
        while (index >= 0) {
            count++;
            index = haystack.indexOf(needle, index + needle.length());
        }
        return count;
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
