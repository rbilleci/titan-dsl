package io.titan.catalog;

import io.titan.introspect.SchemaModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnumTypeGeneratorTest {

    @Test
    void generatesEnumsFromSchemaEnumTypes() {
        var enumType = new SchemaModel.EnumTypeMeta(
                "public",
                "account_status",
                List.of("active", "pending_approval", "suspended")
        );

        var schema = new SchemaModel(List.of(), List.of(), List.of(enumType));
        var generated = new EnumTypeGenerator().generate(schema, "com.example.generated.titan");

        assertEquals(1, generated.size());
        String source = generated.get("com/example/generated/titan/public_/enums/AccountStatus.java");

        assertTrue(source.contains("@Generated(\"titan-generator\")"));
        assertTrue(source.contains("public enum AccountStatus"));
        assertTrue(source.contains("ACTIVE"));
        assertTrue(source.contains("PENDING_APPROVAL"));
        assertTrue(source.contains("SUSPENDED"));
    }

    @Test
    void mapsNonIdentifierEnumValuesToSafeJavaConstants() {
        var enumType = new SchemaModel.EnumTypeMeta(
                "public",
                "special_values",
                List.of("in-progress", "24/7", "***")
        );

        var schema = new SchemaModel(List.of(), List.of(), List.of(enumType));
        String source = new EnumTypeGenerator()
                .generate(schema, "com.example.generated.titan")
                .get("com/example/generated/titan/public_/enums/SpecialValues.java");

        assertTrue(source.contains("IN_PROGRESS"));
        assertTrue(source.contains("_24_7"));
        assertTrue(source.contains("VALUE"));
    }
}
