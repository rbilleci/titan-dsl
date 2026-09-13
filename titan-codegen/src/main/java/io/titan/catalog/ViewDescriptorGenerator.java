package io.titan.catalog;

import io.titan.introspect.SchemaModel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generates view descriptor source files from {@link SchemaModel}.
 *
 * <p>All name mangling flows through {@link CatalogNaming} (audit G-8).</p>
 */
public final class ViewDescriptorGenerator {

    private final TypeMappingEngine typeMappingEngine = new TypeMappingEngine();

    public Map<String, String> generate(SchemaModel schema, String targetPackage) {
        var output = new LinkedHashMap<String, String>();
        for (var view : schema.views()) {
            String className = CatalogNaming.toClassName(view.name(), "UnnamedView");
            String packageName = targetPackage + "." + CatalogNaming.schemaPackageSegment(view.schema()) + ".views";
            String source = generateViewSource(targetPackage, packageName, className, view);
            String relativePath = packageName.replace('.', '/') + "/" + className + ".java";
            output.put(relativePath, source);
        }
        return output;
    }

    private String generateViewSource(String targetPackage, String packageName, String className, SchemaModel.ViewMeta view) {
        String rowType = className + "Record";
        String singletonName = CatalogNaming.toConstantName(view.name());

        String columns = view.columns().stream()
                .map(c -> "    public final Column<" + typeMappingEngine.javaTypeForDescriptor(c) + "> "
                        + CatalogNaming.toConstantName(c.name())
                        + " = column(\"" + c.name() + "\", SQLType." + typeMappingEngine.sqlTypeConstant(c) + ", Nullability."
                        + (c.nullable() ? "NULLABLE" : "NOT_NULL") + ");")
                .collect(Collectors.joining("\n"));

        if (columns.isBlank()) {
            columns = "    // No columns discovered for view metadata.";
        }

        return """
                package %s;

                import javax.annotation.processing.Generated;
                import titan.dsl.Column;
                import titan.dsl.Nullability;
                import titan.dsl.SQLType;
                import titan.dsl.View;
                import %s.%s.records.%s;

                @Generated(\"titan-generator\")
                public final class %s extends View<%s> {

                    public static final %s %s = new %s();

                %s

                    private %s() {
                        super(\"%s\", \"%s\");
                    }
                }
                """.formatted(
                packageName,
                targetPackage,
                CatalogNaming.schemaPackageSegment(view.schema()),
                rowType,
                className,
                rowType,
                className,
                singletonName,
                className,
                columns,
                className,
                view.name(),
                view.schema()
        );
    }
}
