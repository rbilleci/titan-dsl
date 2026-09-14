package titan.dsl;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static titan.dsl.FilterPath.via;

/**
 * Automatic filters: policy resolution at build time, render-time application to SELECT and
 * DML, foreign-key paths as correlated EXISTS, and the fail-closed rules around scope values.
 */
class AutomaticFilterTest {

    static final Filter<Long> TENANT = Filter.of("tenant");
    static final Filter<Long> ORG = Filter.of("org");
    static final Filter<UUID> USER = Filter.of("user");
    static final Filter<Void> LIVE = Filter.predicate("live");

    static final class Customers extends Table<Object> {
        public final Column<Long> ID = column("id", SQLType.BIGINT, Nullability.NOT_NULL);
        public final Column<Long> TENANT_ID = column("tenant_id", SQLType.BIGINT, Nullability.NOT_NULL);
        public final Column<UUID> USER_ID = column("user_id", SQLType.UUID, Nullability.NOT_NULL);

        Customers() {
            super("customers", "app");
        }
    }

    static final class Orders extends Table<Object> {
        public final Column<Long> ID = column("id", SQLType.BIGINT, Nullability.NOT_NULL);
        public final Column<Long> TENANT_ID = column("tenant_id", SQLType.BIGINT, Nullability.NOT_NULL);
        public final Column<Long> ORG_ID = column("org_id", SQLType.BIGINT, Nullability.NOT_NULL);
        public final Column<UUID> OWNER_ID = column("owner_id", SQLType.UUID, Nullability.NULLABLE);
        public final Column<Long> CUSTOMER_ID = column("customer_id", SQLType.BIGINT, Nullability.NOT_NULL);
        public final Column<LocalDateTime> DELETED_AT = column("deleted_at", SQLType.TIMESTAMP, Nullability.NULLABLE);
        public final ForeignKey<Object, Object> FK_CUSTOMER = foreignKey(CUSTOMER_ID, CUSTOMERS, CUSTOMERS.ID);

        Orders() {
            super("orders", "app");
        }
    }

    static final class Products extends Table<Object> {
        public final Column<Long> ID = column("id", SQLType.BIGINT, Nullability.NOT_NULL);
        public final Column<Long> TENANT_ID = column("tenant_id", SQLType.BIGINT, Nullability.NOT_NULL);

        Products() {
            super("products", "app");
        }
    }

    static final class OrderLines extends Table<Object> {
        public final Column<Long> ID = column("id", SQLType.BIGINT, Nullability.NOT_NULL);
        public final Column<Long> ORDER_ID = column("order_id", SQLType.BIGINT, Nullability.NOT_NULL);
        public final Column<Long> PRODUCT_ID = column("product_id", SQLType.BIGINT, Nullability.NOT_NULL);
        public final ForeignKey<Object, Object> FK_ORDER = foreignKey(ORDER_ID, ORDERS, ORDERS.ID);
        public final ForeignKey<Object, Object> FK_PRODUCT = foreignKey(PRODUCT_ID, PRODUCTS, PRODUCTS.ID);

        OrderLines() {
            super("order_lines", "app");
        }
    }

    static final class Shipments extends Table<Object> {
        public final Column<Long> ID = column("id", SQLType.BIGINT, Nullability.NOT_NULL);
        public final Column<Long> ORDER_ID = column("order_id", SQLType.BIGINT, Nullability.NOT_NULL);
        public final ForeignKey<Object, Object> FK_ORDER = foreignKey(ORDER_ID, ORDERS, ORDERS.ID);

        Shipments() {
            super("shipments", "app");
        }
    }

    static final class ShipmentEvents extends Table<Object> {
        public final Column<Long> ID = column("id", SQLType.BIGINT, Nullability.NOT_NULL);
        public final Column<Long> SHIPMENT_ID = column("shipment_id", SQLType.BIGINT, Nullability.NOT_NULL);
        public final ForeignKey<Object, Object> FK_SHIPMENT = foreignKey(SHIPMENT_ID, SHIPMENTS, SHIPMENTS.ID);

        ShipmentEvents() {
            super("shipment_events", "app");
        }
    }

    static final class Countries extends Table<Object> {
        public final Column<String> CODE = column("code", SQLType.VARCHAR, Nullability.NOT_NULL);

        Countries() {
            super("countries", "app");
        }
    }

    static final Customers CUSTOMERS = new Customers();
    static final Orders ORDERS = new Orders();
    static final Products PRODUCTS = new Products();
    static final OrderLines ORDER_LINES = new OrderLines();
    static final Shipments SHIPMENTS = new Shipments();
    static final ShipmentEvents SHIPMENT_EVENTS = new ShipmentEvents();
    static final Countries COUNTRIES = new Countries();

    static final UUID ADA = UUID.fromString("11111111-2222-3333-4444-555555555555");

    /** Tenant everywhere: by column where present, derived through foreign keys otherwise. */
    private static FilterPolicy tenantPolicy() {
        return FilterPolicy.builder(CUSTOMERS, ORDERS, PRODUCTS, ORDER_LINES, SHIPMENTS, SHIPMENT_EVENTS, COUNTRIES)
                .byColumn(TENANT, "tenant_id")
                .bind(ORDER_LINES, TENANT, via(ORDER_LINES.ORDER_ID))
                .derive(TENANT)
                .exempt(COUNTRIES)
                .build();
    }

    private static DSLContext tenant42() {
        return DSL.using(SqlDialect.POSTGRESQL).filters(tenantPolicy()).scoped(Scope.of(TENANT, 42L));
    }

    private static List<Object> values(ParameterizedSql rendered) {
        return rendered.parameters().stream().map(BindValue::value).toList();
    }

    // ---------------------------------------------------------------- direct columns

    @Test
    void directColumnFilterIsAppendedToWhereWithBindsAfterUserBinds() {
        var db = tenant42();
        var bare = db.select(ORDERS.ID).from(ORDERS).render();
        assertEquals("SELECT id FROM app.orders WHERE app.orders.tenant_id = ?", bare.sql());
        assertEquals(List.of(42L), values(bare));

        var filtered = db.select(ORDERS.ID).from(ORDERS).where(ORDERS.ID.eq(7L)).orderBy(ORDERS.ID.asc()).limit(5).render();
        assertEquals("SELECT id FROM app.orders WHERE (id = ?) AND (app.orders.tenant_id = ?) ORDER BY id ASC LIMIT 5",
                filtered.sql());
        assertEquals(List.of(7L, 42L), values(filtered));
    }

    @Test
    void everyJoinedRelationIsFilteredAndAliasesQualifyTheFilter() {
        var o = ORDERS.as("o");
        var rendered = tenant42().select(o.col(ORDERS.ID)).from(o)
                .join(CUSTOMERS).on(o.col(ORDERS.CUSTOMER_ID), CUSTOMERS.ID)
                .render();
        assertEquals("SELECT o.id FROM app.orders AS o JOIN app.customers ON o.customer_id = id"
                + " WHERE (o.tenant_id = ?) AND (app.customers.tenant_id = ?)", rendered.sql());
        assertEquals(List.of(42L, 42L), values(rendered));
    }

    @Test
    void leftJoinFilterGoesIntoOnWithUserConditionParenthesized() {
        var c = CUSTOMERS.as("c");
        var rendered = tenant42().select(ORDERS.ID).from(ORDERS)
                .leftJoin(c).on(Condition.of("customer_id = c.id OR c.id IS NULL"))
                .where(ORDERS.ID.gt(1L))
                .render();
        assertEquals("SELECT id FROM app.orders LEFT JOIN app.customers AS c"
                + " ON (customer_id = c.id OR c.id IS NULL) AND (c.tenant_id = ?)"
                + " WHERE (id > ?) AND (app.orders.tenant_id = ?)", rendered.sql());
        assertEquals(List.of(42L, 1L, 42L), values(rendered));
    }

    @Test
    void rightAndFullJoinsWithAFilteredPreservedSideAreRejectedUnlessAllowed() {
        var db = tenant42();
        var right = assertThrows(IllegalStateException.class, () -> db.select(ORDERS.ID).from(ORDERS)
                .rightJoin(CUSTOMERS).on(ORDERS.CUSTOMER_ID, CUSTOMERS.ID).render());
        assertTrue(right.getMessage().startsWith("RIGHT JOIN with an automatic filter on its preserved side"), right.getMessage());
        assertThrows(IllegalStateException.class, () -> db.select(ORDERS.ID).from(ORDERS)
                .fullOuterJoin(CUSTOMERS).on(ORDERS.CUSTOMER_ID, CUSTOMERS.ID).render());
        assertThrows(IllegalStateException.class, () -> db.select(COUNTRIES.CODE).from(COUNTRIES)
                .fullOuterJoin(CUSTOMERS).on(Condition.of("1 = 1")).render());

        // Only the preserved side is filtered: nothing is dropped, so it renders.
        assertEquals("SELECT code FROM app.countries RIGHT JOIN app.customers ON 1 = 1 WHERE app.customers.tenant_id = ?",
                db.select(COUNTRIES.CODE).from(COUNTRIES).rightJoin(CUSTOMERS).on(Condition.of("1 = 1")).render().sql());

        var allowed = FilterPolicy.builder(ORDERS, CUSTOMERS).byColumn(TENANT, "tenant_id").allowOuterJoinNarrowing().build();
        assertEquals("SELECT id FROM app.orders RIGHT JOIN app.customers ON customer_id = id"
                + " WHERE (app.orders.tenant_id = ?) AND (app.customers.tenant_id = ?)",
                DSL.using(SqlDialect.POSTGRESQL).filters(allowed).scoped(Scope.of(TENANT, 42L))
                        .select(ORDERS.ID).from(ORDERS).rightJoin(CUSTOMERS).on(ORDERS.CUSTOMER_ID, CUSTOMERS.ID).render().sql());
    }

    @Test
    void literalModeAndMysqlRenderTheSameShape() {
        var mysql = DSL.using(SqlDialect.MYSQL).filters(tenantPolicy()).scoped(Scope.of(TENANT, 42L));
        assertEquals("SELECT id FROM app.orders WHERE app.orders.tenant_id = 42",
                mysql.select(ORDERS.ID).from(ORDERS).toSql());
        assertEquals("SELECT id FROM app.orders WHERE app.orders.tenant_id = ?",
                mysql.select(ORDERS.ID).from(ORDERS).render().sql());
    }

    // ---------------------------------------------------------------- foreign-key paths

    @Test
    void explicitPathWithoutTargetEndsAtTheReferencedTablesOwnBinding() {
        var rendered = tenant42().select(ORDER_LINES.ID).from(ORDER_LINES).render();
        assertEquals("SELECT id FROM app.order_lines WHERE EXISTS (SELECT 1 FROM app.orders AS _tf1"
                + " WHERE (_tf1.id = app.order_lines.order_id) AND (_tf1.tenant_id = ?))", rendered.sql());
        assertEquals(List.of(42L), values(rendered));
    }

    @Test
    void derivedPathsFollowTheNearestBoundTableUpToMaxDepth() {
        var db = tenant42();
        assertEquals("SELECT id FROM app.shipments WHERE EXISTS (SELECT 1 FROM app.orders AS _tf1"
                + " WHERE (_tf1.id = app.shipments.order_id) AND (_tf1.tenant_id = ?))",
                db.select(SHIPMENTS.ID).from(SHIPMENTS).render().sql());
        assertEquals("SELECT id FROM app.shipment_events WHERE EXISTS (SELECT 1 FROM app.shipments AS _tf1"
                + " JOIN app.orders AS _tf2 ON _tf2.id = _tf1.order_id"
                + " WHERE (_tf1.id = app.shipment_events.shipment_id) AND (_tf2.tenant_id = ?))",
                db.select(SHIPMENT_EVENTS.ID).from(SHIPMENT_EVENTS).render().sql());
    }

    @Test
    void explicitTwoHopPathWithTargetColumn() {
        var policy = FilterPolicy.builder(CUSTOMERS, ORDERS, ORDER_LINES, PRODUCTS)
                .byColumn(USER, "user_id")
                .bind(ORDERS, USER, via(ORDERS.CUSTOMER_ID))
                .bind(ORDER_LINES, USER, via(ORDER_LINES.ORDER_ID, ORDERS.CUSTOMER_ID).to(CUSTOMERS.USER_ID))
                .exempt(PRODUCTS)
                .build();
        var rendered = DSL.using(SqlDialect.POSTGRESQL).filters(policy).scoped(Scope.of(USER, ADA))
                .select(ORDER_LINES.ID).from(ORDER_LINES).render();
        assertEquals("SELECT id FROM app.order_lines WHERE EXISTS (SELECT 1 FROM app.orders AS _tf1"
                + " JOIN app.customers AS _tf2 ON _tf2.id = _tf1.customer_id"
                + " WHERE (_tf1.id = app.order_lines.order_id) AND (_tf2.user_id = ?))", rendered.sql());
        assertEquals(List.of(ADA), values(rendered));
    }

    @Test
    void derivationRejectsAmbiguousRoutesAtBuildTime() {
        var error = assertThrows(IllegalStateException.class, () -> FilterPolicy.builder(ORDERS, PRODUCTS, ORDER_LINES, CUSTOMERS)
                .byColumn(TENANT, "tenant_id")
                .derive(TENANT)
                .build());
        assertTrue(error.getMessage().contains("app.order_lines: derived path for 'tenant' is ambiguous between [app.orders, app.products]"),
                error.getMessage());
    }

    @Test
    void derivationBeyondMaxDepthIsReportedWithTheLimit() {
        var error = assertThrows(IllegalStateException.class, () -> FilterPolicy.builder(ORDERS, CUSTOMERS, SHIPMENTS, SHIPMENT_EVENTS)
                .byColumn(TENANT, "tenant_id")
                .derive(TENANT)
                .maxDepth(1)
                .build());
        assertTrue(error.getMessage().contains("app.shipment_events: no binding for filter 'tenant' within maxDepth 1 hops"),
                error.getMessage());
    }

    @Test
    void pathProblemsAreReportedTogether() {
        var error = assertThrows(IllegalStateException.class, () -> FilterPolicy.builder(ORDERS, CUSTOMERS, ORDER_LINES, PRODUCTS)
                .byColumn(TENANT, "tenant_id")
                .bind(ORDER_LINES, TENANT, via(ORDER_LINES.ID))
                .bind(PRODUCTS, TENANT, via(PRODUCTS.ID).to(ORDERS.ORG_ID))
                .build());
        assertTrue(error.getMessage().startsWith("FilterPolicy has 2 problem(s):"), error.getMessage());
        assertTrue(error.getMessage().contains("found no single-column foreign key on app.order_lines for column id"), error.getMessage());
        assertTrue(error.getMessage().contains("found no single-column foreign key on app.products for column id"), error.getMessage());
    }

    // ---------------------------------------------------------------- composition

    @Test
    void anyOfGroupsRenderAsOrAndSkippedMembersDropOut() {
        var policy = FilterPolicy.builder(ORDERS, CUSTOMERS)
                .byColumn(TENANT, "tenant_id")
                .byColumn(ORG, "org_id")
                .byColumn(USER, "owner_id")
                .bind(CUSTOMERS, USER, CUSTOMERS.USER_ID)
                .anyOf(ORG, USER)
                .build();
        var db = DSL.using(SqlDialect.POSTGRESQL).filters(policy);

        var both = db.scoped(Scope.of(TENANT, 42L).with(ORG, 9L).with(USER, ADA)).select(ORDERS.ID).from(ORDERS).render();
        assertEquals("SELECT id FROM app.orders WHERE (app.orders.tenant_id = ?)"
                + " AND ((app.orders.org_id = ?) OR (app.orders.owner_id = ?))", both.sql());
        assertEquals(List.of(42L, 9L, ADA), values(both));

        var userOnly = db.scoped(Scope.of(TENANT, 42L).skip(ORG).with(USER, ADA)).select(ORDERS.ID).from(ORDERS).render();
        assertEquals("SELECT id FROM app.orders WHERE (app.orders.tenant_id = ?) AND (app.orders.owner_id = ?)", userOnly.sql());

        var customers = db.scoped(Scope.of(TENANT, 42L).with(ORG, 9L).with(USER, ADA)).select(CUSTOMERS.ID).from(CUSTOMERS).render();
        assertEquals("SELECT id FROM app.customers WHERE (app.customers.tenant_id = ?) AND (app.customers.user_id = ?)", customers.sql());
    }

    @Test
    void anyOfNeedsOneBoundMemberPerRelation() {
        var error = assertThrows(IllegalStateException.class, () -> FilterPolicy.builder(ORDERS, PRODUCTS)
                .byColumn(TENANT, "tenant_id").byColumn(ORG, "org_id").byColumn(USER, "owner_id")
                .anyOf(ORG, USER)
                .build());
        assertTrue(error.getMessage().contains("app.products: none of anyOf(org, user) is bound"), error.getMessage());

        FilterPolicy.builder(ORDERS, PRODUCTS)
                .byColumn(TENANT, "tenant_id").byColumn(ORG, "org_id").byColumn(USER, "owner_id")
                .anyOf(ORG, USER)
                .exemptFrom(PRODUCTS, ORG, USER)
                .build();
    }

    @Test
    void multiValuedScopeRendersIn() {
        var rendered = DSL.using(SqlDialect.POSTGRESQL).filters(tenantPolicy())
                .scoped(Scope.empty().withAny(TENANT, Set.of(42L)))
                .select(ORDERS.ID).from(ORDERS).render();
        assertEquals("SELECT id FROM app.orders WHERE app.orders.tenant_id IN (?)", rendered.sql());
        assertEquals(List.of(42L), values(rendered));
    }

    @Test
    void predicateFiltersApplyOnlyWhereBoundAndLambdaBindingsUseTheDsl() {
        var policy = FilterPolicy.builder(ORDERS, CUSTOMERS)
                .byColumn(TENANT, "tenant_id")
                .bind(ORDERS, LIVE, t -> t.col(ORDERS.DELETED_AT).isNull())
                .bind(CUSTOMERS, ORG, (t, org) -> t.col(CUSTOMERS.ID).eq(org))
                .exemptFrom(ORDERS, ORG)
                .build();
        var db = DSL.using(SqlDialect.POSTGRESQL).filters(policy).scoped(Scope.of(TENANT, 42L).with(ORG, 9L));
        assertEquals("SELECT id FROM app.orders WHERE (app.orders.tenant_id = ?) AND (app.orders.deleted_at IS NULL)",
                db.select(ORDERS.ID).from(ORDERS).render().sql());
        assertEquals("SELECT id FROM app.customers WHERE (app.customers.tenant_id = ?) AND (app.customers.id = ?)",
                db.select(CUSTOMERS.ID).from(CUSTOMERS).render().sql());
        assertThrows(IllegalArgumentException.class, () -> Scope.empty().with(LIVE, null));
    }

    @Test
    void subqueriesAndStructuredCtesRenderFiltered() {
        var db = tenant42();
        var lines = db.select(ORDER_LINES.ORDER_ID).from(ORDER_LINES);
        var rendered = db.select(ORDERS.ID).from(ORDERS).where(ORDERS.ID.in(lines)).render();
        assertEquals("SELECT id FROM app.orders WHERE (id IN (SELECT order_id FROM app.order_lines"
                + " WHERE EXISTS (SELECT 1 FROM app.orders AS _tf1 WHERE (_tf1.id = app.order_lines.order_id)"
                + " AND (_tf1.tenant_id = ?)))) AND (app.orders.tenant_id = ?)", rendered.sql());
        assertEquals(List.of(42L, 42L), values(rendered));

        var recent = db.name("recent").as(db.select(ORDERS.ID).from(ORDERS));
        var outer = db.with(recent).select(recent.field("id", Long.class)).from(recent).render();
        assertEquals("WITH recent AS (SELECT id FROM app.orders WHERE app.orders.tenant_id = ?) SELECT recent.id FROM recent",
                outer.sql());
        assertEquals(List.of(42L), values(outer));
    }

    // ---------------------------------------------------------------- DML

    @Test
    void updateDeleteAndInsertAreScoped() {
        var db = tenant42();
        var delete = db.deleteFrom(ORDERS).where(ORDERS.ID.eq(8L)).render();
        assertEquals("DELETE FROM app.orders WHERE (id = ?) AND (app.orders.tenant_id = ?)", delete.sql());
        assertEquals(List.of(8L, 42L), values(delete));

        var update = db.update(ORDERS).set(ORDERS.ORG_ID, 1L).render();
        assertEquals("UPDATE app.orders SET org_id = ? WHERE app.orders.tenant_id = ?", update.sql());
        assertEquals(List.of(1L, 42L), values(update));

        assertEquals("INSERT INTO app.orders (id, tenant_id) VALUES (?, ?)", db.insertInto(ORDERS).set(ORDERS.ID, 1L).render().sql());
    }

    // ---------------------------------------------------------------- fail-closed behavior

    @Test
    void missingScopeOrValueFailsInsteadOfWidening() {
        var db = DSL.using(SqlDialect.POSTGRESQL).filters(tenantPolicy());
        var noScope = assertThrows(IllegalStateException.class, () -> db.select(ORDERS.ID).from(ORDERS).render());
        assertTrue(noScope.getMessage().contains("has no Scope"), noScope.getMessage());

        var noValue = assertThrows(IllegalStateException.class,
                () -> db.scoped(Scope.empty()).select(ORDERS.ID).from(ORDERS).render());
        assertTrue(noValue.getMessage().contains("Filter 'tenant' has no value"), noValue.getMessage());

        assertTrue(assertThrows(IllegalStateException.class, () -> DSL.using(SqlDialect.POSTGRESQL).scoped(Scope.empty()))
                .getMessage().contains("filters(policy) first"));
    }

    @Test
    void unboundRelationsFailAtBuildAndUnknownRelationsFailAtRender() {
        var error = assertThrows(IllegalStateException.class, () -> FilterPolicy.builder(ORDERS, COUNTRIES, SHIPMENTS)
                .byColumn(TENANT, "tenant_id")
                .build());
        assertTrue(error.getMessage().startsWith("FilterPolicy has 2 problem(s):"), error.getMessage());
        assertTrue(error.getMessage().contains("app.countries: no binding for filter 'tenant'"), error.getMessage());
        assertTrue(error.getMessage().contains("app.shipments: no binding for filter 'tenant'"), error.getMessage());

        var policy = FilterPolicy.builder(ORDERS).byColumn(TENANT, "tenant_id").build();
        var db = DSL.using(SqlDialect.POSTGRESQL).filters(policy).scoped(Scope.of(TENANT, 42L));
        var unknown = assertThrows(IllegalStateException.class, () -> db.select(PRODUCTS.ID).from(PRODUCTS).render());
        assertTrue(unknown.getMessage().contains("app.products is not in the FilterPolicy catalog"), unknown.getMessage());
        assertThrows(IllegalArgumentException.class, () -> FilterPolicy.builder(ORDERS).exempt(PRODUCTS));
    }

    @Test
    void exemptRelationsUnscopedContextsAndStaticDslAreUnfiltered() {
        var db = tenant42();
        assertEquals("SELECT code FROM app.countries", db.select(COUNTRIES.CODE).from(COUNTRIES).render().sql());
        assertEquals("SELECT id FROM app.orders", db.unscoped().select(ORDERS.ID).from(ORDERS).render().sql());
        assertEquals("SELECT id FROM app.orders", DSL.select(ORDERS.ID).from(ORDERS).render(SqlDialect.POSTGRESQL).sql());
        assertNull(db.unscoped().policy());
    }

    @Test
    void scopeSourceIsReadWhenTheBuilderIsCreated() {
        var current = new AtomicReference<>(Scope.of(TENANT, 1L));
        var db = DSL.using(SqlDialect.POSTGRESQL).filters(tenantPolicy(), current::get);
        var query = db.select(ORDERS.ID).from(ORDERS);
        current.set(Scope.of(TENANT, 2L));
        assertEquals(List.of(1L), values(query.render()));
        assertEquals(List.of(2L), values(db.select(ORDERS.ID).from(ORDERS).render()));
        assertEquals(List.of(3L), values(db.scoped(Scope.of(TENANT, 3L)).select(ORDERS.ID).from(ORDERS).render()));
    }

    @Test
    void renderingNeverMutatesTheBuilder() {
        var query = tenant42().select(ORDERS.ID).from(ORDERS).where(ORDERS.ID.eq(1L));
        String first = query.render().sql();
        assertEquals(first, query.render().sql());
        assertEquals(2, query.render().parameters().size());
    }

    @Test
    void explainDescribesEveryResolvedBinding() {
        var policy = tenantPolicy();
        assertEquals("app.orders\n  tenant: column tenant_id\n  write: tenant_id checked, filled when unset", policy.explain(ORDERS));
        assertEquals("app.order_lines\n  tenant: EXISTS via order_id -> app.orders (column tenant_id)\n  write: tenant unchecked (path)",
                policy.explain(ORDER_LINES));
        assertEquals("app.shipment_events\n  tenant: EXISTS via shipment_id -> app.shipments order_id -> app.orders (column tenant_id)"
                + " [derived]\n  write: tenant unchecked (path)", policy.explain(SHIPMENT_EVENTS));
        assertEquals("app.countries: exempt", policy.explain(COUNTRIES));
        assertTrue(policy.explain().contains("app.customers\n  tenant: column tenant_id"));
        assertFalse(policy.explain().contains("not in catalog"));
    }

    @Test
    void reservedHopAliasPrefixIsRejectedUnderAPolicy() {
        var shadowing = ORDERS.as("_tf1");
        var error = assertThrows(IllegalStateException.class,
                () -> tenant42().select(shadowing.col(ORDERS.ID)).from(shadowing).render());
        assertTrue(error.getMessage().contains("reserved prefix '_tf'"), error.getMessage());
        var folded = ORDERS.as("_TF1");
        assertThrows(IllegalStateException.class, () -> tenant42().select(folded.col(ORDERS.ID)).from(folded).render());
        assertEquals("SELECT _tf1.id FROM app.orders AS _tf1",
                tenant42().unscoped().select(shadowing.col(ORDERS.ID)).from(shadowing).render().sql());
    }

    @Test
    void skipCoversPredicateFiltersAndWithAnyNeedsAColumnBinding() {
        var policy = FilterPolicy.builder(ORDERS, CUSTOMERS)
                .byColumn(TENANT, "tenant_id")
                .bind(ORDERS, LIVE, t -> t.col(ORDERS.DELETED_AT).isNull())
                .bind(CUSTOMERS, ORG, (t, org) -> t.col(CUSTOMERS.ID).eq(org))
                .exemptFrom(ORDERS, ORG)
                .build();
        var db = DSL.using(SqlDialect.POSTGRESQL).filters(policy);
        assertEquals("SELECT id FROM app.orders WHERE app.orders.tenant_id = ?",
                db.scoped(Scope.of(TENANT, 42L).skip(LIVE)).select(ORDERS.ID).from(ORDERS).render().sql());

        var error = assertThrows(IllegalStateException.class,
                () -> db.scoped(Scope.of(TENANT, 42L).withAny(ORG, List.of(1L, 2L))).select(CUSTOMERS.ID).from(CUSTOMERS).render());
        assertTrue(error.getMessage().contains("Scope.withAny"), error.getMessage());

        assertEquals("app.orders\n  tenant: column tenant_id\n  live: custom predicate\n  org: exempt"
                + "\n  write: tenant_id checked, filled when unset", policy.explain(ORDERS));
    }

    @Test
    void joinExtraPredicateIsParenthesizedBeforeTheFilterIsAdded() {
        var rendered = tenant42().select(ORDERS.ID).from(ORDERS)
                .leftJoin(CUSTOMERS).on(ORDERS.CUSTOMER_ID, CUSTOMERS.ID, CUSTOMERS.ID.eq(1L).or(CUSTOMERS.ID.eq(2L)))
                .render();
        assertEquals("SELECT id FROM app.orders LEFT JOIN app.customers"
                + " ON (customer_id = id AND ((id = ?) OR (id = ?))) AND (app.customers.tenant_id = ?)"
                + " WHERE app.orders.tenant_id = ?", rendered.sql());
        assertEquals(List.of(1L, 2L, 42L, 42L), values(rendered));
    }

    @Test
    void builderRejectsConflictingDeclarations() {
        assertThrows(IllegalArgumentException.class, () -> FilterPolicy.builder(ORDERS, ORDERS));
        assertThrows(IllegalArgumentException.class, () -> FilterPolicy.builder(ORDERS.as("o")));
        assertThrows(IllegalArgumentException.class, () -> FilterPolicy.builder(ORDERS).anyOf(TENANT));
        assertThrows(IllegalArgumentException.class, () -> FilterPolicy.builder(ORDERS).anyOf(TENANT, LIVE));
        assertThrows(IllegalArgumentException.class, () -> FilterPolicy.builder(ORDERS)
                .bind(ORDERS, TENANT, ORDERS.TENANT_ID).bind(ORDERS, TENANT, ORDERS.ORG_ID));
        var boundAndExempt = assertThrows(IllegalStateException.class, () -> FilterPolicy.builder(ORDERS)
                .bind(ORDERS, TENANT, ORDERS.TENANT_ID).exemptFrom(ORDERS, TENANT).build());
        assertTrue(boundAndExempt.getMessage().contains("both bound and exempt"), boundAndExempt.getMessage());
    }
}
