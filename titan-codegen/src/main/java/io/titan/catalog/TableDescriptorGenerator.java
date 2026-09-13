package io.titan.catalog;

import io.titan.introspect.SchemaModel;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Generates table descriptor source files from {@link SchemaModel}.
 *
 * <p>All name mangling flows through {@link CatalogNaming}; collisions are rejected up front by
 * {@link CatalogCollisionDetector} (audit G-8).</p>
 */
public final class TableDescriptorGenerator {

    private final TypeMappingEngine typeMappingEngine = new TypeMappingEngine();

    public Map<String, String> generate(SchemaModel schema, String targetPackage) {
        var output = new LinkedHashMap<String, String>();
        for (var table : schema.tables()) {
            String className = CatalogNaming.toClassName(table.name(), "UnnamedTable");
            String packageName = targetPackage + "." + CatalogNaming.schemaPackageSegment(table.schema()) + ".tables";
            String source = generateTableSource(targetPackage, packageName, className, table);
            String relativePath = packageName.replace('.', '/') + "/" + className + ".java";
            output.put(relativePath, source);
        }
        return output;
    }

    private String generateTableSource(
            String targetPackage,
            String packageName,
            String className,
            SchemaModel.TableMeta table
    ) {
        String rowType = className + "Record";
        String singletonName = className.toUpperCase(Locale.ROOT);

        String columns = table.columns().stream()
                .map(c -> "    @PhysicalColumn(\"" + c.name() + "\")\n"
                        + "    public final Column<" + typeMappingEngine.javaTypeForDescriptor(c) + "> "
                        + CatalogNaming.toConstantName(c.name())
                        + " = column(\"" + c.name() + "\", SQLType." + typeMappingEngine.sqlTypeConstant(c) + ", Nullability."
                        + (c.nullable() ? "NULLABLE" : "NOT_NULL") + ");")
                .collect(Collectors.joining("\n"));

        String constraints = buildConstraintFields(targetPackage, table);
        String imports = buildImports(targetPackage, table);

        return """
                package %s;

                import javax.annotation.processing.Generated;
                import titan.dsl.Column;
                import titan.dsl.ForeignKey;
                import titan.dsl.Nullability;
                import titan.dsl.PhysicalColumn;
                import titan.dsl.PhysicalTable;
                import titan.dsl.SQLType;
                import titan.dsl.Table;
                import titan.dsl.UniqueKey;
                %s
                @Generated(\"titan-generator\")
                @PhysicalTable(name = \"%s\", schema = \"%s\")
                public final class %s extends Table<%s> {

                    public static final %s %s = new %s();

                %s

                %s

                    private %s() {
                        super(\"%s\", \"%s\");
                    }
                }
                """.formatted(
                packageName,
                imports,
                table.name(),
                table.schema(),
                className,
                rowType,
                className,
                singletonName,
                className,
                columns,
                constraints,
                className,
                table.name(),
                table.schema()
        );
    }

    private String buildImports(String targetPackage, SchemaModel.TableMeta table) {
        Set<String> imports = new LinkedHashSet<>();
        String currentSchemaSegment = CatalogNaming.schemaPackageSegment(table.schema());
        imports.add("import " + targetPackage + "." + currentSchemaSegment + ".records."
                + CatalogNaming.toClassName(table.name(), "UnnamedTable") + "Record;");

        for (var foreignKey : table.foreignKeys()) {
            if (!isSameSchema(foreignKey, table) || isSelfReference(foreignKey, table)) {
                // Cross-schema references are emitted fully qualified (two schemas may contain
                // tables with the same class name, so simple-name imports could collide);
                // self-references need no import at all.
                continue;
            }
            imports.add("import " + targetPackage + "." + currentSchemaSegment + ".records."
                    + CatalogNaming.toClassName(foreignKey.referencedTable(), "UnnamedTable") + "Record;");
        }

        return imports.stream().sorted().collect(Collectors.joining("\n"));
    }

    private String buildConstraintFields(String targetPackage, SchemaModel.TableMeta table) {
        var lines = new StringBuilder();
        String rowType = CatalogNaming.toClassName(table.name(), "UnnamedTable") + "Record";

        for (var constraint : table.constraints()) {
            if (constraint.type() == SchemaModel.ConstraintType.PRIMARY_KEY) {
                lines.append("    public final UniqueKey<")
                        .append(rowType)
                        .append("> PK = primaryKey(")
                        .append(columnRefs(constraint.columns()))
                        .append(");\n");
            } else if (constraint.type() == SchemaModel.ConstraintType.UNIQUE) {
                lines.append("    public final UniqueKey<")
                        .append(rowType)
                        .append("> ")
                        .append(CatalogNaming.toConstantName(constraint.name()))
                        .append(" = uniqueKey(")
                        .append(columnRefs(constraint.columns()))
                        .append(");\n");
            }
        }

        for (var foreignKey : table.foreignKeys()) {
            String referencedClass = CatalogNaming.toClassName(foreignKey.referencedTable(), "UnnamedTable");
            String referencedRecordType;
            String referencedTableRef;
            String referencedColumnPrefix;

            if (isSelfReference(foreignKey, table)) {
                // GAP G-8: a self-referential FK must not read the singleton during its own
                // class initialization (the static field is still null mid-<clinit>); reference
                // the instance under construction directly instead.
                referencedRecordType = rowType;
                referencedTableRef = "this";
                referencedColumnPrefix = "";
            } else if (isSameSchema(foreignKey, table)) {
                referencedRecordType = referencedClass + "Record";
                referencedTableRef = referencedClass + "." + CatalogNaming.toConstantName(foreignKey.referencedTable());
                referencedColumnPrefix = referencedTableRef + ".";
            } else {
                // GAP G-8: cross-schema references are fully qualified; the previous code had a
                // dead branch that "qualified" with the same simple name, which breaks when two
                // schemas contain identically named tables.
                String referencedSchemaSegment = CatalogNaming.schemaPackageSegment(foreignKey.referencedSchema());
                String descriptorFqcn = targetPackage + "." + referencedSchemaSegment + ".tables." + referencedClass;
                referencedRecordType = targetPackage + "." + referencedSchemaSegment + ".records." + referencedClass + "Record";
                referencedTableRef = descriptorFqcn + "." + CatalogNaming.toConstantName(foreignKey.referencedTable());
                referencedColumnPrefix = referencedTableRef + ".";
            }

            String referencedColumns = foreignKey.referencedColumns().stream()
                    .map(column -> referencedColumnPrefix + CatalogNaming.toConstantName(column))
                    .collect(Collectors.joining(", "));

            lines.append("    public final ForeignKey<")
                    .append(rowType)
                    .append(", ")
                    .append(referencedRecordType).append("> ")
                    .append(CatalogNaming.toConstantName(foreignKey.name()))
                    .append(" = foreignKey(")
                    .append(columnRefs(foreignKey.columns()))
                    .append(", ")
                    .append(referencedTableRef)
                    .append(", ")
                    .append(referencedColumns)
                    .append(");\n");
        }

        if (lines.isEmpty()) {
            return "    // No key metadata available from introspection.";
        }
        return lines.toString().trim();
    }

    private static boolean isSameSchema(SchemaModel.ForeignKeyMeta foreignKey, SchemaModel.TableMeta table) {
        return Objects.equals(foreignKey.referencedSchema(), table.schema());
    }

    private static boolean isSelfReference(SchemaModel.ForeignKeyMeta foreignKey, SchemaModel.TableMeta table) {
        return isSameSchema(foreignKey, table) && Objects.equals(foreignKey.referencedTable(), table.name());
    }

    private String columnRefs(List<String> columns) {
        return columns.stream()
                .map(CatalogNaming::toConstantName)
                .collect(Collectors.joining(", "));
    }
}
