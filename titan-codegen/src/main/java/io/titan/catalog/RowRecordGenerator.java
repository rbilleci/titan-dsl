package io.titan.catalog;

import io.titan.introspect.SchemaModel;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Generates row record source files from {@link SchemaModel}.
 *
 * <p>Component names flow through {@link CatalogNaming#toJavaFieldName(String)}, which guards
 * Java keywords (a column named {@code class} becomes the component {@code class_}) — audit G-8.</p>
 */
public final class RowRecordGenerator {

    private final TypeMappingEngine typeMappingEngine = new TypeMappingEngine();

    public Map<String, String> generate(SchemaModel schema, String targetPackage) {
        var output = new LinkedHashMap<String, String>();
        for (var table : schema.tables()) {
            String tableClassName = CatalogNaming.toClassName(table.name(), "UnnamedTable");
            String recordClassName = tableClassName + "Record";
            String packageName = targetPackage + "." + CatalogNaming.schemaPackageSegment(table.schema()) + ".records";
            String source = generateRecordSource(packageName, recordClassName, table);
            String relativePath = packageName.replace('.', '/') + "/" + recordClassName + ".java";
            output.put(relativePath, source);
        }
        return output;
    }

    private String generateRecordSource(String packageName, String recordClassName, SchemaModel.TableMeta table) {
        boolean usesNullable = table.columns().stream().anyMatch(SchemaModel.ColumnMeta::nullable);
        Set<String> imports = new LinkedHashSet<>();
        if (usesNullable) {
            imports.add("org.jspecify.annotations.Nullable");
        }

        String components = table.columns().stream()
                .map(column -> componentDeclaration(column, imports))
                .collect(Collectors.joining(",\n        "));

        String importBlock = imports.isEmpty()
                ? ""
                : imports.stream().map(type -> "import " + type + ";\n").collect(Collectors.joining());

        return """
                package %s;

                import javax.annotation.processing.Generated;
                %s
                @Generated("titan-generator")
                public record %s(
                        %s
                ) {}
                """.formatted(
                packageName,
                importBlock,
                recordClassName,
                components
        );
    }

    private String componentDeclaration(SchemaModel.ColumnMeta column, Set<String> imports) {
        String type = simplifyTypeName(typeMappingEngine.javaTypeForRecord(column, !column.nullable()), imports);
        String nullablePrefix = column.nullable() ? "@Nullable " : "";
        return nullablePrefix + type + " " + CatalogNaming.toJavaFieldName(column.name());
    }

    private String simplifyTypeName(String typeName, Set<String> imports) {
        if (typeName == null || typeName.isBlank() || !typeName.contains(".")) {
            return typeName;
        }
        if (typeName.startsWith("java.lang.")) {
            return typeName.substring("java.lang.".length());
        }
        imports.add(typeName);
        return typeName.substring(typeName.lastIndexOf('.') + 1);
    }
}
