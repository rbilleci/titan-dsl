package io.titan.introspect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SchemaJsonReaderTest {

    private static SchemaModel sampleModel() {
        return new SchemaModel(
                List.of(
                        new SchemaModel.TableMeta("public", "accounts", List.of(
                                new SchemaModel.ColumnMeta("email", "varchar", false, null, null, 255),
                                new SchemaModel.ColumnMeta("id", "integer", false, 32, 0, null, null, null, List.of(), true),
                                new SchemaModel.ColumnMeta("status", "enum", false, null, null, null, null, "account_status", List.of("ACTIVE", "SUSPENDED"))
                        ), List.of(
                                new SchemaModel.ConstraintMeta("accounts_pkey", SchemaModel.ConstraintType.PRIMARY_KEY, List.of("id")),
                                new SchemaModel.ConstraintMeta("accounts_email_key", SchemaModel.ConstraintType.UNIQUE, List.of("email"))
                        ), List.of(
                                new SchemaModel.ForeignKeyMeta("fk_accounts_team", List.of("team_id"), "public", "teams", List.of("id"))
                        ), List.of(
                                new SchemaModel.IndexMeta("idx_accounts_email", true, List.of("email"))
                        ))
                ),
                List.of(
                        new SchemaModel.ViewMeta("public", "active_accounts", List.of(
                                new SchemaModel.ColumnMeta("email", "varchar", false, null, null, 255)
                        )),
                        new SchemaModel.ViewMeta("reporting", "Account_Summary", List.of())
                ),
                List.of(
                        new SchemaModel.EnumTypeMeta(null, "account_status", List.of("ACTIVE", "SUSPENDED")),
                        new SchemaModel.EnumTypeMeta("public", "team_kind", List.of("A", "B"))
                )
        );
    }

    @Test
    void roundTripsWriterOutputByteIdentically() {
        SchemaJsonWriter writer = new SchemaJsonWriter();
        String written = writer.write(sampleModel());

        SchemaModel reread = new SchemaJsonReader().read(written);

        assertEquals(written, writer.write(reread),
                "writer -> reader -> writer must be byte-identical so the reader is a faithful inverse");
    }

    @Test
    void readsStructuredModelFields() {
        SchemaModel model = new SchemaJsonReader().read(new SchemaJsonWriter().write(sampleModel()));

        assertEquals(1, model.tables().size());
        SchemaModel.TableMeta accounts = model.tables().get(0);
        assertEquals("public", accounts.schema());
        assertEquals("accounts", accounts.name());
        assertEquals(3, accounts.columns().size());
        assertEquals(List.of("email", "id", "status"),
                accounts.columns().stream().map(SchemaModel.ColumnMeta::name).toList());
        SchemaModel.ColumnMeta id = accounts.columns().get(1);
        assertEquals(32, id.precision());
        assertEquals(0, id.scale());
        assertNull(id.length());
        assertTrue(id.autoIncrement());
        SchemaModel.ColumnMeta status = accounts.columns().get(2);
        assertEquals("account_status", status.enumTypeName());
        assertEquals(List.of("ACTIVE", "SUSPENDED"), status.enumValues());

        assertEquals(2, accounts.constraints().size());
        assertEquals(SchemaModel.ConstraintType.UNIQUE, accounts.constraints().get(0).type());
        assertEquals("accounts_email_key", accounts.constraints().get(0).name());

        assertEquals(1, accounts.foreignKeys().size());
        SchemaModel.ForeignKeyMeta fk = accounts.foreignKeys().get(0);
        assertEquals("teams", fk.referencedTable());
        assertEquals(List.of("team_id"), fk.columns());
        assertEquals(List.of("id"), fk.referencedColumns());

        assertEquals(1, accounts.indexes().size());
        assertTrue(accounts.indexes().get(0).unique());

        assertEquals(2, model.views().size());
        assertEquals(List.of("active_accounts", "Account_Summary"),
                model.views().stream().map(SchemaModel.ViewMeta::name).toList());

        assertEquals(2, model.enumTypes().size());
        assertNull(model.enumTypes().get(0).schema());
        assertEquals("public", model.enumTypes().get(1).schema());
    }

    @Test
    void unescapesStringValues() {
        SchemaModel model = new SchemaModel(
                List.of(new SchemaModel.TableMeta("public", "weird\"name\\with\ttabs", List.of())),
                List.of(),
                List.of());

        SchemaModel reread = new SchemaJsonReader().read(new SchemaJsonWriter().write(model));

        assertEquals("weird\"name\\with\ttabs", reread.tables().get(0).name());
    }

    @Test
    void toleratesUnknownKeysForForwardCompatibility() {
        String json = """
                {
                  "formatVersion": 2,
                  "tables": [],
                  "views": [
                    {
                      "schema": "public",
                      "name": "v_orders",
                      "comment": "added by a future writer",
                      "columns": []
                    }
                  ],
                  "enumTypes": []
                }
                """;

        SchemaModel model = new SchemaJsonReader().read(json);

        assertEquals(1, model.views().size());
        assertEquals("v_orders", model.views().get(0).name());
    }

    @Test
    void rejectsMalformedJson() {
        assertThrows(SchemaJsonReader.SchemaJsonFormatException.class,
                () -> new SchemaJsonReader().read("{ \"tables\": [ { \"schema\": \"public\" "));
        assertThrows(SchemaJsonReader.SchemaJsonFormatException.class,
                () -> new SchemaJsonReader().read("{ \"tables\": \"not-an-array\" }"));
        assertThrows(SchemaJsonReader.SchemaJsonFormatException.class,
                () -> new SchemaJsonReader().read("{} trailing"));
    }
}
