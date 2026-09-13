package io.titan.introspect;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SchemaChangeDetector {

    public Map<String, String> fingerprint(SchemaModel model) {
        Map<String, String> result = new LinkedHashMap<>();
        model.tables().stream()
                .sorted(Comparator.comparing(SchemaModel.TableMeta::schema).thenComparing(SchemaModel.TableMeta::name))
                .forEach(table -> table.columns().stream()
                        .sorted(Comparator.comparing(SchemaModel.ColumnMeta::name))
                        .forEach(column -> result.put(
                                table.schema() + "." + table.name() + "." + column.name(),
                                columnSignature(column))));
        return result;
    }

    public SchemaDiff diff(Map<String, String> previous, Map<String, String> current) {
        List<String> added = current.keySet().stream()
                .filter(key -> !previous.containsKey(key))
                .sorted()
                .toList();

        List<String> removed = previous.keySet().stream()
                .filter(key -> !current.containsKey(key))
                .sorted()
                .toList();

        List<ChangedColumn> changed = current.keySet().stream()
                .filter(previous::containsKey)
                .filter(key -> !current.get(key).equals(previous.get(key)))
                .sorted()
                .map(key -> new ChangedColumn(key, previous.get(key), current.get(key)))
                .toList();

        return new SchemaDiff(added, removed, changed);
    }

    public String checksum(Map<String, String> fingerprint) {
        String canonical = fingerprint.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "|" + e.getValue())
                .reduce(new StringBuilder(), (sb, line) -> sb.append(line).append('\n'), StringBuilder::append)
                .toString();
        return sha256Hex(canonical);
    }

    public List<String> serialize(Map<String, String> fingerprint) {
        return fingerprint.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "|" + e.getValue())
                .toList();
    }

    public Map<String, String> deserialize(List<String> lines) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            int split = line.indexOf('|');
            if (split <= 0 || split == line.length() - 1) {
                continue;
            }
            result.put(line.substring(0, split), line.substring(split + 1));
        }
        return result;
    }

    private static String columnSignature(SchemaModel.ColumnMeta column) {
        List<String> enumValues = new ArrayList<>(column.enumValues());
        enumValues.sort(String::compareTo);
        return String.join("~",
                nullToEmpty(column.sqlType()),
                Boolean.toString(column.nullable()),
                numberToString(column.precision()),
                numberToString(column.scale()),
                numberToString(column.length()),
                nullToEmpty(column.dbTypeName()),
                nullToEmpty(column.enumTypeName()),
                Boolean.toString(column.autoIncrement()),
                String.join(",", enumValues));
    }

    private static String numberToString(Integer value) {
        return value == null ? "" : Integer.toString(value);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                out.append(String.format("%02x", b));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public record SchemaDiff(
            List<String> addedColumns,
            List<String> removedColumns,
            List<ChangedColumn> changedColumns
    ) {
        public boolean hasChanges() {
            return !addedColumns.isEmpty() || !removedColumns.isEmpty() || !changedColumns.isEmpty();
        }
    }

    public record ChangedColumn(String columnPath, String previousSignature, String currentSignature) {}
}
