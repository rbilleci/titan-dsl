package io.titan.introspect;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Structured reader for the {@code schema.json} document emitted by {@link SchemaJsonWriter}.
 *
 * <p>This is the single supported way to consume {@code schema.json}; ad-hoc line or regex
 * parsing of the document is format-coupled and breaks silently when the writer evolves
 * (audit finding S9). The reader is tolerant of unknown object keys so the format can gain
 * fields without breaking older consumers, but it is strict about JSON well-formedness.</p>
 */
public final class SchemaJsonReader {

    public SchemaModel read(Path schemaJsonFile) throws IOException {
        return read(Files.readString(schemaJsonFile, StandardCharsets.UTF_8));
    }

    public SchemaModel read(String schemaJson) {
        Object root = new JsonParser(schemaJson).parseDocument();
        Map<String, Object> document = asObject(root, "schema document");

        List<SchemaModel.TableMeta> tables = new ArrayList<>();
        for (Object entry : asArray(document.getOrDefault("tables", List.of()), "tables")) {
            tables.add(readTable(asObject(entry, "table")));
        }

        List<SchemaModel.ViewMeta> views = new ArrayList<>();
        for (Object entry : asArray(document.getOrDefault("views", List.of()), "views")) {
            views.add(readView(asObject(entry, "view")));
        }

        List<SchemaModel.EnumTypeMeta> enumTypes = new ArrayList<>();
        for (Object entry : asArray(document.getOrDefault("enumTypes", List.of()), "enumTypes")) {
            enumTypes.add(readEnumType(asObject(entry, "enumType")));
        }

        return new SchemaModel(List.copyOf(tables), List.copyOf(views), List.copyOf(enumTypes));
    }

    private static SchemaModel.TableMeta readTable(Map<String, Object> table) {
        return new SchemaModel.TableMeta(
                requiredString(table, "schema", "table"),
                requiredString(table, "name", "table"),
                readColumns(table),
                readConstraints(table),
                readForeignKeys(table),
                readIndexes(table)
        );
    }

    private static SchemaModel.ViewMeta readView(Map<String, Object> view) {
        return new SchemaModel.ViewMeta(
                requiredString(view, "schema", "view"),
                requiredString(view, "name", "view"),
                readColumns(view)
        );
    }

    private static SchemaModel.EnumTypeMeta readEnumType(Map<String, Object> enumType) {
        return new SchemaModel.EnumTypeMeta(
                optionalString(enumType, "schema"),
                requiredString(enumType, "name", "enumType"),
                readStringArray(enumType.getOrDefault("values", List.of()), "enumType.values")
        );
    }

    private static List<SchemaModel.ColumnMeta> readColumns(Map<String, Object> owner) {
        List<SchemaModel.ColumnMeta> columns = new ArrayList<>();
        for (Object entry : asArray(owner.getOrDefault("columns", List.of()), "columns")) {
            Map<String, Object> column = asObject(entry, "column");
            columns.add(new SchemaModel.ColumnMeta(
                    requiredString(column, "name", "column"),
                    requiredString(column, "sqlType", "column"),
                    asBoolean(column.getOrDefault("nullable", Boolean.TRUE), "column.nullable"),
                    optionalInteger(column, "precision"),
                    optionalInteger(column, "scale"),
                    optionalInteger(column, "length"),
                    optionalString(column, "dbTypeName"),
                    optionalString(column, "enumTypeName"),
                    readStringArray(column.getOrDefault("enumValues", List.of()), "column.enumValues"),
                    asBoolean(column.getOrDefault("autoIncrement", Boolean.FALSE), "column.autoIncrement")
            ));
        }
        return List.copyOf(columns);
    }

    private static List<SchemaModel.ConstraintMeta> readConstraints(Map<String, Object> table) {
        List<SchemaModel.ConstraintMeta> constraints = new ArrayList<>();
        for (Object entry : asArray(table.getOrDefault("constraints", List.of()), "constraints")) {
            Map<String, Object> constraint = asObject(entry, "constraint");
            constraints.add(new SchemaModel.ConstraintMeta(
                    requiredString(constraint, "name", "constraint"),
                    SchemaModel.ConstraintType.valueOf(requiredString(constraint, "type", "constraint")),
                    readStringArray(constraint.getOrDefault("columns", List.of()), "constraint.columns")
            ));
        }
        return List.copyOf(constraints);
    }

    private static List<SchemaModel.ForeignKeyMeta> readForeignKeys(Map<String, Object> table) {
        List<SchemaModel.ForeignKeyMeta> foreignKeys = new ArrayList<>();
        for (Object entry : asArray(table.getOrDefault("foreignKeys", List.of()), "foreignKeys")) {
            Map<String, Object> foreignKey = asObject(entry, "foreignKey");
            foreignKeys.add(new SchemaModel.ForeignKeyMeta(
                    requiredString(foreignKey, "name", "foreignKey"),
                    readStringArray(foreignKey.getOrDefault("columns", List.of()), "foreignKey.columns"),
                    requiredString(foreignKey, "referencedSchema", "foreignKey"),
                    requiredString(foreignKey, "referencedTable", "foreignKey"),
                    readStringArray(foreignKey.getOrDefault("referencedColumns", List.of()), "foreignKey.referencedColumns")
            ));
        }
        return List.copyOf(foreignKeys);
    }

    private static List<SchemaModel.IndexMeta> readIndexes(Map<String, Object> table) {
        List<SchemaModel.IndexMeta> indexes = new ArrayList<>();
        for (Object entry : asArray(table.getOrDefault("indexes", List.of()), "indexes")) {
            Map<String, Object> index = asObject(entry, "index");
            indexes.add(new SchemaModel.IndexMeta(
                    requiredString(index, "name", "index"),
                    asBoolean(index.getOrDefault("unique", Boolean.FALSE), "index.unique"),
                    readStringArray(index.getOrDefault("columns", List.of()), "index.columns")
            ));
        }
        return List.copyOf(indexes);
    }

    private static List<String> readStringArray(Object value, String context) {
        List<String> strings = new ArrayList<>();
        for (Object entry : asArray(value, context)) {
            if (!(entry instanceof String string)) {
                throw new SchemaJsonFormatException(context + " must contain only strings, found: " + describe(entry));
            }
            strings.add(string);
        }
        return List.copyOf(strings);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asObject(Object value, String context) {
        if (!(value instanceof Map)) {
            throw new SchemaJsonFormatException(context + " must be a JSON object, found: " + describe(value));
        }
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asArray(Object value, String context) {
        if (!(value instanceof List)) {
            throw new SchemaJsonFormatException(context + " must be a JSON array, found: " + describe(value));
        }
        return (List<Object>) value;
    }

    private static boolean asBoolean(Object value, String context) {
        if (!(value instanceof Boolean bool)) {
            throw new SchemaJsonFormatException(context + " must be a JSON boolean, found: " + describe(value));
        }
        return bool;
    }

    private static String requiredString(Map<String, Object> object, String key, String context) {
        Object value = object.get(key);
        if (!(value instanceof String string)) {
            throw new SchemaJsonFormatException(context + "." + key + " must be a JSON string, found: " + describe(value));
        }
        return string;
    }

    private static String optionalString(Map<String, Object> object, String key) {
        Object value = object.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String string)) {
            throw new SchemaJsonFormatException(key + " must be a JSON string or null, found: " + describe(value));
        }
        return string;
    }

    private static Integer optionalInteger(Map<String, Object> object, String key) {
        Object value = object.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Number number)) {
            throw new SchemaJsonFormatException(key + " must be a JSON number or null, found: " + describe(value));
        }
        return number.intValue();
    }

    private static String describe(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }

    /** Thrown when {@code schema.json} is not well-formed or violates the documented shape. */
    public static final class SchemaJsonFormatException extends RuntimeException {
        SchemaJsonFormatException(String message) {
            super(message);
        }
    }

    /**
     * Minimal recursive-descent JSON parser covering the subset emitted by
     * {@link SchemaJsonWriter}: objects, arrays, strings (with escapes), integers,
     * booleans, and null. It exists so the transpiler stack stays dependency-free.
     */
    private static final class JsonParser {
        private final String input;
        private int position;

        JsonParser(String input) {
            this.input = input;
        }

        Object parseDocument() {
            Object value = parseValue();
            skipWhitespace();
            if (position < input.length()) {
                throw error("unexpected trailing content");
            }
            return value;
        }

        private Object parseValue() {
            skipWhitespace();
            if (position >= input.length()) {
                throw error("unexpected end of input");
            }
            char c = input.charAt(position);
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't', 'f' -> parseBoolean();
                case 'n' -> parseNull();
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> object = new java.util.LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') {
                position++;
                return object;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                object.put(key, parseValue());
                skipWhitespace();
                char c = next();
                if (c == '}') {
                    return object;
                }
                if (c != ',') {
                    throw error("expected ',' or '}' in object");
                }
            }
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> array = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') {
                position++;
                return array;
            }
            while (true) {
                array.add(parseValue());
                skipWhitespace();
                char c = next();
                if (c == ']') {
                    return array;
                }
                if (c != ',') {
                    throw error("expected ',' or ']' in array");
                }
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (true) {
                if (position >= input.length()) {
                    throw error("unterminated string");
                }
                char c = input.charAt(position++);
                if (c == '"') {
                    return out.toString();
                }
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                if (position >= input.length()) {
                    throw error("unterminated escape sequence");
                }
                char escaped = input.charAt(position++);
                switch (escaped) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (position + 4 > input.length()) {
                            throw error("incomplete unicode escape");
                        }
                        out.append((char) Integer.parseInt(input.substring(position, position + 4), 16));
                        position += 4;
                    }
                    default -> throw error("unsupported escape '\\" + escaped + "'");
                }
            }
        }

        private Boolean parseBoolean() {
            if (input.startsWith("true", position)) {
                position += 4;
                return Boolean.TRUE;
            }
            if (input.startsWith("false", position)) {
                position += 5;
                return Boolean.FALSE;
            }
            throw error("invalid literal");
        }

        private Object parseNull() {
            if (input.startsWith("null", position)) {
                position += 4;
                return null;
            }
            throw error("invalid literal");
        }

        private Object parseNumber() {
            int start = position;
            if (peek() == '-') {
                position++;
            }
            while (position < input.length() && isNumberChar(input.charAt(position))) {
                position++;
            }
            String token = input.substring(start, position);
            if (token.isEmpty() || token.equals("-")) {
                throw error("invalid number");
            }
            try {
                if (token.indexOf('.') >= 0 || token.indexOf('e') >= 0 || token.indexOf('E') >= 0) {
                    return Double.parseDouble(token);
                }
                return Integer.parseInt(token);
            } catch (NumberFormatException e) {
                throw error("invalid number '" + token + "'");
            }
        }

        private static boolean isNumberChar(char c) {
            return (c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-';
        }

        private void skipWhitespace() {
            while (position < input.length() && Character.isWhitespace(input.charAt(position))) {
                position++;
            }
        }

        private char peek() {
            if (position >= input.length()) {
                throw error("unexpected end of input");
            }
            return input.charAt(position);
        }

        private char next() {
            if (position >= input.length()) {
                throw error("unexpected end of input");
            }
            return input.charAt(position++);
        }

        private void expect(char expected) {
            char c = next();
            if (c != expected) {
                throw error("expected '" + expected + "' but found '" + c + "'");
            }
        }

        private SchemaJsonFormatException error(String message) {
            return new SchemaJsonFormatException("Malformed schema.json at offset " + position + ": " + message);
        }
    }
}
