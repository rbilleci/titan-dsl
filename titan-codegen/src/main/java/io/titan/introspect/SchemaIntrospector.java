package io.titan.introspect;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SchemaIntrospector {

    public SchemaModel introspect(Connection connection, Dialect dialect, List<String> schemas) throws SQLException {
        var enumTypes = readEnumTypes(connection, dialect, schemas);
        var tables = readObjects(connection, schemas, "BASE TABLE");
        var views = readObjects(connection, schemas, "VIEW");

        var tableMetas = new ArrayList<SchemaModel.TableMeta>();
        for (var table : tables) {
            var columns = readColumns(connection, dialect, table.schema(), table.name(), enumTypes);
            var constraints = readConstraints(connection, table.schema(), table.name());
            var foreignKeys = readForeignKeys(connection, dialect, table.schema(), table.name());
            var indexes = readIndexes(connection, dialect, table.schema(), table.name());
            tableMetas.add(new SchemaModel.TableMeta(table.schema(), table.name(), columns, constraints, foreignKeys, indexes));
        }

        var viewMetas = new ArrayList<SchemaModel.ViewMeta>();
        for (var view : views) {
            var columns = readColumns(connection, dialect, view.schema(), view.name(), enumTypes);
            viewMetas.add(new SchemaModel.ViewMeta(view.schema(), view.name(), columns));
        }

        return new SchemaModel(tableMetas, viewMetas, flattenEnums(enumTypes));
    }

    public String introspectAsJson(Connection connection, Dialect dialect, List<String> schemas) throws SQLException {
        return new SchemaJsonWriter().write(introspect(connection, dialect, schemas));
    }

    private List<TableName> readObjects(Connection connection, List<String> schemas, String tableType) throws SQLException {
        String placeholders = String.join(",", schemas.stream().map(s -> "?").toList());
        String sql = """
                SELECT table_schema, table_name
                FROM information_schema.tables
                WHERE table_schema IN (%s)
                  AND table_type = ?
                ORDER BY table_schema, table_name
                """.formatted(placeholders);

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int i = 1;
            for (var schema : schemas) {
                statement.setString(i++, schema);
            }
            statement.setString(i, tableType);

            try (ResultSet rs = statement.executeQuery()) {
                var out = new ArrayList<TableName>();
                while (rs.next()) {
                    out.add(new TableName(rs.getString("table_schema"), rs.getString("table_name")));
                }
                return out;
            }
        }
    }

    private List<SchemaModel.ColumnMeta> readColumns(
            Connection connection,
            Dialect dialect,
            String schema,
            String table,
            Map<String, List<String>> enumTypes
    ) throws SQLException {
        String sql = dialect == Dialect.POSTGRESQL
                ? """
                SELECT column_name, data_type, udt_name, is_nullable, numeric_precision, numeric_scale, character_maximum_length,
                       column_default, is_identity
                FROM information_schema.columns
                WHERE table_schema = ?
                  AND table_name = ?
                ORDER BY ordinal_position
                """
                : """
                SELECT column_name, data_type, is_nullable, numeric_precision, numeric_scale, character_maximum_length,
                       column_type, extra
                FROM information_schema.columns
                WHERE table_schema = ?
                  AND table_name = ?
                ORDER BY ordinal_position
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            statement.setString(2, table);
            try (ResultSet rs = statement.executeQuery()) {
                var out = new ArrayList<SchemaModel.ColumnMeta>();
                while (rs.next()) {
                    String dataType = rs.getString("data_type");
                    String normalized = normalizeType(dialect, dataType);
                    String dbTypeName = dataType;

                    String enumTypeName = null;
                    List<String> enumValues = List.of();

                    if (dialect == Dialect.POSTGRESQL) {
                        String udtName = rs.getString("udt_name");
                        String qualifiedUdtName = schema + "." + udtName;
                        if (udtName != null && (enumTypes.containsKey(qualifiedUdtName) || enumTypes.containsKey(udtName))) {
                            normalized = "enum";
                            enumTypeName = udtName;
                            enumValues = enumTypes.getOrDefault(qualifiedUdtName, enumTypes.get(udtName));
                        }
                    } else if (dialect == Dialect.MYSQL && "enum".equalsIgnoreCase(dataType)) {
                        normalized = "enum";
                        String columnType = rs.getString("column_type");
                        enumValues = parseMysqlEnumValues(columnType);
                        // GAP G-8: MySQL enums are anonymous column types; synthesize a stable
                        // per-column type name instead of leaking the raw "enum('a','b')" string
                        // into generated class names.
                        enumTypeName = mysqlEnumTypeName(table, rs.getString("column_name"));
                    }

                    boolean autoIncrement = isAutoIncrementColumn(rs, dialect, dataType);

                    out.add(new SchemaModel.ColumnMeta(
                            rs.getString("column_name"),
                            normalized,
                            "YES".equalsIgnoreCase(rs.getString("is_nullable")),
                            toInteger(rs, "numeric_precision"),
                            toInteger(rs, "numeric_scale"),
                            toInteger(rs, "character_maximum_length"),
                            dbTypeName,
                            enumTypeName,
                            enumValues,
                            autoIncrement
                    ));
                }
                return out;
            }
        }
    }

    private List<SchemaModel.ConstraintMeta> readConstraints(Connection connection, String schema, String table) throws SQLException {
        String sql = """
                SELECT tc.constraint_name, tc.constraint_type, kcu.column_name, kcu.ordinal_position
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_schema = kcu.constraint_schema
                 AND tc.constraint_name = kcu.constraint_name
                 AND tc.table_schema = kcu.table_schema
                 AND tc.table_name = kcu.table_name
                WHERE tc.table_schema = ?
                  AND tc.table_name = ?
                  AND tc.constraint_type IN ('PRIMARY KEY', 'UNIQUE')
                ORDER BY tc.constraint_name, kcu.ordinal_position
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            statement.setString(2, table);

            var grouped = new HashMap<String, ConstraintBuilder>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("constraint_name");
                    String typeRaw = rs.getString("constraint_type");
                    var type = "PRIMARY KEY".equalsIgnoreCase(typeRaw)
                            ? SchemaModel.ConstraintType.PRIMARY_KEY
                            : SchemaModel.ConstraintType.UNIQUE;

                    grouped.computeIfAbsent(name, n -> new ConstraintBuilder(type)).columns().add(rs.getString("column_name"));
                }
            }

            var out = new ArrayList<SchemaModel.ConstraintMeta>();
            grouped.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> out.add(new SchemaModel.ConstraintMeta(
                            entry.getKey(),
                            entry.getValue().type(),
                            List.copyOf(entry.getValue().columns())
                    )));
            return out;
        }
    }

    private List<SchemaModel.ForeignKeyMeta> readForeignKeys(Connection connection, Dialect dialect, String schema, String table) throws SQLException {
        String sql = dialect == Dialect.MYSQL
                ? """
                SELECT
                    constraint_name,
                    column_name,
                    ordinal_position,
                    referenced_table_schema,
                    referenced_table_name,
                    referenced_column_name
                FROM information_schema.key_column_usage
                WHERE table_schema = ?
                  AND table_name = ?
                  AND referenced_table_name IS NOT NULL
                ORDER BY constraint_name, ordinal_position
                """
                : """
                SELECT
                    con.conname AS constraint_name,
                    att.attname AS column_name,
                    cols.ord AS ordinal_position,
                    fns.nspname AS referenced_table_schema,
                    fcl.relname AS referenced_table_name,
                    fatt.attname AS referenced_column_name
                FROM pg_constraint con
                JOIN pg_class cl ON cl.oid = con.conrelid
                JOIN pg_namespace ns ON ns.oid = cl.relnamespace
                JOIN LATERAL unnest(con.conkey, con.confkey) WITH ORDINALITY AS cols(attnum, fattnum, ord) ON true
                JOIN pg_attribute att ON att.attrelid = con.conrelid AND att.attnum = cols.attnum
                JOIN pg_class fcl ON fcl.oid = con.confrelid
                JOIN pg_namespace fns ON fns.oid = fcl.relnamespace
                JOIN pg_attribute fatt ON fatt.attrelid = con.confrelid AND fatt.attnum = cols.fattnum
                WHERE con.contype = 'f'
                  AND ns.nspname = ?
                  AND cl.relname = ?
                ORDER BY con.conname, cols.ord
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            statement.setString(2, table);

            var grouped = new HashMap<String, ForeignKeyBuilder>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("constraint_name");
                    String referencedSchema = rs.getString("referenced_table_schema");
                    String referencedTable = rs.getString("referenced_table_name");
                    var builder = grouped.computeIfAbsent(name, ignored -> new ForeignKeyBuilder(
                            referencedSchema,
                            referencedTable
                    ));
                    builder.columns().add(rs.getString("column_name"));
                    builder.referencedColumns().add(rs.getString("referenced_column_name"));
                }
            }

            var out = new ArrayList<SchemaModel.ForeignKeyMeta>();
            grouped.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> out.add(new SchemaModel.ForeignKeyMeta(
                            entry.getKey(),
                            List.copyOf(entry.getValue().columns()),
                            entry.getValue().referencedSchema(),
                            entry.getValue().referencedTable(),
                            List.copyOf(entry.getValue().referencedColumns())
                    )));
            return out;
        }
    }

    private List<SchemaModel.IndexMeta> readIndexes(Connection connection, Dialect dialect, String schema, String table) throws SQLException {
        String sql = dialect == Dialect.MYSQL
                ? """
                SELECT index_name, non_unique, column_name, seq_in_index
                FROM information_schema.statistics
                WHERE table_schema = ?
                  AND table_name = ?
                  AND index_name <> 'PRIMARY'
                ORDER BY index_name, seq_in_index
                """
                : """
                SELECT i.relname AS index_name,
                       ix.indisunique AS is_unique,
                       a.attname AS column_name,
                       ord.ordinality AS seq_in_index
                FROM pg_class t
                JOIN pg_namespace ns ON ns.oid = t.relnamespace
                JOIN pg_index ix ON ix.indrelid = t.oid
                JOIN pg_class i ON i.oid = ix.indexrelid
                JOIN LATERAL unnest(ix.indkey) WITH ORDINALITY AS ord(attnum, ordinality) ON true
                JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = ord.attnum
                WHERE ns.nspname = ?
                  AND t.relname = ?
                  AND ix.indisprimary = false
                ORDER BY i.relname, ord.ordinality
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            statement.setString(2, table);

            var grouped = new HashMap<String, IndexBuilder>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String indexName = rs.getString("index_name");
                    boolean unique = dialect == Dialect.MYSQL
                            ? rs.getInt("non_unique") == 0
                            : rs.getBoolean("is_unique");
                    var builder = grouped.computeIfAbsent(indexName, ignored -> new IndexBuilder(unique));
                    builder.columns().add(rs.getString("column_name"));
                }
            }

            var out = new ArrayList<SchemaModel.IndexMeta>();
            grouped.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> out.add(new SchemaModel.IndexMeta(
                            entry.getKey(),
                            entry.getValue().unique(),
                            List.copyOf(entry.getValue().columns())
                    )));
            return out;
        }
    }

    private Map<String, List<String>> readEnumTypes(Connection connection, Dialect dialect, List<String> schemas) throws SQLException {
        return switch (dialect) {
            case POSTGRESQL -> readPostgresEnums(connection, schemas);
            case MYSQL -> readMysqlEnums(connection, schemas);
        };
    }

    private Map<String, List<String>> readPostgresEnums(Connection connection, List<String> schemas) throws SQLException {
        String placeholders = String.join(",", schemas.stream().map(s -> "?").toList());
        String sql = """
                SELECT n.nspname AS schema_name, t.typname AS type_name, e.enumlabel AS enum_label
                FROM pg_type t
                JOIN pg_enum e ON t.oid = e.enumtypid
                JOIN pg_namespace n ON n.oid = t.typnamespace
                WHERE n.nspname IN (%s)
                ORDER BY n.nspname, t.typname, e.enumsortorder
                """.formatted(placeholders);

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < schemas.size(); i++) {
                statement.setString(i + 1, schemas.get(i));
            }

            var out = new HashMap<String, List<String>>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString("schema_name") + "." + rs.getString("type_name");
                    out.computeIfAbsent(key, ignored -> new ArrayList<>())
                            .add(rs.getString("enum_label"));
                }
            }
            return out;
        }
    }

    private Map<String, List<String>> readMysqlEnums(Connection connection, List<String> schemas) throws SQLException {
        String placeholders = String.join(",", schemas.stream().map(s -> "?").toList());
        String sql = """
                SELECT table_schema, table_name, column_name, column_type
                FROM information_schema.columns
                WHERE table_schema IN (%s)
                  AND data_type = 'enum'
                ORDER BY table_schema, table_name, column_name
                """.formatted(placeholders);

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < schemas.size(); i++) {
                statement.setString(i + 1, schemas.get(i));
            }

            var out = new HashMap<String, List<String>>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    // GAP G-8: one synthesized enum type per enum column; the raw column type
                    // ("enum('a','b')") is not a usable type name.
                    String key = rs.getString("table_schema") + "::"
                            + mysqlEnumTypeName(rs.getString("table_name"), rs.getString("column_name"));
                    out.putIfAbsent(key, parseMysqlEnumValues(rs.getString("column_type")));
                }
            }
            return out;
        }
    }

    /** Synthesized type name for a MySQL enum column (G-8): {@code <table>_<column>_enum}. */
    static String mysqlEnumTypeName(String table, String column) {
        return table + "_" + column + "_enum";
    }

    private List<SchemaModel.EnumTypeMeta> flattenEnums(Map<String, List<String>> enums) {
        var out = new ArrayList<SchemaModel.EnumTypeMeta>();
        for (var entry : enums.entrySet()) {
            String key = entry.getKey();
            String schema = null;
            String name = key;

            if (key.contains("::")) {
                var parts = key.split("::", 2);
                schema = parts[0];
                name = parts[1];
            } else if (key.contains(".")) {
                var parts = key.split("\\.", 2);
                schema = parts[0];
                name = parts[1];
            }

            out.add(new SchemaModel.EnumTypeMeta(schema, name, List.copyOf(entry.getValue())));
        }
        out.sort((a, b) -> {
            int schemaCompare = stringOrEmpty(a.schema()).compareTo(stringOrEmpty(b.schema()));
            if (schemaCompare != 0) {
                return schemaCompare;
            }
            return a.name().compareTo(b.name());
        });
        return out;
    }

    private static String stringOrEmpty(String value) {
        return value == null ? "" : value;
    }

    static String normalizeType(Dialect dialect, String dataType) {
        if (dataType == null) {
            return "unknown";
        }

        String t = dataType.toLowerCase();
        return switch (dialect) {
            case POSTGRESQL -> switch (t) {
                case "int4", "integer" -> "integer";
                case "int8", "bigint" -> "bigint";
                case "int2", "smallint" -> "smallint";
                case "bool", "boolean" -> "boolean";
                case "varchar", "character varying" -> "varchar";
                case "timestamp without time zone" -> "timestamp";
                case "timestamp with time zone" -> "timestamptz";
                default -> t;
            };
            case MYSQL -> switch (t) {
                case "int" -> "integer";
                case "tinyint", "bit", "boolean", "bool" -> "tinyint";
                case "datetime" -> "timestamp";
                default -> t;
            };
        };
    }

    private static Integer toInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static boolean isAutoIncrementColumn(ResultSet rs, Dialect dialect, String dataType) throws SQLException {
        if (dialect == Dialect.POSTGRESQL) {
            String isIdentity = rs.getString("is_identity");
            if ("YES".equalsIgnoreCase(isIdentity)) {
                return true;
            }
            String columnDefault = rs.getString("column_default");
            return columnDefault != null && columnDefault.toLowerCase().startsWith("nextval(");
        }

        String extra = rs.getString("extra");
        if (extra != null && extra.toLowerCase().contains("auto_increment")) {
            return true;
        }

        String type = dataType == null ? "" : dataType.toLowerCase();
        return "serial".equals(type) || "bigserial".equals(type);
    }

    static List<String> parseMysqlEnumValues(String columnType) {
        if (columnType == null || !columnType.toLowerCase().startsWith("enum(")) {
            return List.of();
        }

        String inner = columnType.substring(5, columnType.length() - 1);
        var values = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == '\'' && (i == 0 || inner.charAt(i - 1) != '\\')) {
                inQuote = !inQuote;
                if (!inQuote) {
                    values.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            if (inQuote) {
                current.append(c);
            }
        }
        return values;
    }

    private record TableName(String schema, String name) {}
    private record ConstraintBuilder(SchemaModel.ConstraintType type, List<String> columns) {
        ConstraintBuilder(SchemaModel.ConstraintType type) {
            this(type, new ArrayList<>());
        }
    }

    private record ForeignKeyBuilder(
            String referencedSchema,
            String referencedTable,
            List<String> columns,
            List<String> referencedColumns
    ) {
        ForeignKeyBuilder(String referencedSchema, String referencedTable) {
            this(referencedSchema, referencedTable, new ArrayList<>(), new ArrayList<>());
        }
    }

    private record IndexBuilder(boolean unique, List<String> columns) {
        IndexBuilder(boolean unique) {
            this(unique, new ArrayList<>());
        }
    }
}
