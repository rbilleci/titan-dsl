package io.titan.dsl;

import io.titan.test.TestContainers;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import titan.dsl.BindValue;
import titan.dsl.Column;
import titan.dsl.DSL;
import titan.dsl.DSLContext;
import titan.dsl.Filter;
import titan.dsl.FilterPolicy;
import titan.dsl.ForeignKey;
import titan.dsl.Nullability;
import titan.dsl.ParameterizedSql;
import titan.dsl.SQLType;
import titan.dsl.Scope;
import titan.dsl.SqlDialect;
import titan.dsl.Table;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static titan.dsl.FilterPath.via;

/**
 * Executes the SQL shapes that automatic filters synthesize against real PostgreSQL and MySQL
 * servers: schema-qualified column references, correlated EXISTS paths, LEFT JOIN filters in
 * ON, guarded upserts, INSERT fills, and copy-within-scope INSERT ... SELECT.
 */
@Tag("docker")
class AutomaticFilterLiveDbIT {

    static final Filter<Long> TENANT = Filter.of("tenant");
    static final Filter<Long> ORG = Filter.of("org");
    static final Filter<Long> USER = Filter.of("user");

    static final class Customers extends Table<Object> {
        public final Column<Long> ID = column("id", SQLType.BIGINT, Nullability.NOT_NULL);
        public final Column<Long> TENANT_ID = column("tenant_id", SQLType.BIGINT, Nullability.NOT_NULL);
        public final Column<Long> USER_ID = column("user_id", SQLType.BIGINT, Nullability.NOT_NULL);

        Customers() {
            super("customers", "app");
        }
    }

    static final class Orders extends Table<Object> {
        public final Column<Long> ID = column("id", SQLType.BIGINT, Nullability.NOT_NULL);
        public final Column<Long> TENANT_ID = column("tenant_id", SQLType.BIGINT, Nullability.NOT_NULL);
        public final Column<Long> ORG_ID = column("org_id", SQLType.BIGINT, Nullability.NOT_NULL);
        public final Column<Long> OWNER_ID = column("owner_id", SQLType.BIGINT, Nullability.NULLABLE);
        public final Column<Long> CUSTOMER_ID = column("customer_id", SQLType.BIGINT, Nullability.NULLABLE);
        public final ForeignKey<Object, Object> FK_CUSTOMER = foreignKey(CUSTOMER_ID, CUSTOMERS, CUSTOMERS.ID);

        Orders() {
            super("orders", "app");
        }
    }

    static final class OrderLines extends Table<Object> {
        public final Column<Long> ID = column("id", SQLType.BIGINT, Nullability.NOT_NULL);
        public final Column<Long> ORDER_ID = column("order_id", SQLType.BIGINT, Nullability.NOT_NULL);
        public final Column<Integer> QTY = column("qty", SQLType.INTEGER, Nullability.NOT_NULL);
        public final ForeignKey<Object, Object> FK_ORDER = foreignKey(ORDER_ID, ORDERS, ORDERS.ID);

        OrderLines() {
            super("order_lines", "app");
        }
    }

    static final class Users extends Table<Object> {
        public final Column<Long> ID = column("id", SQLType.BIGINT, Nullability.NOT_NULL);
        public final Column<Long> TENANT_ID = column("tenant_id", SQLType.BIGINT, Nullability.NOT_NULL);
        public final Column<Long> MANAGER_ID = column("manager_id", SQLType.BIGINT, Nullability.NULLABLE);
        public final ForeignKey<Object, Object> FK_MANAGER = foreignKey(MANAGER_ID, this, ID);

        Users() {
            super("users", "app");
        }
    }

    static final Customers CUSTOMERS = new Customers();
    static final Orders ORDERS = new Orders();
    static final OrderLines ORDER_LINES = new OrderLines();
    static final Users USERS = new Users();

    private static final List<String> DDL = List.of(
            "CREATE TABLE app.customers (id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, user_id BIGINT NOT NULL)",
            "CREATE TABLE app.orders (id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, org_id BIGINT NOT NULL,"
                    + " owner_id BIGINT NULL, customer_id BIGINT NULL REFERENCES app.customers(id))",
            "CREATE TABLE app.order_lines (id BIGINT PRIMARY KEY, order_id BIGINT NOT NULL REFERENCES app.orders(id),"
                    + " qty INTEGER NOT NULL)",
            "CREATE TABLE app.users (id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL,"
                    + " manager_id BIGINT NULL REFERENCES app.users(id))");

    private static final List<String> SEED = List.of(
            "INSERT INTO app.customers VALUES (1, 42, 100), (2, 43, 200)",
            // order 3 belongs to tenant 42 but references a tenant-43 customer (LEFT JOIN probe);
            // order 4 is visible to user 100 only, not through org 9.
            "INSERT INTO app.orders VALUES (1, 42, 9, 100, 1), (2, 42, 8, 300, NULL), (3, 42, 9, 300, 2),"
                    + " (4, 42, 8, 100, NULL), (7, 43, 9, 100, 2)",
            "INSERT INTO app.order_lines VALUES (1, 1, 5), (2, 7, 3), (3, 3, 1)",
            "INSERT INTO app.users VALUES (1, 42, NULL), (2, 42, 1), (3, 43, NULL)");

    @Test
    void postgres() throws Exception {
        TestContainers.SharedDatabase db = TestContainers.freshPostgresDatabase("titan_filters_pg");
        try (Connection connection = DriverManager.getConnection(db.jdbcUrl(), db.username(), db.password())) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA app");
            }
            prepare(connection);
            scenarios(SqlDialect.POSTGRESQL, connection);
            // Self-referential path in DELETE: PostgreSQL accepts the correlated subquery.
            assertEquals(1, executeUpdate(connection, selfPathContext(SqlDialect.POSTGRESQL)
                    .deleteFrom(USERS).where(USERS.ID.eq(2L)).render()));
        }
    }

    @Test
    void mysql() throws Exception {
        TestContainers.SharedDatabase db = TestContainers.freshMysqlDatabase("app");
        try (Connection connection = DriverManager.getConnection(db.jdbcUrl(), db.username(), db.password())) {
            prepare(connection);
            scenarios(SqlDialect.MYSQL, connection);
            // MySQL error 1093 forbids a subquery on the DELETE target table; documented limitation.
            try {
                executeUpdate(connection, selfPathContext(SqlDialect.MYSQL).deleteFrom(USERS).where(USERS.ID.eq(2L)).render());
                throw new AssertionError("expected MySQL to reject a self-referential path in DELETE");
            } catch (SQLException expected) {
                assertTrue(expected.getMessage().contains("target table"), expected.getMessage());
            }
        }
    }

    private static void scenarios(SqlDialect dialect, Connection connection) throws SQLException {
        DSLContext tenant42 = DSL.using(dialect).filters(tenantPolicy()).scoped(Scope.of(TENANT, 42L));
        DSLContext owner = DSL.using(dialect).filters(ownershipPolicy())
                .scoped(Scope.of(TENANT, 42L).with(ORG, 9L).with(USER, 100L));

        // Direct column, schema-qualified reference.
        assertEquals(Set.of(1L, 2L, 3L, 4L), ids(connection, tenant42.select(ORDERS.ID).from(ORDERS).render()));
        // Derived path: correlated EXISTS against the unaliased outer table.
        assertEquals(Set.of(1L, 3L), ids(connection, tenant42.select(ORDER_LINES.ID).from(ORDER_LINES).render()));
        // anyOf: org 9 or owner 100, within tenant 42 (order 7 is org 9 but tenant 43).
        assertEquals(Set.of(1L, 3L, 4L), ids(connection, owner.select(ORDERS.ID).from(ORDERS).render()));

        // LEFT JOIN keeps order 3 even though its customer is filtered out in ON.
        var o = ORDERS.as("o");
        var c = CUSTOMERS.as("c");
        List<List<Object>> joined = rows(connection, tenant42.select(o.col(ORDERS.ID), c.col(CUSTOMERS.ID)).from(o)
                .leftJoin(c).on(o.col(ORDERS.CUSTOMER_ID), c.col(CUSTOMERS.ID)).orderBy(o.col(ORDERS.ID).asc()).render());
        assertEquals(List.of(List.of(1L, 1L), nullable(2L), nullable(3L), nullable(4L)), joined);

        // Group transfer: owner changes only where the row stays visible through org 9.
        assertEquals(2, executeUpdate(connection, owner.update(ORDERS).set(ORDERS.OWNER_ID, 999L)
                .where(ORDERS.ID.in(1L, 3L, 4L)).render()));
        assertEquals(100L, scalar(connection, "SELECT owner_id FROM app.orders WHERE id = 4"));
        assertEquals(999L, scalar(connection, "SELECT owner_id FROM app.orders WHERE id = 3"));

        // UPDATE and DELETE touch only scoped rows.
        assertEquals(1, executeUpdate(connection, tenant42.update(ORDERS).set(ORDERS.ORG_ID, 1L)
                .where(ORDERS.ID.ne(1L).and(ORDERS.ID.ne(3L)).and(ORDERS.ID.ne(4L))).render()));
        assertEquals(1L, scalar(connection, "SELECT org_id FROM app.orders WHERE id = 2"));
        assertEquals(9L, scalar(connection, "SELECT org_id FROM app.orders WHERE id = 7"));
        assertEquals(1, executeUpdate(connection, tenant42.deleteFrom(ORDER_LINES).where(ORDER_LINES.QTY.lt(2)).render()));
        assertEquals(Set.of(1L, 2L), ids(connection, DSL.using(dialect).select(ORDER_LINES.ID).from(ORDER_LINES).render()));

        // INSERT fill.
        assertEquals(1, executeUpdate(connection, tenant42.insertInto(ORDERS).set(ORDERS.ID, 10L).set(ORDERS.ORG_ID, 9L).render()));
        assertEquals(42L, scalar(connection, "SELECT tenant_id FROM app.orders WHERE id = 10"));

        // Upsert: a key conflict with tenant 43's row 7 must not update it; row 1 is updated.
        executeUpdate(connection, tenant42.insertInto(ORDERS).set(ORDERS.ID, 7L).set(ORDERS.ORG_ID, 9L)
                .onConflict(ORDERS.ID).doUpdate().set(ORDERS.ORG_ID, 55L).render());
        assertEquals(9L, scalar(connection, "SELECT org_id FROM app.orders WHERE id = 7"));
        executeUpdate(connection, tenant42.insertInto(ORDERS).set(ORDERS.ID, 1L).set(ORDERS.ORG_ID, 9L)
                .onConflict(ORDERS.ID).doUpdate().set(ORDERS.ORG_ID, 55L).render());
        assertEquals(55L, scalar(connection, "SELECT org_id FROM app.orders WHERE id = 1"));
        // Path-bound table: the guard is a correlated EXISTS on the existing row.
        executeUpdate(connection, tenant42.insertInto(ORDER_LINES).set(ORDER_LINES.ID, 2L).set(ORDER_LINES.ORDER_ID, 1L)
                .set(ORDER_LINES.QTY, 1).onConflict(ORDER_LINES.ID).doUpdate().set(ORDER_LINES.QTY, 99).render());
        assertEquals(3L, scalar(connection, "SELECT qty FROM app.order_lines WHERE id = 2"));
        executeUpdate(connection, tenant42.insertInto(ORDER_LINES).set(ORDER_LINES.ID, 1L).set(ORDER_LINES.ORDER_ID, 1L)
                .set(ORDER_LINES.QTY, 1).onConflict(ORDER_LINES.ID).doUpdate().set(ORDER_LINES.QTY, 99).render());
        assertEquals(99L, scalar(connection, "SELECT qty FROM app.order_lines WHERE id = 1"));
        // anyOf inside the upsert guard: row 3 stays visible through org 9 and changes owner;
        // row 4 is visible only through owner 100, so the transfer is refused.
        executeUpdate(connection, owner.insertInto(ORDERS).set(ORDERS.ID, 3L).set(ORDERS.ORG_ID, 9L)
                .onConflict(ORDERS.ID).doUpdate().set(ORDERS.OWNER_ID, 777L).render());
        executeUpdate(connection, owner.insertInto(ORDERS).set(ORDERS.ID, 4L).set(ORDERS.ORG_ID, 9L)
                .onConflict(ORDERS.ID).doUpdate().set(ORDERS.OWNER_ID, 777L).render());
        assertEquals(777L, scalar(connection, "SELECT owner_id FROM app.orders WHERE id = 3"));
        assertEquals(100L, scalar(connection, "SELECT owner_id FROM app.orders WHERE id = 4"));

        // Copy within scope.
        assertEquals(5, executeUpdate(connection, tenant42.insertInto(ORDERS).columns(ORDERS.ID, ORDERS.TENANT_ID, ORDERS.ORG_ID)
                .select(tenant42.select(ORDERS.ID.add(100), ORDERS.TENANT_ID, ORDERS.ORG_ID).from(ORDERS)).render()));
        assertEquals(0L, scalar(connection, "SELECT COUNT(*) FROM app.orders WHERE id > 100 AND tenant_id <> 42"));
    }

    private static FilterPolicy tenantPolicy() {
        return FilterPolicy.builder(CUSTOMERS, ORDERS, ORDER_LINES, USERS).byColumn(TENANT, "tenant_id").derive(TENANT).build();
    }

    private static FilterPolicy ownershipPolicy() {
        return FilterPolicy.builder(ORDERS, CUSTOMERS)
                .byColumn(TENANT, "tenant_id").byColumn(ORG, "org_id").byColumn(USER, "owner_id")
                .bind(CUSTOMERS, USER, CUSTOMERS.USER_ID)
                .anyOf(ORG, USER)
                .build();
    }

    private static DSLContext selfPathContext(SqlDialect dialect) {
        FilterPolicy policy = FilterPolicy.builder(USERS)
                .bind(USERS, TENANT, via(USERS.MANAGER_ID).to(USERS.TENANT_ID))
                .build();
        return DSL.using(dialect).filters(policy).scoped(Scope.of(TENANT, 42L));
    }

    private static void prepare(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String ddl : DDL) {
                statement.execute(ddl);
            }
            for (String seed : SEED) {
                statement.execute(seed);
            }
        }
    }

    private static PreparedStatement bind(Connection connection, ParameterizedSql rendered) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(rendered.sql());
        int index = 1;
        for (BindValue value : rendered.parameters()) {
            statement.setObject(index++, value.value());
        }
        return statement;
    }

    private static int executeUpdate(Connection connection, ParameterizedSql rendered) throws SQLException {
        try (PreparedStatement statement = bind(connection, rendered)) {
            return statement.executeUpdate();
        }
    }

    private static Set<Long> ids(Connection connection, ParameterizedSql rendered) throws SQLException {
        Set<Long> ids = new TreeSet<>();
        try (PreparedStatement statement = bind(connection, rendered); ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                ids.add(rows.getLong(1));
            }
        }
        return ids;
    }

    private static List<List<Object>> rows(Connection connection, ParameterizedSql rendered) throws SQLException {
        List<List<Object>> result = new ArrayList<>();
        try (PreparedStatement statement = bind(connection, rendered); ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                List<Object> row = new ArrayList<>();
                for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) {
                    long value = rows.getLong(i);
                    row.add(rows.wasNull() ? null : value);
                }
                result.add(row);
            }
        }
        return result;
    }

    private static List<Object> nullable(Long id) {
        List<Object> row = new ArrayList<>();
        row.add(id);
        row.add(null);
        return row;
    }

    private static long scalar(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next(), "expected one row from: " + sql);
            return rows.getLong(1);
        }
    }
}
