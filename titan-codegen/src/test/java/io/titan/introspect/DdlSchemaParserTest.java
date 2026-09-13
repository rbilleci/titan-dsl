package io.titan.introspect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class DdlSchemaParserTest {

    @Test
    void parsesPostgresCreateTableAndView() {
        String ddl = """
                CREATE TABLE public.accounts (
                  id int4 NOT NULL,
                  email varchar(255) NOT NULL,
                  active boolean,
                  plan_id int,
                  CONSTRAINT accounts_pkey PRIMARY KEY (id),
                  CONSTRAINT accounts_email_key UNIQUE (email),
                  CONSTRAINT accounts_plan_fk FOREIGN KEY (plan_id) REFERENCES public.plans (id),
                  INDEX idx_accounts_active (active)
                );

                CREATE VIEW public.active_accounts AS
                SELECT a.id, a.email AS email_address
                FROM public.accounts a;
                """;

        SchemaModel model = new DdlSchemaParser().parse(ddl, Dialect.POSTGRESQL, "public");

        assertEquals(1, model.tables().size());
        var table = model.tables().getFirst();
        assertEquals("public", table.schema());
        assertEquals("accounts", table.name());
        assertEquals(4, table.columns().size());
        assertEquals("integer", table.columns().get(0).sqlType());
        assertEquals("varchar", table.columns().get(1).sqlType());
        assertEquals(255, table.columns().get(1).length());
        assertTrue(table.columns().get(2).nullable());

        assertEquals(2, table.constraints().size());
        assertTrue(table.constraints().stream().anyMatch(c -> c.name().equals("accounts_pkey")
                && c.type() == SchemaModel.ConstraintType.PRIMARY_KEY));
        assertTrue(table.constraints().stream().anyMatch(c -> c.name().equals("accounts_email_key")
                && c.type() == SchemaModel.ConstraintType.UNIQUE));

        assertEquals(1, table.foreignKeys().size());
        assertEquals("accounts_plan_fk", table.foreignKeys().get(0).name());
        assertEquals("public", table.foreignKeys().get(0).referencedSchema());
        assertEquals("plans", table.foreignKeys().get(0).referencedTable());
        assertEquals(2, table.indexes().size());
        assertTrue(table.indexes().stream().anyMatch(i -> i.name().equals("accounts_email_key") && i.unique()));
        assertTrue(table.indexes().stream().anyMatch(i -> i.name().equals("idx_accounts_active") && !i.unique()));

        assertEquals(1, model.views().size());
        var view = model.views().getFirst();
        assertEquals("active_accounts", view.name());
        assertEquals(2, view.columns().size());
        assertEquals("id", view.columns().get(0).name());
        assertEquals("email_address", view.columns().get(1).name());
    }

    @Test
    void parsesMysqlCreateTableWithDefaultSchema() {
        String ddl = """
                CREATE TABLE accounts (
                  id int NOT NULL PRIMARY KEY,
                  created_at datetime NOT NULL,
                  plan_code varchar(32),
                  tenant_id int,
                  UNIQUE KEY uq_accounts_plan_code (plan_code),
                  KEY idx_accounts_created_at (created_at),
                  CONSTRAINT fk_accounts_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
                );
                """;

        SchemaModel model = new DdlSchemaParser().parse(ddl, Dialect.MYSQL, "app");

        assertEquals(1, model.tables().size());
        var table = model.tables().getFirst();
        assertEquals("app", table.schema());
        assertEquals("accounts", table.name());

        assertEquals("integer", table.columns().get(0).sqlType());
        assertEquals("timestamp", table.columns().get(1).sqlType());
        assertEquals("varchar", table.columns().get(2).sqlType());
        assertEquals(32, table.columns().get(2).length());

        assertEquals(2, table.constraints().size());
        assertTrue(table.constraints().stream().anyMatch(c -> c.type() == SchemaModel.ConstraintType.PRIMARY_KEY));
        assertTrue(table.constraints().stream().anyMatch(c -> c.name().equals("uq_accounts_plan_code")
                && c.type() == SchemaModel.ConstraintType.UNIQUE));

        assertEquals(1, table.foreignKeys().size());
        assertEquals("fk_accounts_tenant", table.foreignKeys().get(0).name());
        assertEquals("app", table.foreignKeys().get(0).referencedSchema());
        assertEquals("tenants", table.foreignKeys().get(0).referencedTable());

        assertEquals(3, table.indexes().size());
        assertTrue(table.indexes().stream().anyMatch(i -> i.name().equals("uq_accounts_plan_code") && i.unique()));
        assertTrue(table.indexes().stream().anyMatch(i -> i.name().equals("idx_accounts_created_at") && !i.unique()));
        // MySQL names the implicitly created FK index after the constraint (what JDBC reports).
        assertTrue(table.indexes().stream().anyMatch(i -> i.name().equals("fk_accounts_tenant") && !i.unique()
                && i.columns().equals(List.of("tenant_id"))));
    }

    @Test
    void doesNotAddImplicitMysqlFkIndexWhenCoveredByExistingPrefixIndex() {
        String ddl = """
                CREATE TABLE account_tenants (
                  id int NOT NULL PRIMARY KEY,
                  tenant_id int NOT NULL,
                  account_id int NOT NULL,
                  UNIQUE KEY uq_tenant_account (tenant_id, account_id),
                  CONSTRAINT fk_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
                );
                """;

        SchemaModel model = new DdlSchemaParser().parse(ddl, Dialect.MYSQL, "app");
        var table = model.tables().getFirst();

        assertEquals(1, table.foreignKeys().size());
        assertEquals(1, table.indexes().size());
        assertEquals("uq_tenant_account", table.indexes().getFirst().name());
    }

    @Test
    void detectsPostgresSerialAndIdentityAsAutoIncrement() {
        String ddl = """
                CREATE TABLE public.audit_events (
                  id bigserial PRIMARY KEY,
                  ordinal int GENERATED BY DEFAULT AS IDENTITY,
                  label text
                );
                """;

        SchemaModel model = new DdlSchemaParser().parse(ddl, Dialect.POSTGRESQL, "public");
        var columns = model.tables().getFirst().columns();

        assertTrue(columns.stream().filter(c -> c.name().equals("id")).findFirst().orElseThrow().autoIncrement());
        assertTrue(columns.stream().filter(c -> c.name().equals("ordinal")).findFirst().orElseThrow().autoIncrement());
        assertFalse(columns.stream().filter(c -> c.name().equals("label")).findFirst().orElseThrow().autoIncrement());
    }

    @Test
    void detectsMysqlAutoIncrementColumns() {
        String ddl = """
                CREATE TABLE app_events (
                  id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
                  label varchar(64)
                );
                """;

        SchemaModel model = new DdlSchemaParser().parse(ddl, Dialect.MYSQL, "app");
        var columns = model.tables().getFirst().columns();

        assertTrue(columns.stream().filter(c -> c.name().equals("id")).findFirst().orElseThrow().autoIncrement());
        assertFalse(columns.stream().filter(c -> c.name().equals("label")).findFirst().orElseThrow().autoIncrement());
    }

    @Test
    void keepsDotsInsideQuotedIdentifiersAsUnqualifiedNames() {
        String ddl = """
                CREATE TABLE "audit.logs" (
                  id int PRIMARY KEY,
                  tenant_id int,
                  CONSTRAINT fk_tenant FOREIGN KEY (tenant_id) REFERENCES "tenant.refs"(id)
                );
                """;

        SchemaModel model = new DdlSchemaParser().parse(ddl, Dialect.POSTGRESQL, "public");
        var table = model.tables().getFirst();

        assertEquals("public", table.schema());
        assertEquals("audit.logs", table.name());
        assertEquals(1, table.foreignKeys().size());
        assertEquals("public", table.foreignKeys().getFirst().referencedSchema());
        assertEquals("tenant.refs", table.foreignKeys().getFirst().referencedTable());
    }

    @Test
    void parsesIfNotExistsTablesAndStandaloneIndexesWithoutNames() {
        String ddl = """
                CREATE TABLE IF NOT EXISTS public.owners (
                  id int4 NOT NULL,
                  email varchar(255),
                  last_name varchar(255)
                );

                CREATE INDEX ON public.owners (last_name);
                CREATE UNIQUE INDEX IF NOT EXISTS idx_owners_email ON public.owners (email);
                """;

        SchemaModel model = new DdlSchemaParser().parse(ddl, Dialect.POSTGRESQL, "public");
        var table = model.tables().getFirst();

        assertEquals("owners", table.name());
        assertEquals(2, table.indexes().size());
        assertTrue(table.indexes().stream().anyMatch(i -> i.name().startsWith("idx_owners_last_name_")
                && !i.unique()
                && i.columns().equals(List.of("last_name"))));
        assertTrue(table.indexes().stream().anyMatch(i -> i.name().equals("idx_owners_email")
                && i.unique()
                && i.columns().equals(List.of("email"))));
    }

    @Test
    void parsesCurrentPetClinicUpstreamPostgresSchemaShapeDirectly() {
        String ddl = """
                CREATE TABLE IF NOT EXISTS vets (
                  id         INT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                  first_name TEXT,
                  last_name  TEXT
                );
                CREATE INDEX ON vets (last_name);

                CREATE TABLE IF NOT EXISTS specialties (
                  id   INT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                  name TEXT
                );
                CREATE INDEX ON specialties (name);

                CREATE TABLE IF NOT EXISTS vet_specialties (
                  vet_id       INT NOT NULL REFERENCES vets (id),
                  specialty_id INT NOT NULL REFERENCES specialties (id),
                  UNIQUE (vet_id, specialty_id)
                );

                CREATE TABLE IF NOT EXISTS types (
                  id   INT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                  name TEXT
                );
                CREATE INDEX ON types (name);

                CREATE TABLE IF NOT EXISTS owners (
                  id         INT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                  first_name TEXT,
                  last_name  TEXT,
                  address    TEXT,
                  city       TEXT,
                  telephone  TEXT
                );
                CREATE INDEX ON owners (last_name);

                CREATE TABLE IF NOT EXISTS pets (
                  id         INT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                  name       TEXT,
                  birth_date DATE,
                  type_id    INT NOT NULL REFERENCES types (id),
                  owner_id   INT REFERENCES owners (id)
                );
                CREATE INDEX ON pets (name);
                CREATE INDEX ON pets (owner_id);

                CREATE TABLE IF NOT EXISTS visits (
                  id          INT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                  pet_id      INT REFERENCES pets (id),
                  visit_date  DATE,
                  description TEXT
                );
                CREATE INDEX ON visits (pet_id);
                """;

        SchemaModel model = new DdlSchemaParser().parse(ddl, Dialect.POSTGRESQL, "public");

        assertEquals(7, model.tables().size());
        assertTrue(model.tables().stream().anyMatch(t -> t.name().equals("owners")
                && t.columns().size() == 6
                && t.indexes().stream().anyMatch(i -> i.columns().equals(List.of("last_name")))));
        assertTrue(model.tables().stream().anyMatch(t -> t.name().equals("pets")
                && t.foreignKeys().size() == 2
                && t.indexes().stream().anyMatch(i -> i.columns().equals(List.of("owner_id")))));
        assertTrue(model.tables().stream().anyMatch(t -> t.name().equals("vet_specialties")
                && t.constraints().stream().anyMatch(c -> c.type() == SchemaModel.ConstraintType.UNIQUE
                && c.columns().equals(List.of("vet_id", "specialty_id")))));
    }
}
