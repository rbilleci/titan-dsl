package io.titan.introspect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * GAP G-6: the fallback regex parser must never drop SQL silently. Every statement (or
 * CREATE TABLE clause) it does not recognize is a hard error naming the offending SQL and
 * pointing at container-backed introspection.
 */
class DdlSchemaParserStrictTest {

    private final DdlSchemaParser parser = new DdlSchemaParser();

    private DdlParseException parseFails(String ddl, Dialect dialect) {
        return assertThrows(DdlParseException.class, () -> parser.parse(ddl, dialect, "public"));
    }

    @Test
    void mysqlTableOptionsLikeEngineAreHardErrorsNotSilentDrops() {
        DdlParseException failure = parseFails("""
                CREATE TABLE accounts (
                  id INT NOT NULL,
                  PRIMARY KEY (id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """, Dialect.MYSQL);

        assertTrue(failure.getMessage().contains("ENGINE=InnoDB"),
                "must quote the offending statement, was:\n" + failure.getMessage());
        assertTrue(failure.getMessage().contains("container"),
                "must point at container-backed introspection, was:\n" + failure.getMessage());
    }

    @Test
    void createOrReplaceViewIsAHardError() {
        DdlParseException failure = parseFails(
                "CREATE OR REPLACE VIEW active_accounts AS SELECT id FROM accounts;", Dialect.POSTGRESQL);
        assertTrue(failure.getMessage().contains("CREATE OR REPLACE VIEW active_accounts"));
        assertTrue(failure.getMessage().contains("ddlMode = 'container'"));
    }

    @Test
    void alterTableAddConstraintIsAHardError() {
        DdlParseException failure = parseFails("""
                CREATE TABLE orders (id INT NOT NULL, customer_id INT, PRIMARY KEY (id));
                ALTER TABLE orders ADD CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES customers (id);
                """, Dialect.POSTGRESQL);
        assertTrue(failure.getMessage().contains("ALTER TABLE orders ADD CONSTRAINT"));
    }

    @Test
    void createTypeAndCreateSequenceAreHardErrors() {
        assertTrue(parseFails("CREATE TYPE account_status AS ENUM ('ACTIVE', 'SUSPENDED');", Dialect.POSTGRESQL)
                .getMessage().contains("CREATE TYPE account_status"));
        assertTrue(parseFails("CREATE SEQUENCE account_id_seq START 1;", Dialect.POSTGRESQL)
                .getMessage().contains("CREATE SEQUENCE account_id_seq"));
    }

    @Test
    void unrecognizedTableClauseIsAHardErrorNamingTheTable() {
        DdlParseException failure = parseFails("""
                CREATE TABLE reservations (
                  id INT NOT NULL,
                  room INT,
                  EXCLUDE USING gist (room WITH =)
                );
                """, Dialect.POSTGRESQL);
        assertTrue(failure.getMessage().contains("reservations"));
        assertTrue(failure.getMessage().contains("EXCLUDE USING gist"));
    }

    @Test
    void deferrableForeignKeyIsAHardErrorButOnDeleteIsGenuinelyHandled() {
        assertTrue(parseFails("""
                CREATE TABLE orders (
                  id INT NOT NULL,
                  customer_id INT,
                  CONSTRAINT fk_c FOREIGN KEY (customer_id) REFERENCES customers (id) DEFERRABLE INITIALLY DEFERRED
                );
                """, Dialect.POSTGRESQL).getMessage().contains("DEFERRABLE"));

        // ON DELETE / ON UPDATE carry no SchemaModel metadata (the JDBC path does not read
        // referential actions either), so the parser handles them instead of erroring.
        SchemaModel model = parser.parse("""
                CREATE TABLE orders (
                  id INT NOT NULL,
                  customer_id INT,
                  CONSTRAINT fk_c FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE CASCADE ON UPDATE SET NULL
                );
                """, Dialect.POSTGRESQL, "public");
        assertEquals(1, model.tables().getFirst().foreignKeys().size());
        assertEquals("fk_c", model.tables().getFirst().foreignKeys().getFirst().name());
        assertEquals("customers", model.tables().getFirst().foreignKeys().getFirst().referencedTable());
    }

    @Test
    void checkConstraintsAndCommentsAreRecognizedAndIgnored() {
        SchemaModel model = parser.parse("""
                -- accounts of the tenant
                CREATE TABLE accounts (
                  id INT NOT NULL, -- surrogate key
                  balance NUMERIC(10,2) CHECK (balance >= 0),
                  CONSTRAINT positive_balance CHECK (balance >= 0),
                  PRIMARY KEY (id)
                );

                /* block comment between statements */
                COMMENT ON TABLE accounts IS 'tenant accounts';
                """, Dialect.POSTGRESQL, "public");

        assertEquals(1, model.tables().size());
        assertEquals(2, model.tables().getFirst().columns().size());
        assertEquals(1, model.tables().getFirst().constraints().size());
    }

    @Test
    void standaloneIndexOnUnknownTableIsAHardError() {
        DdlParseException failure = parseFails(
                "CREATE INDEX idx_ghost ON ghost_table (id);", Dialect.POSTGRESQL);
        assertTrue(failure.getMessage().contains("ghost_table"));
    }

    @Test
    void mysqlInlineEnumIsParsedWithSynthesizedTypeName() {
        SchemaModel model = parser.parse("""
                CREATE TABLE accounts (
                  id INT NOT NULL,
                  status ENUM('ACTIVE','SUSPENDED') NOT NULL,
                  PRIMARY KEY (id)
                );
                """, Dialect.MYSQL, "titan");

        var status = model.tables().getFirst().columns().stream()
                .filter(c -> c.name().equals("status")).findFirst().orElseThrow();
        assertEquals("enum", status.sqlType());
        assertEquals("accounts_status_enum", status.enumTypeName());
        assertEquals(List.of("ACTIVE", "SUSPENDED"), status.enumValues());
        assertEquals(1, model.enumTypes().size());
        assertEquals("accounts_status_enum", model.enumTypes().getFirst().name());
        assertEquals("titan", model.enumTypes().getFirst().schema());
    }

    @Test
    void multiWordTypesKeepFidelityInsteadOfTruncating() {
        SchemaModel model = parser.parse("""
                CREATE TABLE events (
                  id BIGINT NOT NULL,
                  label CHARACTER VARYING(40),
                  happened_at TIMESTAMP WITH TIME ZONE,
                  recorded_at TIMESTAMP WITHOUT TIME ZONE,
                  factor DOUBLE PRECISION,
                  amount NUMERIC(12,2),
                  PRIMARY KEY (id)
                );
                """, Dialect.POSTGRESQL, "public");

        var columns = model.tables().getFirst().columns();
        var label = columns.stream().filter(c -> c.name().equals("label")).findFirst().orElseThrow();
        // G-7 regression: "character varying" used to truncate to "character".
        assertEquals("character varying", label.dbTypeName());
        assertEquals("varchar", label.sqlType());
        assertEquals(40, label.length());

        var happenedAt = columns.stream().filter(c -> c.name().equals("happened_at")).findFirst().orElseThrow();
        assertEquals("timestamptz", happenedAt.sqlType());
        assertEquals("timestamp with time zone", happenedAt.dbTypeName());

        var recordedAt = columns.stream().filter(c -> c.name().equals("recorded_at")).findFirst().orElseThrow();
        assertEquals("timestamp", recordedAt.sqlType());

        var factor = columns.stream().filter(c -> c.name().equals("factor")).findFirst().orElseThrow();
        assertEquals("double precision", factor.dbTypeName());

        var amount = columns.stream().filter(c -> c.name().equals("amount")).findFirst().orElseThrow();
        assertEquals(12, amount.precision());
        assertEquals(2, amount.scale());

        var id = columns.stream().filter(c -> c.name().equals("id")).findFirst().orElseThrow();
        assertEquals(64, id.precision());
        assertEquals(0, id.scale());
    }
}
