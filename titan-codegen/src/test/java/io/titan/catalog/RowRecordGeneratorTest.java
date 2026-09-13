package io.titan.catalog;

import io.titan.introspect.SchemaModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RowRecordGeneratorTest {

    @Test
    void generatesRecordWithPrimitiveNotNullFieldsAndNullableAnnotations() {
        var table = new SchemaModel.TableMeta(
                "public",
                "accounts",
                List.of(
                        new SchemaModel.ColumnMeta("id", "integer", false, null, null, null),
                        new SchemaModel.ColumnMeta("active", "boolean", false, null, null, null),
                        new SchemaModel.ColumnMeta("email", "varchar", false, null, null, 255),
                        new SchemaModel.ColumnMeta("plan_id", "integer", true, null, null, null),
                        new SchemaModel.ColumnMeta("last_login", "date", true, null, null, null)
                ),
                List.of(),
                List.of(),
                List.of()
        );

        var schema = new SchemaModel(List.of(table), List.of(), List.of());
        var generated = new RowRecordGenerator().generate(schema, "com.example.generated.titan");

        assertEquals(1, generated.size());
        String source = generated.get("com/example/generated/titan/public_/records/AccountsRecord.java");

        assertTrue(source.contains("@Generated(\"titan-generator\")"));
        assertTrue(source.contains("public record AccountsRecord("));

        assertTrue(source.contains("int id"));
        assertTrue(source.contains("boolean active"));
        assertTrue(source.contains("String email"));

        assertTrue(source.contains("@Nullable Integer planId"));
        assertTrue(source.contains("import java.time.LocalDate;"));
        assertTrue(source.contains("@Nullable LocalDate lastLogin"));
        assertTrue(source.contains("import org.jspecify.annotations.Nullable;"));
    }

    @Test
    void omitsNullableImportWhenNotNeeded() {
        var table = new SchemaModel.TableMeta(
                "public",
                "plans",
                List.of(
                        new SchemaModel.ColumnMeta("id", "integer", false, null, null, null),
                        new SchemaModel.ColumnMeta("name", "varchar", false, null, null, 255)
                ),
                List.of(),
                List.of(),
                List.of()
        );

        var schema = new SchemaModel(List.of(table), List.of(), List.of());
        var generated = new RowRecordGenerator().generate(schema, "com.example.generated.titan");
        String source = generated.get("com/example/generated/titan/public_/records/PlansRecord.java");

        assertFalse(source.contains("import org.jspecify.annotations.Nullable;"));
    }
}
