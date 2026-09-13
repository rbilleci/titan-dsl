package io.titan.catalog;

import io.titan.introspect.SchemaModel;

import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generates Java enum source files from introspected schema enum metadata.
 *
 * <p>All class names flow through {@link CatalogNaming#toClassName(String, String)} (audit G-8):
 * MySQL enum types arrive with synthesized {@code <table>_<column>_enum} names from
 * introspection, and even a raw {@code enum('a','b')} read from an old schema.json sanitizes to
 * a valid class name instead of producing files like {@code Enum('a','b').java}.</p>
 */
public final class EnumTypeGenerator {

    public Map<String, String> generate(SchemaModel schema, String targetPackage) {
        var output = new LinkedHashMap<String, String>();
        for (var enumType : schema.enumTypes()) {
            String className = CatalogNaming.toClassName(enumType.name(), "UnnamedEnum");
            String packageName = targetPackage + "." + CatalogNaming.schemaPackageSegment(enumType.schema()) + ".enums";
            String source = generateEnumSource(packageName, className, enumType);
            String relativePath = packageName.replace('.', '/') + "/" + className + ".java";
            output.put(relativePath, source);
        }
        return output;
    }

    private String generateEnumSource(String packageName, String className, SchemaModel.EnumTypeMeta enumType) {
        String constants = enumType.values().stream()
                .map(this::toEnumConstant)
                .collect(Collectors.joining(",\n    "));

        if (constants.isBlank()) {
            constants = "UNKNOWN";
        }

        return """
                package %s;

                import javax.annotation.processing.Generated;

                @Generated(\"titan-generator\")
                public enum %s {
                    %s;
                }
                """.formatted(packageName, className, constants);
    }

    private String toEnumConstant(String rawValue) {
        String normalized = rawValue
                .replaceAll("[^a-zA-Z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "")
                .toUpperCase(Locale.ROOT);

        if (normalized.isBlank()) {
            normalized = "VALUE";
        }
        if (Character.isDigit(normalized.charAt(0))) {
            normalized = "_" + normalized;
        }

        return normalized;
    }
}
