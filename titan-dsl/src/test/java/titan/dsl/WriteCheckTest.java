package titan.dsl;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static titan.dsl.AutomaticFilterTest.ADA;
import static titan.dsl.AutomaticFilterTest.COUNTRIES;
import static titan.dsl.AutomaticFilterTest.CUSTOMERS;
import static titan.dsl.AutomaticFilterTest.ORDERS;
import static titan.dsl.AutomaticFilterTest.ORDER_LINES;
import static titan.dsl.AutomaticFilterTest.ORG;
import static titan.dsl.AutomaticFilterTest.PRODUCTS;
import static titan.dsl.AutomaticFilterTest.TENANT;
import static titan.dsl.AutomaticFilterTest.USER;
import static titan.dsl.FilterPath.via;

/**
 * Write-side scope checks: a statement may only produce rows the scope could read. Checked
 * columns are verified and filled, group members need one match, upserts guard the existing row,
 * and INSERT ... SELECT is limited to the copy-within-scope shape.
 */
class WriteCheckTest {

    static final UUID GRACE = UUID.fromString("99999999-8888-7777-6666-555555555555");

    private static FilterPolicy tenantPolicy() {
        return FilterPolicy.builder(ORDERS, CUSTOMERS, ORDER_LINES, PRODUCTS, COUNTRIES)
                .byColumn(TENANT, "tenant_id")
                .bind(ORDER_LINES, TENANT, via(ORDER_LINES.ORDER_ID))
                .exempt(COUNTRIES)
                .build();
    }

    private static FilterPolicy ownershipPolicy() {
        return FilterPolicy.builder(ORDERS, CUSTOMERS)
                .byColumn(TENANT, "tenant_id")
                .byColumn(ORG, "org_id")
                .byColumn(USER, "owner_id")
                .bind(CUSTOMERS, USER, CUSTOMERS.USER_ID)
                .anyOf(ORG, USER)
                .build();
    }

    private static DSLContext tenant42(SqlDialect dialect) {
        return DSL.using(dialect).filters(tenantPolicy()).scoped(Scope.of(TENANT, 42L));
    }

    private static DSLContext owner() {
        return DSL.using(SqlDialect.POSTGRESQL).filters(ownershipPolicy())
                .scoped(Scope.of(TENANT, 42L).with(ORG, 9L).with(USER, ADA));
    }

    private static List<Object> values(ParameterizedSql rendered) {
        return rendered.parameters().stream().map(BindValue::value).toList();
    }

    // ---------------------------------------------------------------- INSERT

    @Test
    void insertFillsAnUnassignedCheckedColumnInBothValueForms() {
        var db = tenant42(SqlDialect.POSTGRESQL);
        var single = db.insertInto(ORDERS).set(ORDERS.ID, 1L).render();
        assertEquals("INSERT INTO app.orders (id, tenant_id) VALUES (?, ?)", single.sql());
        assertEquals(List.of(1L, 42L), values(single));

        var batch = db.insertInto(ORDERS).columns(ORDERS.ID).values(1L).values(2L).render();
        assertEquals("INSERT INTO app.orders (id, tenant_id) VALUES (?, ?), (?, ?)", batch.sql());
        assertEquals(List.of(1L, 42L, 2L, 42L), values(batch));

        var query = db.insertInto(ORDERS).set(ORDERS.ID, 1L);
        assertEquals(query.render().sql(), query.render().sql());
    }

    @Test
    void insertAcceptsValuesWithinScopeAndRejectsOthers() {
        var db = tenant42(SqlDialect.POSTGRESQL);
        var explicit = db.insertInto(ORDERS).set(ORDERS.ID, 1L).set(ORDERS.TENANT_ID, 42L).render();
        assertEquals("INSERT INTO app.orders (id, tenant_id) VALUES (?, ?)", explicit.sql());
        assertEquals(List.of(1L, 42L), values(explicit));

        assertEquals("INSERT INTO app.orders (id, tenant_id) VALUES (?, ?)",
                db.insertInto(ORDERS).columns(ORDERS.ID, ORDERS.TENANT_ID).values(1L, 42).render().sql());

        var error = assertThrows(IllegalStateException.class,
                () -> db.insertInto(ORDERS).set(ORDERS.ID, 1L).set(ORDERS.TENANT_ID, 43L).render());
        assertTrue(error.getMessage().contains("outside the current scope for filter 'tenant'"), error.getMessage());
        assertThrows(IllegalStateException.class,
                () -> db.insertInto(ORDERS).columns(ORDERS.ID, ORDERS.TENANT_ID).values(1L, 42L).values(2L, 43L).render());
    }

    @Test
    void multiValuedScopeNeedsAnExplicitMemberValue() {
        var db = DSL.using(SqlDialect.POSTGRESQL).filters(tenantPolicy())
                .scoped(Scope.empty().withAny(TENANT, Set.of(41L, 42L)));
        var error = assertThrows(IllegalStateException.class, () -> db.insertInto(ORDERS).set(ORDERS.ID, 1L).render());
        assertTrue(error.getMessage().contains("several values"), error.getMessage());
        assertEquals("INSERT INTO app.orders (id, tenant_id) VALUES (?, ?)",
                db.insertInto(ORDERS).set(ORDERS.ID, 1L).set(ORDERS.TENANT_ID, 41L).render().sql());
        assertThrows(IllegalStateException.class,
                () -> db.insertInto(ORDERS).set(ORDERS.ID, 1L).set(ORDERS.TENANT_ID, 43L).render());
    }

    @Test
    void groupInsertNeedsOneMemberWithinScope() {
        var db = owner();
        assertEquals("INSERT INTO app.orders (id, org_id, tenant_id) VALUES (?, ?, ?)",
                db.insertInto(ORDERS).set(ORDERS.ID, 1L).set(ORDERS.ORG_ID, 9L).render().sql());
        assertEquals("INSERT INTO app.orders (id, org_id, owner_id, tenant_id) VALUES (?, ?, ?, ?)",
                db.insertInto(ORDERS).set(ORDERS.ID, 1L).set(ORDERS.ORG_ID, 8L).set(ORDERS.OWNER_ID, ADA).render().sql());

        var noMatch = assertThrows(IllegalStateException.class,
                () -> db.insertInto(ORDERS).set(ORDERS.ID, 1L).set(ORDERS.ORG_ID, 8L).render());
        assertTrue(noMatch.getMessage().contains("at least one of (org_id, owner_id)"), noMatch.getMessage());
        assertThrows(IllegalStateException.class, () -> db.insertInto(ORDERS).set(ORDERS.ID, 1L).render());
    }

    // ---------------------------------------------------------------- UPDATE

    @Test
    void updateCannotMoveACheckedColumnOutOfScope() {
        var db = tenant42(SqlDialect.POSTGRESQL);
        assertEquals("UPDATE app.orders SET tenant_id = ? WHERE app.orders.tenant_id = ?",
                db.update(ORDERS).set(ORDERS.TENANT_ID, 42L).render().sql());
        var moved = assertThrows(IllegalStateException.class, () -> db.update(ORDERS).set(ORDERS.TENANT_ID, 43L).render());
        assertTrue(moved.getMessage().contains("UPDATE of app.orders assigns tenant_id"), moved.getMessage());
        var expression = assertThrows(IllegalStateException.class,
                () -> db.update(ORDERS).set(ORDERS.TENANT_ID, ORDERS.TENANT_ID.add(1)).render());
        assertTrue(expression.getMessage().contains("an expression"), expression.getMessage());
    }

    @Test
    void updateOfAGroupMemberOnlyTouchesRowsThatStayVisible() {
        var db = owner();
        var transfer = db.update(ORDERS).set(ORDERS.OWNER_ID, GRACE).where(ORDERS.ID.eq(7L)).render();
        assertEquals("UPDATE app.orders SET owner_id = ? WHERE ((id = ?) AND ((app.orders.tenant_id = ?)"
                + " AND ((app.orders.org_id = ?) OR (app.orders.owner_id = ?)))) AND (app.orders.org_id = ?)", transfer.sql());
        assertEquals(List.of(GRACE, 7L, 42L, 9L, ADA, 9L), values(transfer));

        assertEquals("UPDATE app.orders SET owner_id = ? WHERE (app.orders.tenant_id = ?)"
                + " AND ((app.orders.org_id = ?) OR (app.orders.owner_id = ?))",
                db.update(ORDERS).set(ORDERS.OWNER_ID, ADA).render().sql());

        var invisible = assertThrows(IllegalStateException.class,
                () -> db.update(ORDERS).set(ORDERS.ORG_ID, 8L).set(ORDERS.OWNER_ID, GRACE).render());
        assertTrue(invisible.getMessage().contains("would become invisible"), invisible.getMessage());
    }

    // ---------------------------------------------------------------- upserts

    @Test
    void postgresUpsertGuardsTheExistingRow() {
        var db = tenant42(SqlDialect.POSTGRESQL);
        var upsert = db.insertInto(ORDERS).set(ORDERS.ID, 7L).onConflict(ORDERS.ID).doUpdate().set(ORDERS.ORG_ID, 1L).render();
        assertEquals("INSERT INTO app.orders (id, tenant_id) VALUES (?, ?) ON CONFLICT (id) DO UPDATE SET org_id = ?"
                + " WHERE app.orders.tenant_id = ?", upsert.sql());
        assertEquals(List.of(7L, 42L, 1L, 42L), values(upsert));

        assertThrows(IllegalStateException.class, () -> db.insertInto(ORDERS).set(ORDERS.ID, 7L)
                .onConflict(ORDERS.ID).doUpdate().set(ORDERS.TENANT_ID, 43L).render());

        var pathGuard = db.insertInto(ORDER_LINES).set(ORDER_LINES.ID, 1L).set(ORDER_LINES.ORDER_ID, 5L)
                .onConflict(ORDER_LINES.ID).doUpdate().set(ORDER_LINES.PRODUCT_ID, 2L).render();
        assertEquals("INSERT INTO app.order_lines (id, order_id) VALUES (?, ?) ON CONFLICT (id) DO UPDATE SET product_id = ?"
                + " WHERE EXISTS (SELECT 1 FROM app.orders AS _tf1 WHERE (_tf1.id = app.order_lines.order_id)"
                + " AND (_tf1.tenant_id = ?))", pathGuard.sql());
        assertEquals(List.of(1L, 5L, 2L, 42L), values(pathGuard));
    }

    @Test
    void mysqlUpsertNeedsCheckedColumnsInTheConflictTarget() {
        var db = tenant42(SqlDialect.MYSQL);
        var missing = assertThrows(IllegalStateException.class, () -> db.insertInto(ORDERS).set(ORDERS.ID, 7L)
                .onConflict(ORDERS.ID).doUpdate().set(ORDERS.ORG_ID, 1L).render());
        assertTrue(missing.getMessage().contains("needs tenant_id in onConflict(...)"), missing.getMessage());

        assertEquals("INSERT INTO app.orders (id, tenant_id) VALUES (?, ?) ON DUPLICATE KEY UPDATE org_id = ?",
                db.insertInto(ORDERS).set(ORDERS.ID, 7L).onConflict(ORDERS.ID, ORDERS.TENANT_ID)
                        .doUpdate().set(ORDERS.ORG_ID, 1L).render().sql());

        var path = assertThrows(IllegalStateException.class, () -> db.insertInto(ORDER_LINES)
                .set(ORDER_LINES.ID, 1L).set(ORDER_LINES.ORDER_ID, 5L)
                .onConflict(ORDER_LINES.ID).doUpdate().set(ORDER_LINES.PRODUCT_ID, 2L).render());
        assertTrue(path.getMessage().contains("cannot check the existing row for filter 'tenant'"), path.getMessage());
    }

    // ---------------------------------------------------------------- INSERT ... SELECT

    @Test
    void insertSelectAcceptsOnlyTheCopyWithinScopeShape() {
        var db = tenant42(SqlDialect.POSTGRESQL);
        var copy = db.insertInto(ORDERS).columns(ORDERS.ID, ORDERS.TENANT_ID)
                .select(db.select(ORDERS.ID, ORDERS.TENANT_ID).from(ORDERS)).render();
        assertEquals("INSERT INTO app.orders (id, tenant_id) SELECT id, tenant_id FROM app.orders"
                + " WHERE app.orders.tenant_id = ?", copy.sql());

        var o = ORDERS.as("o");
        assertTrue(db.insertInto(ORDERS).columns(ORDERS.ID, ORDERS.TENANT_ID)
                .select(db.select(o.col(ORDERS.ID), o.col(ORDERS.TENANT_ID)).from(o)
                        .join(CUSTOMERS).on(o.col(ORDERS.CUSTOMER_ID), CUSTOMERS.ID))
                .render().sql().startsWith("INSERT INTO app.orders (id, tenant_id) SELECT o.id, o.tenant_id FROM app.orders AS o"));

        assertTrue(assertThrows(IllegalStateException.class, () -> db.insertInto(ORDERS).columns(ORDERS.ID)
                .select(db.select(ORDERS.ID).from(ORDERS)).render()).getMessage().contains("must include tenant_id"));
        assertTrue(assertThrows(IllegalStateException.class, () -> db.insertInto(ORDERS).columns(ORDERS.ID, ORDERS.TENANT_ID)
                .select(db.select(ORDERS.ID, ORDERS.ORG_ID).from(ORDERS)).render()).getMessage().contains("projection 2"));
        assertTrue(assertThrows(IllegalStateException.class, () -> db.insertInto(ORDERS).columns(ORDERS.ID, ORDERS.TENANT_ID)
                .select(db.unscoped().select(ORDERS.ID, ORDERS.TENANT_ID).from(ORDERS)).render())
                .getMessage().contains("same scoped context"));
        assertTrue(assertThrows(IllegalStateException.class, () -> db.insertInto(ORDERS).columns(ORDERS.ID, ORDERS.TENANT_ID)
                .select(db.select(ORDERS.ID, ORDERS.TENANT_ID).from(ORDERS).join(CUSTOMERS).on(ORDERS.CUSTOMER_ID, CUSTOMERS.ID))
                .render()).getMessage().contains("qualify tenant_id"));
        assertTrue(assertThrows(IllegalStateException.class, () -> owner().insertInto(ORDERS).columns(ORDERS.ID, ORDERS.TENANT_ID)
                .select(owner().select(ORDERS.ID, ORDERS.TENANT_ID).from(ORDERS)).render())
                .getMessage().contains("cannot check anyOf"));
    }

    // ---------------------------------------------------------------- types, bypasses, explain

    static final class LegacyOrders extends Table<Object> {
        public final Column<Long> ID = column("id", SQLType.BIGINT, Nullability.NOT_NULL);
        public final Column<Integer> TENANT_ID = column("tenant_id", SQLType.INTEGER, Nullability.NOT_NULL);

        LegacyOrders() {
            super("legacy_orders", "app");
        }
    }

    @Test
    void scopeValuesMustMatchTheBoundColumnType() {
        var legacy = new LegacyOrders();
        var mixed = assertThrows(IllegalStateException.class, () -> FilterPolicy.builder(ORDERS, legacy)
                .byColumn(TENANT, "tenant_id").build());
        assertTrue(mixed.getMessage().contains("binds columns of different SQL types: app.orders.tenant_id BIGINT,"
                + " app.legacy_orders.tenant_id INTEGER"), mixed.getMessage());

        var policy = FilterPolicy.builder(legacy).byColumn(TENANT, "tenant_id").build();
        var db = DSL.using(SqlDialect.POSTGRESQL).filters(policy).scoped(Scope.of(TENANT, 42L));
        var read = assertThrows(IllegalStateException.class, () -> db.select(legacy.ID).from(legacy).render());
        assertTrue(read.getMessage().contains("Scope value of type Long cannot bind to column tenant_id (INTEGER)"), read.getMessage());
        assertThrows(IllegalStateException.class, () -> db.insertInto(legacy).set(legacy.ID, 1L).render());
    }

    @Test
    void skippedFiltersUnscopedContextsAndExemptTablesAreNotChecked() {
        var skipped = DSL.using(SqlDialect.POSTGRESQL).filters(tenantPolicy()).scoped(Scope.of(TENANT, 42L).skip(TENANT));
        assertEquals("INSERT INTO app.orders (id) VALUES (?)", skipped.insertInto(ORDERS).set(ORDERS.ID, 1L).render().sql());
        assertEquals("UPDATE app.orders SET tenant_id = ?", skipped.update(ORDERS).set(ORDERS.TENANT_ID, 43L).render().sql());

        var db = tenant42(SqlDialect.POSTGRESQL);
        assertEquals("INSERT INTO app.orders (id) VALUES (?)", db.unscoped().insertInto(ORDERS).set(ORDERS.ID, 1L).render().sql());
        assertEquals("INSERT INTO app.countries (code) VALUES (?)", db.insertInto(COUNTRIES).set(COUNTRIES.CODE, "NL").render().sql());
        assertEquals("INSERT INTO app.orders (id) VALUES (?)", DSL.insertInto(ORDERS).set(ORDERS.ID, 1L).render(SqlDialect.POSTGRESQL).sql());
    }

    @Test
    void explainReportsWriteHandling() {
        var tenant = tenantPolicy();
        assertEquals("app.orders\n  tenant: column tenant_id\n  write: tenant_id checked, filled when unset", tenant.explain(ORDERS));
        assertEquals("app.order_lines\n  tenant: EXISTS via order_id -> app.orders (column tenant_id)\n  write: tenant unchecked (path)",
                tenant.explain(ORDER_LINES));
        assertEquals("app.orders\n  tenant: column tenant_id\n  anyOf: org: column org_id; user: column owner_id"
                + "\n  write: tenant_id checked, filled when unset; anyOf(org, user): (org_id, owner_id) checked",
                ownershipPolicy().explain(ORDERS));
    }
}
