package io.titan.introspect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;
import io.titan.test.TestContainers;

// Docker-dependent (Testcontainers); runs via the integrationTest task, excluded from plain test (plan 4.5).
@org.junit.jupiter.api.Tag("docker")
class SchemaIntrospectorLiveDbIT {
    // Private databases on the shared singleton containers: this IT asserts exact view/enum
    // counts over whole schemas, so it must not share a database with other test classes.
    static final TestContainers.SharedDatabase POSTGRES =
            TestContainers.freshPostgresDatabase("titan_live_pg");

    static final TestContainers.SharedDatabase MYSQL =
            TestContainers.freshMysqlDatabase("titan_live_mysql");

    @Test
    void introspectsPostgresTablesColumnsTypesViewsAndEnums() throws Exception {
        try (Connection connection = DriverManager.getConnection(
            POSTGRES.jdbcUrl(),
            POSTGRES.username(),
            POSTGRES.password()
        )) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("DROP VIEW IF EXISTS active_accounts");
                stmt.execute("DROP TABLE IF EXISTS accounts");
                stmt.execute("DROP TABLE IF EXISTS plans");
                stmt.execute("DROP TYPE IF EXISTS account_status");
                stmt.execute("CREATE TYPE account_status AS ENUM ('ACTIVE', 'SUSPENDED')");
                stmt.execute("CREATE TABLE plans (id SERIAL PRIMARY KEY, name VARCHAR(100) NOT NULL)");
                stmt.execute("""
                    CREATE TABLE accounts (
                      id SERIAL PRIMARY KEY,
                      email VARCHAR(255) NOT NULL UNIQUE,
                      plan_id INTEGER REFERENCES plans(id),
                      status account_status NOT NULL,
                      active BOOLEAN NOT NULL,
                      balance NUMERIC(10,2),
                      created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL
                    )
                    """);
                stmt.execute("CREATE INDEX idx_accounts_active ON accounts(active)");
                stmt.execute("""
                    CREATE VIEW active_accounts AS
                    SELECT id, email FROM accounts WHERE active = TRUE
                    """);
            }

            SchemaModel model = new SchemaIntrospector().introspect(connection, Dialect.POSTGRESQL, List.of("public"));

            assertTrue(model.tables().size() >= 2);
            var table = model.tables().stream().filter(t -> t.name().equals("accounts")).findFirst().orElseThrow();
            var status = table.columns().stream().filter(c -> c.name().equals("status")).findFirst().orElseThrow();
            assertEquals("enum", status.sqlType());
            assertEquals("account_status", status.enumTypeName());
            assertEquals(List.of("ACTIVE", "SUSPENDED"), status.enumValues());
            assertEquals(1, model.enumTypes().size());
            assertEquals(1, model.views().size());
            assertEquals("active_accounts", model.views().getFirst().name());
            assertEquals(2, model.views().getFirst().columns().size());

            assertTrue(table.constraints().stream().anyMatch(c -> c.type() == SchemaModel.ConstraintType.PRIMARY_KEY));
            assertTrue(table.constraints().stream().anyMatch(c -> c.type() == SchemaModel.ConstraintType.UNIQUE));
            assertTrue(table.foreignKeys().stream().anyMatch(fk -> fk.referencedTable().equals("plans")));
            assertTrue(table.indexes().stream().anyMatch(idx -> idx.name().equals("idx_accounts_active")));
        }
    }

    @Test
    void pairsPostgresCompositeForeignKeyColumnsByOrdinal() throws Exception {
        try (Connection connection = DriverManager.getConnection(
            POSTGRES.jdbcUrl(),
            POSTGRES.username(),
            POSTGRES.password()
        )) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS fk_order_lines");
                stmt.execute("DROP TABLE IF EXISTS fk_orders");
                stmt.execute("DROP TABLE IF EXISTS fk_customers");
                stmt.execute("CREATE TABLE fk_customers (id INTEGER PRIMARY KEY)");
                stmt.execute("""
                    CREATE TABLE fk_orders (
                      tenant_id INTEGER NOT NULL,
                      order_id INTEGER NOT NULL,
                      PRIMARY KEY (tenant_id, order_id)
                    )
                    """);
                stmt.execute("""
                    CREATE TABLE fk_order_lines (
                      line_id INTEGER PRIMARY KEY,
                      customer_id INTEGER NOT NULL,
                      order_tenant_id INTEGER NOT NULL,
                      order_ref_id INTEGER NOT NULL,
                      CONSTRAINT fk_lines_customer FOREIGN KEY (customer_id) REFERENCES fk_customers (id),
                      CONSTRAINT fk_lines_order FOREIGN KEY (order_tenant_id, order_ref_id)
                        REFERENCES fk_orders (tenant_id, order_id)
                    )
                    """);
            }

            SchemaModel model = new SchemaIntrospector().introspect(connection, Dialect.POSTGRESQL, List.of("public"));

            var table = model.tables().stream().filter(t -> t.name().equals("fk_order_lines")).findFirst().orElseThrow();
            assertEquals(2, table.foreignKeys().size());

            var composite = table.foreignKeys().stream()
                .filter(fk -> fk.name().equals("fk_lines_order")).findFirst().orElseThrow();
            assertEquals(List.of("order_tenant_id", "order_ref_id"), composite.columns());
            assertEquals("public", composite.referencedSchema());
            assertEquals("fk_orders", composite.referencedTable());
            assertEquals(List.of("tenant_id", "order_id"), composite.referencedColumns());

            var single = table.foreignKeys().stream()
                .filter(fk -> fk.name().equals("fk_lines_customer")).findFirst().orElseThrow();
            assertEquals(List.of("customer_id"), single.columns());
            assertEquals("fk_customers", single.referencedTable());
            assertEquals(List.of("id"), single.referencedColumns());
        }
    }

    @Test
    void introspectsMysqlTablesColumnsTypesViewsAndEnums() throws Exception {
        try (Connection connection = DriverManager.getConnection(
            MYSQL.jdbcUrl(),
            MYSQL.username(),
            MYSQL.password()
        )) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("DROP VIEW IF EXISTS active_accounts");
                stmt.execute("DROP TABLE IF EXISTS accounts");
                stmt.execute("DROP TABLE IF EXISTS plans");
                stmt.execute("CREATE TABLE plans (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100) NOT NULL)");
                stmt.execute("""
                    CREATE TABLE accounts (
                      id INT AUTO_INCREMENT PRIMARY KEY,
                      email VARCHAR(255) NOT NULL UNIQUE,
                      plan_id INT,
                      status ENUM('ACTIVE','SUSPENDED') NOT NULL,
                      active BOOLEAN NOT NULL,
                      balance DECIMAL(10,2),
                      created_at DATETIME NOT NULL,
                      CONSTRAINT fk_accounts_plan FOREIGN KEY (plan_id) REFERENCES plans(id)
                    )
                    """);
                stmt.execute("CREATE INDEX idx_accounts_active ON accounts(active)");
                stmt.execute("""
                    CREATE VIEW active_accounts AS
                    SELECT id, email FROM accounts WHERE active = TRUE
                    """);
            }

            SchemaModel model = new SchemaIntrospector().introspect(connection, Dialect.MYSQL, List.of(MYSQL.databaseName()));

            assertTrue(model.tables().size() >= 2);
            var table = model.tables().stream().filter(t -> t.name().equals("accounts")).findFirst().orElseThrow();
            assertEquals("accounts", table.name());

            var id = table.columns().stream().filter(c -> c.name().equals("id")).findFirst().orElseThrow();
            assertEquals("integer", id.sqlType());

            var active = table.columns().stream().filter(c -> c.name().equals("active")).findFirst().orElseThrow();
            assertTrue(active.dbTypeName().equalsIgnoreCase("bit") || active.dbTypeName().equalsIgnoreCase("tinyint"));

            var status = table.columns().stream().filter(c -> c.name().equals("status")).findFirst().orElseThrow();
            assertEquals("enum", status.sqlType());
            assertEquals(List.of("ACTIVE", "SUSPENDED"), status.enumValues());
            // GAP G-8: MySQL enum type names are synthesized per column, never the raw
            // "enum('a','b')" string (which produced invalid generated class names).
            assertEquals("accounts_status_enum", status.enumTypeName());

            assertEquals(1, model.views().size());
            assertEquals("active_accounts", model.views().getFirst().name());
            assertEquals(2, model.views().getFirst().columns().size());
            assertFalse(model.enumTypes().isEmpty());
            assertTrue(model.enumTypes().stream().anyMatch(e -> e.name().equals("accounts_status_enum")
                    && e.values().equals(List.of("ACTIVE", "SUSPENDED"))));

            assertTrue(table.constraints().stream().anyMatch(c -> c.type() == SchemaModel.ConstraintType.PRIMARY_KEY));
            assertTrue(table.constraints().stream().anyMatch(c -> c.type() == SchemaModel.ConstraintType.UNIQUE));
            assertTrue(table.foreignKeys().stream().anyMatch(fk -> fk.referencedTable().equals("plans")));
            assertTrue(table.indexes().stream().anyMatch(idx -> idx.name().equals("idx_accounts_active")));
        }
    }
}
