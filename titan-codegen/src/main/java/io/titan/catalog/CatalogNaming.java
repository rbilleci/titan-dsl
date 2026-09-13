package io.titan.catalog;

import java.util.Locale;
import java.util.Set;

/**
 * Single home for Java-name mangling used by all catalog generators (audit G-8).
 *
 * <p>Previously each generator carried its own copy of the keyword set and its own subtly
 * different mangling rules; collisions and keyword hits produced uncompilable output. All class
 * names, field names, constant names and package segments now flow through this class, and
 * {@link CatalogCollisionDetector} uses the same functions to detect mangling collisions before
 * any file is written.</p>
 */
public final class CatalogNaming {

    /**
     * Java keywords plus the reserved literals ({@code true}, {@code false}, {@code null}) and
     * contextual keywords that cannot be used as identifiers.
     */
    public static final Set<String> JAVA_KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp", "super",
            "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void", "volatile", "while",
            "true", "false", "null", "var", "record", "yield", "sealed", "permits"
    );

    private CatalogNaming() {
    }

    /**
     * PascalCase class name from a database object name. Splits on any non-alphanumeric run,
     * so {@code user_account}, {@code user-account} and {@code enum('a','b')} all produce valid
     * Java class names. Returns {@code fallback} when nothing usable remains.
     */
    public static String toClassName(String objectName, String fallback) {
        String[] parts = objectName == null ? new String[0] : objectName.split("[^a-zA-Z0-9]+");
        var sb = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        if (sb.isEmpty()) {
            return fallback;
        }
        if (Character.isDigit(sb.charAt(0))) {
            sb.insert(0, '_');
        }
        return sb.toString();
    }

    /** UPPER_SNAKE constant name for descriptor fields (columns, keys, singletons). */
    public static String toConstantName(String name) {
        String constant = name.replaceAll("[^a-zA-Z0-9]+", "_")
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toUpperCase(Locale.ROOT);
        if (constant.isBlank()) {
            constant = "_";
        }
        if (Character.isDigit(constant.charAt(0))) {
            constant = "_" + constant;
        }
        return constant;
    }

    /**
     * camelCase record-component / field name. Java keywords and reserved literals get a
     * trailing underscore ({@code class} → {@code class_}) so columns named after keywords
     * still compile (audit G-8).
     */
    public static String toJavaFieldName(String columnName) {
        String[] parts = columnName == null ? new String[0] : columnName.split("[^a-zA-Z0-9]+");
        var sb = new StringBuilder();
        boolean first = true;
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (first) {
                sb.append(part.toLowerCase(Locale.ROOT));
                first = false;
            } else {
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    sb.append(part.substring(1).toLowerCase(Locale.ROOT));
                }
            }
        }
        if (sb.isEmpty()) {
            sb.append('_');
        }
        if (Character.isDigit(sb.charAt(0))) {
            sb.insert(0, '_');
        }
        String field = sb.toString();
        if (JAVA_KEYWORDS.contains(field)) {
            field = field + "_";
        }
        return field;
    }

    /** Package segment for a database schema name. */
    public static String schemaPackageSegment(String schemaName) {
        String normalized = schemaName == null ? "default" : schemaName
                .replaceAll("[^a-zA-Z0-9]+", "_")
                .toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            normalized = "default";
        }
        if (Character.isDigit(normalized.charAt(0))) {
            normalized = "_" + normalized;
        }
        if (JAVA_KEYWORDS.contains(normalized)) {
            normalized = normalized + "_";
        }
        return normalized;
    }
}
