package io.titan.catalog;

import io.titan.introspect.SchemaModel;

import java.util.Locale;
import java.util.Set;

/**
 * Central SQL-to-Java and SQL-to-DSL type mapping for catalog generation.
 */
public final class TypeMappingEngine {

    private static final Set<String> TEXT_TYPES = Set.of(
            "varchar", "char", "character", "text", "json", "jsonb", "xml"
    );

    public String javaTypeForDescriptor(SchemaModel.ColumnMeta column) {
        return javaType(column, false);
    }

    public String javaTypeForRecord(SchemaModel.ColumnMeta column, boolean preferPrimitive) {
        return javaType(column, preferPrimitive);
    }

    public String sqlTypeConstant(SchemaModel.ColumnMeta column) {
        String sqlType = normalized(column.sqlType());
        String dbTypeName = normalized(column.dbTypeName());

        return switch (sqlType) {
            case "smallint" -> "SMALLINT";
            case "integer", "int", "serial" -> "INTEGER";
            case "bigint", "bigserial" -> "BIGINT";
            case "tinyint", "boolean", "bool" -> "BOOLEAN";
            case "date" -> "DATE";
            case "time" -> "TIME";
            case "timestamp", "timestamptz", "datetime" -> "TIMESTAMP";
            case "numeric", "decimal", "real", "double", "float" -> "NUMERIC";
            case "uuid" -> "UUID";
            case "enum" -> "VARCHAR";
            default -> {
                if (dbTypeName.contains("int")) {
                    yield "INTEGER";
                }
                if (dbTypeName.contains("bool") || dbTypeName.startsWith("bit")) {
                    yield "BOOLEAN";
                }
                if (dbTypeName.contains("date")) {
                    yield "DATE";
                }
                if (dbTypeName.contains("time")) {
                    yield "TIMESTAMP";
                }
                if (dbTypeName.contains("decimal") || dbTypeName.contains("numeric") || dbTypeName.contains("double")
                        || dbTypeName.contains("float")) {
                    yield "NUMERIC";
                }
                yield "VARCHAR";
            }
        };
    }

    private String javaType(SchemaModel.ColumnMeta column, boolean preferPrimitive) {
        String sqlType = normalized(column.sqlType());
        String dbTypeName = normalized(column.dbTypeName());

        if ("enum".equals(sqlType) || dbTypeName.startsWith("enum(")) {
            return "String";
        }
        if (TEXT_TYPES.contains(sqlType)) {
            return "String";
        }

        return switch (sqlType) {
            case "smallint" -> preferPrimitive ? "short" : "Short";
            case "integer", "int", "serial" -> preferPrimitive ? "int" : "Integer";
            case "bigint", "bigserial" -> preferPrimitive ? "long" : "Long";
            case "tinyint", "boolean", "bool" -> preferPrimitive ? "boolean" : "Boolean";
            case "real", "float" -> preferPrimitive ? "float" : "Float";
            case "double", "double precision" -> preferPrimitive ? "double" : "Double";
            case "numeric", "decimal" -> "java.math.BigDecimal";
            case "date" -> "java.time.LocalDate";
            case "time" -> "java.time.LocalTime";
            case "timestamp", "datetime" -> "java.time.LocalDateTime";
            case "timestamptz" -> "java.time.OffsetDateTime";
            // PostgreSQL native uuid → type-safe java.util.UUID (no primitive form). MySQL UUIDs are
            // plain char(36) columns and correctly stay String — UUID semantics aren't inferable there.
            case "uuid" -> "java.util.UUID";
            default -> javaTypeFromDbTypeName(dbTypeName, preferPrimitive);
        };
    }

    private String javaTypeFromDbTypeName(String dbTypeName, boolean preferPrimitive) {
        if (dbTypeName.isBlank()) {
            return "String";
        }
        if (dbTypeName.contains("bigint")) {
            return preferPrimitive ? "long" : "Long";
        }
        if (dbTypeName.contains("smallint")) {
            return preferPrimitive ? "short" : "Short";
        }
        if (dbTypeName.contains("int")) {
            return preferPrimitive ? "int" : "Integer";
        }
        if (dbTypeName.contains("bool") || dbTypeName.startsWith("bit")) {
            return preferPrimitive ? "boolean" : "Boolean";
        }
        if (dbTypeName.contains("double")) {
            return preferPrimitive ? "double" : "Double";
        }
        if (dbTypeName.contains("float") || dbTypeName.contains("real")) {
            return preferPrimitive ? "float" : "Float";
        }
        if (dbTypeName.contains("decimal") || dbTypeName.contains("numeric")) {
            return "java.math.BigDecimal";
        }
        if (dbTypeName.contains("timestamp") || dbTypeName.contains("datetime")) {
            return "java.time.LocalDateTime";
        }
        if (dbTypeName.contains("date")) {
            return "java.time.LocalDate";
        }
        if (dbTypeName.contains("time")) {
            return "java.time.LocalTime";
        }
        return "String";
    }

    private String normalized(String type) {
        return type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
    }
}
