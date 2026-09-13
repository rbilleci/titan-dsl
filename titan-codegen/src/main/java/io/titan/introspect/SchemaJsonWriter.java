package io.titan.introspect;

import java.util.Comparator;

public final class SchemaJsonWriter {

    public String write(SchemaModel schemaModel) {
        StringBuilder out = new StringBuilder();
        out.append("{\n");
        out.append("  \"tables\": [\n");

        var sortedTables = schemaModel.tables().stream()
                .sorted(Comparator.comparing(SchemaModel.TableMeta::schema)
                        .thenComparing(SchemaModel.TableMeta::name))
                .toList();

        for (int t = 0; t < sortedTables.size(); t++) {
            var table = sortedTables.get(t);
            out.append("    {\n");
            out.append("      \"schema\": \"").append(escape(table.schema())).append("\",\n");
            out.append("      \"name\": \"").append(escape(table.name())).append("\",\n");
            out.append("      \"columns\": [\n");

            var sortedColumns = table.columns().stream()
                    .sorted(Comparator.comparing(SchemaModel.ColumnMeta::name))
                    .toList();

            for (int c = 0; c < sortedColumns.size(); c++) {
                var col = sortedColumns.get(c);
                out.append("        {\n");
                out.append("          \"name\": \"").append(escape(col.name())).append("\",\n");
                out.append("          \"sqlType\": \"").append(escape(col.sqlType())).append("\",\n");
                out.append("          \"nullable\": ").append(col.nullable()).append(",\n");
                out.append("          \"precision\": ").append(numberOrNull(col.precision())).append(",\n");
                out.append("          \"scale\": ").append(numberOrNull(col.scale())).append(",\n");
                out.append("          \"length\": ").append(numberOrNull(col.length())).append(",\n");
                out.append("          \"autoIncrement\": ").append(col.autoIncrement()).append(",\n");
                out.append("          \"enumTypeName\": ").append(stringOrNull(col.enumTypeName())).append(",\n");
                out.append("          \"enumValues\": ").append(toJsonArray(col.enumValues())).append("\n");
                out.append("        }");
                if (c < sortedColumns.size() - 1) {
                    out.append(',');
                }
                out.append('\n');
            }

            out.append("      ],\n");
            out.append("      \"constraints\": [\n");
            var sortedConstraints = table.constraints().stream()
                    .sorted(Comparator.comparing(SchemaModel.ConstraintMeta::name))
                    .toList();
            for (int i = 0; i < sortedConstraints.size(); i++) {
                var constraint = sortedConstraints.get(i);
                out.append("        {\n");
                out.append("          \"name\": \"").append(escape(constraint.name())).append("\",\n");
                out.append("          \"type\": \"").append(constraint.type()).append("\",\n");
                out.append("          \"columns\": ").append(toJsonArray(constraint.columns())).append("\n");
                out.append("        }");
                if (i < sortedConstraints.size() - 1) {
                    out.append(',');
                }
                out.append('\n');
            }

            out.append("      ],\n");
            out.append("      \"foreignKeys\": [\n");
            var sortedForeignKeys = table.foreignKeys().stream()
                    .sorted(Comparator.comparing(SchemaModel.ForeignKeyMeta::name))
                    .toList();
            for (int i = 0; i < sortedForeignKeys.size(); i++) {
                var fk = sortedForeignKeys.get(i);
                out.append("        {\n");
                out.append("          \"name\": \"").append(escape(fk.name())).append("\",\n");
                out.append("          \"columns\": ").append(toJsonArray(fk.columns())).append(",\n");
                out.append("          \"referencedSchema\": \"").append(escape(fk.referencedSchema())).append("\",\n");
                out.append("          \"referencedTable\": \"").append(escape(fk.referencedTable())).append("\",\n");
                out.append("          \"referencedColumns\": ").append(toJsonArray(fk.referencedColumns())).append("\n");
                out.append("        }");
                if (i < sortedForeignKeys.size() - 1) {
                    out.append(',');
                }
                out.append('\n');
            }

            out.append("      ],\n");
            out.append("      \"indexes\": [\n");
            var sortedIndexes = table.indexes().stream()
                    .sorted(Comparator.comparing(SchemaModel.IndexMeta::name))
                    .toList();
            for (int i = 0; i < sortedIndexes.size(); i++) {
                var index = sortedIndexes.get(i);
                out.append("        {\n");
                out.append("          \"name\": \"").append(escape(index.name())).append("\",\n");
                out.append("          \"unique\": ").append(index.unique()).append(",\n");
                out.append("          \"columns\": ").append(toJsonArray(index.columns())).append("\n");
                out.append("        }");
                if (i < sortedIndexes.size() - 1) {
                    out.append(',');
                }
                out.append('\n');
            }

            out.append("      ]\n");
            out.append("    }");
            if (t < sortedTables.size() - 1) {
                out.append(',');
            }
            out.append('\n');
        }

        out.append("  ],\n");
        out.append("  \"views\": [\n");

        var sortedViews = schemaModel.views().stream()
                .sorted(Comparator.comparing(SchemaModel.ViewMeta::schema)
                        .thenComparing(SchemaModel.ViewMeta::name))
                .toList();

        for (int v = 0; v < sortedViews.size(); v++) {
            var view = sortedViews.get(v);
            out.append("    {\n");
            out.append("      \"schema\": \"").append(escape(view.schema())).append("\",\n");
            out.append("      \"name\": \"").append(escape(view.name())).append("\",\n");
            out.append("      \"columns\": [\n");

            var sortedColumns = view.columns().stream()
                    .sorted(Comparator.comparing(SchemaModel.ColumnMeta::name))
                    .toList();

            for (int c = 0; c < sortedColumns.size(); c++) {
                var col = sortedColumns.get(c);
                out.append("        {\n");
                out.append("          \"name\": \"").append(escape(col.name())).append("\",\n");
                out.append("          \"sqlType\": \"").append(escape(col.sqlType())).append("\",\n");
                out.append("          \"nullable\": ").append(col.nullable()).append(",\n");
                out.append("          \"precision\": ").append(numberOrNull(col.precision())).append(",\n");
                out.append("          \"scale\": ").append(numberOrNull(col.scale())).append(",\n");
                out.append("          \"length\": ").append(numberOrNull(col.length())).append(",\n");
                out.append("          \"autoIncrement\": ").append(col.autoIncrement()).append(",\n");
                out.append("          \"enumTypeName\": ").append(stringOrNull(col.enumTypeName())).append(",\n");
                out.append("          \"enumValues\": ").append(toJsonArray(col.enumValues())).append("\n");
                out.append("        }");
                if (c < sortedColumns.size() - 1) {
                    out.append(',');
                }
                out.append('\n');
            }

            out.append("      ]\n");
            out.append("    }");
            if (v < sortedViews.size() - 1) {
                out.append(',');
            }
            out.append('\n');
        }

        out.append("  ],\n");
        out.append("  \"enumTypes\": [\n");

        var sortedEnumTypes = schemaModel.enumTypes().stream()
                .sorted(Comparator.comparing((SchemaModel.EnumTypeMeta e) -> stringOrEmpty(e.schema()))
                        .thenComparing(SchemaModel.EnumTypeMeta::name))
                .toList();

        for (int i = 0; i < sortedEnumTypes.size(); i++) {
            var enumType = sortedEnumTypes.get(i);
            out.append("    {\n");
            out.append("      \"schema\": ").append(stringOrNull(enumType.schema())).append(",\n");
            out.append("      \"name\": \"").append(escape(enumType.name())).append("\",\n");
            out.append("      \"values\": ").append(toJsonArray(enumType.values())).append("\n");
            out.append("    }");
            if (i < sortedEnumTypes.size() - 1) {
                out.append(',');
            }
            out.append('\n');
        }

        out.append("  ]\n");
        out.append("}\n");
        return out.toString();
    }

    private static String toJsonArray(Iterable<String> values) {
        StringBuilder out = new StringBuilder("[");
        boolean first = true;
        for (String value : values) {
            if (!first) {
                out.append(", ");
            }
            out.append('"').append(escape(value)).append('"');
            first = false;
        }
        out.append(']');
        return out.toString();
    }

    private static String numberOrNull(Integer number) {
        return number == null ? "null" : Integer.toString(number);
    }

    private static String stringOrNull(String value) {
        return value == null ? "null" : '"' + escape(value) + '"';
    }

    private static String stringOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
