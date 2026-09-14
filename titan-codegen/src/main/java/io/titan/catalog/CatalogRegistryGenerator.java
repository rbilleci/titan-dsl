package io.titan.catalog;

import io.titan.introspect.SchemaModel;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generates {@code <targetPackage>.Catalog}, a registry listing every generated table and view
 * singleton. Applications hand {@code Catalog.TABLES} to {@code FilterPolicy.builder(...)} so a
 * policy can be validated against the whole schema at startup.
 */
public final class CatalogRegistryGenerator {

    public Map<String, String> generate(SchemaModel schema, String targetPackage) {
        String tables = schema.tables().stream()
                .map(table -> {
                    String className = CatalogNaming.toClassName(table.name(), "UnnamedTable");
                    return targetPackage + "." + CatalogNaming.schemaPackageSegment(table.schema()) + ".tables."
                            + className + "." + className.toUpperCase(Locale.ROOT);
                })
                .collect(Collectors.joining(",\n            "));
        String views = schema.views().stream()
                .map(view -> targetPackage + "." + CatalogNaming.schemaPackageSegment(view.schema()) + ".views."
                        + CatalogNaming.toClassName(view.name(), "UnnamedView") + "."
                        + CatalogNaming.toConstantName(view.name()))
                .collect(Collectors.joining(",\n            "));

        String source = """
                package %s;

                import java.util.List;
                import javax.annotation.processing.Generated;
                import titan.dsl.Table;
                import titan.dsl.View;

                @Generated("titan-generator")
                public final class Catalog {

                    public static final List<Table<?>> TABLES = List.of(%s);

                    public static final List<View<?>> VIEWS = List.of(%s);

                    private Catalog() {
                    }
                }
                """.formatted(targetPackage, indent(tables), indent(views));

        return Map.of(targetPackage.replace('.', '/') + "/Catalog.java", source);
    }

    private static String indent(String elements) {
        return elements.isEmpty() ? "" : "\n            " + elements;
    }
}
