package io.titan.introspect;

import java.util.List;

public record SchemaModel(
        List<TableMeta> tables,
        List<ViewMeta> views,
        List<EnumTypeMeta> enumTypes
) {

    public SchemaModel(List<TableMeta> tables) {
        this(tables, List.of(), List.of());
    }

    public record TableMeta(
            String schema,
            String name,
            List<ColumnMeta> columns,
            List<ConstraintMeta> constraints,
            List<ForeignKeyMeta> foreignKeys,
            List<IndexMeta> indexes
    ) {
        public TableMeta(String schema, String name, List<ColumnMeta> columns) {
            this(schema, name, columns, List.of(), List.of(), List.of());
        }
    }

    public record ConstraintMeta(
            String name,
            ConstraintType type,
            List<String> columns
    ) {}

    public enum ConstraintType {
        PRIMARY_KEY,
        UNIQUE
    }

    public record ForeignKeyMeta(
            String name,
            List<String> columns,
            String referencedSchema,
            String referencedTable,
            List<String> referencedColumns
    ) {}

    public record IndexMeta(
            String name,
            boolean unique,
            List<String> columns
    ) {}

    public record ViewMeta(
            String schema,
            String name,
            List<ColumnMeta> columns
    ) {}

    public record EnumTypeMeta(
            String schema,
            String name,
            List<String> values
    ) {}

    public record ColumnMeta(
            String name,
            String sqlType,
            boolean nullable,
            Integer precision,
            Integer scale,
            Integer length,
            String dbTypeName,
            String enumTypeName,
            List<String> enumValues,
            boolean autoIncrement
    ) {
        public ColumnMeta(
                String name,
                String sqlType,
                boolean nullable,
                Integer precision,
                Integer scale,
                Integer length
        ) {
            this(name, sqlType, nullable, precision, scale, length, null, null, List.of(), false);
        }

        public ColumnMeta(
                String name,
                String sqlType,
                boolean nullable,
                Integer precision,
                Integer scale,
                Integer length,
                String dbTypeName,
                String enumTypeName,
                List<String> enumValues
        ) {
            this(name, sqlType, nullable, precision, scale, length, dbTypeName, enumTypeName, enumValues, false);
        }
    }
}
