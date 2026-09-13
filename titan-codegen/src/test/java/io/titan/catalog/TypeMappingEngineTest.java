package io.titan.catalog;

import io.titan.introspect.SchemaModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TypeMappingEngineTest {

    private final TypeMappingEngine engine = new TypeMappingEngine();

    @Test
    void mapsPostgresAndMysqlCoreTypesToJavaAndDslTypes() {
        assertMapping("integer", "int4", false, "int", "Integer", "INTEGER");
        assertMapping("bigint", "int8", true, "Long", "Long", "BIGINT");
        assertMapping("tinyint", "tinyint", false, "boolean", "Boolean", "BOOLEAN");
        assertMapping("numeric", "numeric", true, "java.math.BigDecimal", "java.math.BigDecimal", "NUMERIC");
        assertMapping("timestamp", "datetime", true, "java.time.LocalDateTime", "java.time.LocalDateTime", "TIMESTAMP");
        assertMapping("date", "date", true, "java.time.LocalDate", "java.time.LocalDate", "DATE");
        assertMapping("varchar", "varchar", true, "String", "String", "VARCHAR");
    }

    @Test
    void mapsExtendedTypesUsingSqlTypeAndDbTypeHints() {
        assertMapping("real", "real", false, "float", "Float", "NUMERIC");
        assertMapping("double", "double precision", false, "double", "Double", "NUMERIC");
        assertMapping("timestamptz", "timestamp with time zone", true, "java.time.OffsetDateTime", "java.time.OffsetDateTime", "TIMESTAMP");
        assertMapping("unknown", "bigint unsigned", false, "long", "Long", "INTEGER");
        assertMapping("enum", "enum('PENDING','DONE')", true, "String", "String", "VARCHAR");
    }

    @Test
    void mapsUuidToTypeSafeUuidOnPostgresButCharColumnsStayString() {
        // PostgreSQL native uuid -> type-safe java.util.UUID + SQLType.UUID (no primitive form).
        assertMapping("uuid", "uuid", false, "java.util.UUID", "java.util.UUID", "UUID");
        assertMapping("uuid", "uuid", true, "java.util.UUID", "java.util.UUID", "UUID");
        // MySQL stores UUIDs as plain char(36): semantics aren't inferable, so it correctly stays String.
        assertMapping("char", "char(36)", true, "String", "String", "VARCHAR");
    }

    private void assertMapping(
            String sqlType,
            String dbTypeName,
            boolean nullable,
            String recordType,
            String descriptorType,
            String sqlTypeConstant
    ) {
        var column = new SchemaModel.ColumnMeta("c", sqlType, nullable, null, null, null, dbTypeName, null, java.util.List.of());

        assertEquals(recordType, engine.javaTypeForRecord(column, !nullable));
        assertEquals(descriptorType, engine.javaTypeForDescriptor(column));
        assertEquals(sqlTypeConstant, engine.sqlTypeConstant(column));
    }
}
