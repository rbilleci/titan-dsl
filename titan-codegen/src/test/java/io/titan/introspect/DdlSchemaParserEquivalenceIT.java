package io.titan.introspect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import io.titan.test.TestContainers;

// Docker-dependent (Testcontainers); runs via the integrationTest task, excluded from plain test (plan 4.5).
@org.junit.jupiter.api.Tag("docker")
class DdlSchemaParserEquivalenceIT {

    @org.junit.jupiter.api.Test
    void hasAtLeastFiftyDdlFixturesAcrossDialects() throws Exception {
        long postgresCount;
        long mysqlCount;
        try (Stream<Path> pg = listFixtures("src/test/resources/ddl/postgresql");
             Stream<Path> my = listFixtures("src/test/resources/ddl/mysql")) {
            postgresCount = pg.count();
            mysqlCount = my.count();
        }

        assertTrue(postgresCount >= 25, "Expected at least 25 PostgreSQL fixtures");
        assertTrue(mysqlCount >= 25, "Expected at least 25 MySQL fixtures");
        assertTrue(postgresCount + mysqlCount >= 50, "Expected at least 50 total DDL fixtures");
    }

    // Private databases on the shared singleton containers: this IT resets whole schemas per
    // fixture, and its parse/introspect calls hardcode the MySQL database name "titan".
    static final TestContainers.SharedDatabase POSTGRES =
            TestContainers.freshPostgresDatabase("titan_ddl_pg");

    static final TestContainers.SharedDatabase MYSQL =
            TestContainers.freshMysqlDatabase("titan");

    @ParameterizedTest
    @MethodSource("postgresFixtures")
    void ddlParserMatchesPostgresJdbcForCoreMetadata(Path fixturePath) throws Exception {
        String ddl = Files.readString(fixturePath, StandardCharsets.UTF_8);

        SchemaModel parsed = new DdlSchemaParser().parse(ddl, Dialect.POSTGRESQL, "public");
        SchemaModel jdbc = introspectAgainstPostgres(ddl);

        assertEquivalent(parsed, jdbc);
    }

    @ParameterizedTest
    @MethodSource("mysqlFixtures")
    void ddlParserMatchesMysqlJdbcForCoreMetadata(Path fixturePath) throws Exception {
        String ddl = Files.readString(fixturePath, StandardCharsets.UTF_8);

        SchemaModel parsed = new DdlSchemaParser().parse(ddl, Dialect.MYSQL, "titan");
        SchemaModel jdbc = introspectAgainstMysql(ddl);

        assertEquivalent(parsed, jdbc);
    }

    private static Stream<Path> postgresFixtures() throws IOException {
        return listFixtures("src/test/resources/ddl/postgresql");
    }

    private static Stream<Path> mysqlFixtures() throws IOException {
        return listFixtures("src/test/resources/ddl/mysql");
    }

    private static Stream<Path> listFixtures(String baseDir) throws IOException {
        try (Stream<Path> paths = Files.list(Path.of(baseDir))) {
            return paths
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .sorted()
                    .toList()
                    .stream();
        }
    }

    private static SchemaModel introspectAgainstPostgres(String ddl) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.jdbcUrl(),
                POSTGRES.username(),
                POSTGRES.password())) {
            resetPostgresSchema(connection);
            executeStatements(connection, ddl);
            return new SchemaIntrospector().introspect(connection, Dialect.POSTGRESQL, List.of("public"));
        }
    }

    private static SchemaModel introspectAgainstMysql(String ddl) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                MYSQL.jdbcUrl(),
                MYSQL.username(),
                MYSQL.password())) {
            resetMysqlSchema(connection);
            executeStatements(connection, ddl);
            return new SchemaIntrospector().introspect(connection, Dialect.MYSQL, List.of("titan"));
        }
    }

    private static void resetPostgresSchema(Connection connection) throws Exception {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("DROP SCHEMA public CASCADE");
            stmt.execute("CREATE SCHEMA public");
        }
    }

    private static void resetMysqlSchema(Connection connection) throws Exception {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SET FOREIGN_KEY_CHECKS = 0");

            var drops = new java.util.ArrayList<String>();
            try (var rs = stmt.executeQuery("SELECT table_name, table_type FROM information_schema.tables WHERE table_schema = 'titan'")) {
                while (rs.next()) {
                    String name = rs.getString("table_name");
                    String type = rs.getString("table_type");
                    if ("VIEW".equalsIgnoreCase(type)) {
                        drops.add("DROP VIEW IF EXISTS `" + name + "`");
                    } else {
                        drops.add("DROP TABLE IF EXISTS `" + name + "`");
                    }
                }
            }

            for (String drop : drops) {
                stmt.execute(drop);
            }
            stmt.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }

    private static void executeStatements(Connection connection, String ddl) throws Exception {
        try (Statement stmt = connection.createStatement()) {
            for (String raw : ddl.split(";")) {
                String sql = raw.trim();
                if (!sql.isEmpty()) {
                    stmt.execute(sql);
                }
            }
        }
    }

    private static void assertEquivalent(SchemaModel parsed, SchemaModel jdbc) {
        var parsedTables = parsed.tables().stream()
                .map(DdlSchemaParserEquivalenceIT::canonicalTable)
                .sorted(Comparator.comparing(CanonicalTable::schema).thenComparing(CanonicalTable::name))
                .toList();
        var jdbcTables = jdbc.tables().stream()
                .map(DdlSchemaParserEquivalenceIT::canonicalTable)
                .sorted(Comparator.comparing(CanonicalTable::schema).thenComparing(CanonicalTable::name))
                .toList();

        var parsedViews = parsed.views().stream()
                .map(v -> new CanonicalView(v.schema(), v.name(),
                        v.columns().stream().map(SchemaModel.ColumnMeta::name).toList()))
                .sorted(Comparator.comparing(CanonicalView::schema).thenComparing(CanonicalView::name))
                .toList();
        var jdbcViews = jdbc.views().stream()
                .map(v -> new CanonicalView(v.schema(), v.name(),
                        v.columns().stream().map(SchemaModel.ColumnMeta::name).toList()))
                .sorted(Comparator.comparing(CanonicalView::schema).thenComparing(CanonicalView::name))
                .toList();

        assertEquals(parsedTables, jdbcTables, "Table metadata mismatch");
        assertEquals(parsedViews, jdbcViews, "View metadata mismatch");
    }

    private static CanonicalTable canonicalTable(SchemaModel.TableMeta table) {
        var columns = table.columns().stream()
                .map(c -> new CanonicalColumn(c.name(), c.sqlType(), c.nullable(), c.length(), c.autoIncrement()))
                .toList();
        var constraints = table.constraints().stream()
                .map(c -> new CanonicalConstraint(c.type().name(), c.columns()))
                .sorted(Comparator.comparing(CanonicalConstraint::type).thenComparing(c -> String.join(",", c.columns())))
                .toList();
        var foreignKeys = table.foreignKeys().stream()
                .map(fk -> new CanonicalForeignKey(
                        fk.columns(),
                        fk.referencedSchema(),
                        fk.referencedTable(),
                        fk.referencedColumns()))
                .sorted(Comparator.comparing((CanonicalForeignKey fk) -> String.join(",", fk.columns()))
                        .thenComparing(CanonicalForeignKey::referencedSchema)
                        .thenComparing(CanonicalForeignKey::referencedTable)
                        .thenComparing(fk -> String.join(",", fk.referencedColumns())))
                .toList();
        var indexes = table.indexes().stream()
                .map(i -> new CanonicalIndex(i.unique(), i.columns()))
                .sorted(Comparator.comparing(CanonicalIndex::unique).thenComparing(i -> String.join(",", i.columns())))
                .toList();

        return new CanonicalTable(table.schema(), table.name(), columns, constraints, foreignKeys, indexes);
    }

    private record CanonicalTable(
            String schema,
            String name,
            List<CanonicalColumn> columns,
            List<CanonicalConstraint> constraints,
            List<CanonicalForeignKey> foreignKeys,
            List<CanonicalIndex> indexes
    ) {}

    private record CanonicalColumn(String name, String sqlType, boolean nullable, Integer length, boolean autoIncrement) {}

    private record CanonicalConstraint(String type, List<String> columns) {}

    private record CanonicalForeignKey(
            List<String> columns,
            String referencedSchema,
            String referencedTable,
            List<String> referencedColumns
    ) {}

    private record CanonicalIndex(boolean unique, List<String> columns) {}

    private record CanonicalView(String schema, String name, List<String> columns) {}
}
