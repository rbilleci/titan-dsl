package io.titan.introspect;

import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Container-backed DDL introspection (audit G-6/G-7): applies DDL files to a scratch
 * Testcontainers database for the configured dialect and introspects the result through the
 * real JDBC {@link SchemaIntrospector}.
 *
 * <p>This makes DDL mode <em>delegate</em> to JDBC mode — the {@link SchemaModel} produced from
 * DDL files is equivalent to the one a live database would produce by construction, deleting the
 * regex parser's entire bug class (silently dropped statements, type truncation, missing
 * enums/sequence-backed defaults, untyped view columns, lost time zones and precision/scale).</p>
 *
 * <p>JDBC drivers are supplied by the caller through a {@link ConnectionFactory} so this class
 * needs no driver on its own classpath: the Gradle plugin resolves drivers from its
 * {@code titanJdbc} configuration, tests use {@link #driverManagerConnections()}.</p>
 */
public final class DdlContainerIntrospector {

    private static final String POSTGRES_IMAGE = "postgres:16";
    private static final String MYSQL_IMAGE = "mysql:8.4";
    private static final String SCRATCH_USER = "titan";
    private static final String SCRATCH_PASSWORD = "titan";
    private static final String SCRATCH_DATABASE = "titan";
    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(3);
    private static final Duration CONNECT_RETRY_INTERVAL = Duration.ofMillis(500);

    /** A DDL script plus a human-readable origin (file name) for error messages. */
    public record DdlSource(String name, String sql) {}

    /** A live connection whose close also releases whatever loaded the driver. */
    public interface ConnectionHandle extends AutoCloseable {
        Connection connection();
    }

    /**
     * Opens JDBC connections to the scratch database. Implementations decide where the driver
     * comes from; throwing anything other than a {@link SQLException} (directly or as a cause)
     * aborts immediately instead of being retried as "database not ready yet".
     */
    @FunctionalInterface
    public interface ConnectionFactory {
        ConnectionHandle open(String jdbcUrl, String username, String password) throws Exception;
    }

    /** Connection factory backed by {@link DriverManager} (driver must be on the classpath). */
    public static ConnectionFactory driverManagerConnections() {
        return (jdbcUrl, username, password) -> {
            Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
            return new ConnectionHandle() {
                @Override
                public Connection connection() {
                    return connection;
                }

                @Override
                public void close() throws SQLException {
                    connection.close();
                }
            };
        };
    }

    /** Probes Docker availability without throwing. */
    public static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    public static String dockerUnavailableMessage() {
        return "Container-backed DDL introspection (ddlMode 'container', the default for DDL sources) requires a "
                + "working Docker environment, but none was detected.\n"
                + "Either start Docker (or point DOCKER_HOST/Testcontainers at a reachable daemon), or opt into the "
                + "no-Docker fallback with:\n\n"
                + "    titan {\n"
                + "        database.ddlMode = 'parser'\n"
                + "    }\n\n"
                + "The fallback regex parser supports a reduced DDL subset (no ALTER TABLE, CREATE TYPE, "
                + "CREATE SEQUENCE, OR REPLACE, table options like ENGINE=..., untyped view columns) and fails "
                + "hard on anything it cannot represent instead of dropping it silently.";
    }

    public SchemaModel introspect(
            List<DdlSource> ddlSources,
            Dialect dialect,
            List<String> schemas,
            ConnectionFactory connections
    ) throws Exception {
        if (!dockerAvailable()) {
            throw new IllegalStateException(dockerUnavailableMessage());
        }
        List<String> effectiveSchemas = schemas.isEmpty() ? List.of(defaultSchema(dialect)) : schemas;

        try (GenericContainer<?> container = createContainer(dialect, effectiveSchemas)) {
            container.start();
            String jdbcUrl = jdbcUrl(dialect, container, effectiveSchemas);
            try (ConnectionHandle handle = openWithRetry(connections, jdbcUrl, dialect)) {
                Connection connection = handle.connection();
                prepareSchemas(connection, dialect, effectiveSchemas);
                applyDdl(connection, ddlSources);
                return new SchemaIntrospector().introspect(connection, dialect, effectiveSchemas);
            }
        }
    }

    private static String defaultSchema(Dialect dialect) {
        return dialect == Dialect.POSTGRESQL ? "public" : SCRATCH_DATABASE;
    }

    private static GenericContainer<?> createContainer(Dialect dialect, List<String> schemas) {
        return switch (dialect) {
            case POSTGRESQL -> new GenericContainer<>(POSTGRES_IMAGE)
                    .withEnv("POSTGRES_USER", SCRATCH_USER)
                    .withEnv("POSTGRES_PASSWORD", SCRATCH_PASSWORD)
                    .withEnv("POSTGRES_DB", SCRATCH_DATABASE)
                    .withExposedPorts(5432)
                    .withStartupTimeout(STARTUP_TIMEOUT);
            case MYSQL -> new GenericContainer<>(MYSQL_IMAGE)
                    // Connect as root so additional schemas (= databases in MySQL) can be created.
                    .withEnv("MYSQL_ROOT_PASSWORD", SCRATCH_PASSWORD)
                    .withEnv("MYSQL_DATABASE", schemas.get(0))
                    .withExposedPorts(3306)
                    .withStartupTimeout(STARTUP_TIMEOUT);
        };
    }

    private static String jdbcUrl(Dialect dialect, GenericContainer<?> container, List<String> schemas) {
        String host = container.getHost();
        return switch (dialect) {
            case POSTGRESQL -> "jdbc:postgresql://" + host + ":" + container.getMappedPort(5432) + "/" + SCRATCH_DATABASE;
            case MYSQL -> "jdbc:mysql://" + host + ":" + container.getMappedPort(3306) + "/" + schemas.get(0);
        };
    }

    private ConnectionHandle openWithRetry(ConnectionFactory connections, String jdbcUrl, Dialect dialect)
            throws Exception {
        String username = dialect == Dialect.MYSQL ? "root" : SCRATCH_USER;
        Instant deadline = Instant.now().plus(STARTUP_TIMEOUT);
        Exception lastFailure = null;
        while (Instant.now().isBefore(deadline)) {
            try {
                return connections.open(jdbcUrl, username, SCRATCH_PASSWORD);
            } catch (Exception e) {
                if (!causedBySqlException(e)) {
                    throw e; // e.g. no driver available — retrying cannot fix that.
                }
                lastFailure = e;
                Thread.sleep(CONNECT_RETRY_INTERVAL.toMillis());
            }
        }
        throw new IllegalStateException(
                "Scratch " + dialect + " container did not accept connections within " + STARTUP_TIMEOUT
                        + " (" + jdbcUrl + ")", lastFailure);
    }

    private static boolean causedBySqlException(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof SQLException) {
                return true;
            }
        }
        return false;
    }

    private static void prepareSchemas(Connection connection, Dialect dialect, List<String> schemas)
            throws SQLException {
        try (Statement statement = connection.createStatement()) {
            if (dialect == Dialect.POSTGRESQL) {
                for (String schema : schemas) {
                    statement.execute("CREATE SCHEMA IF NOT EXISTS " + quotePostgres(schema));
                }
                // Unqualified DDL lands in the first configured schema, mirroring the regex
                // parser's defaultSchema behavior.
                String searchPath = quotePostgres(schemas.get(0));
                if (!"public".equals(schemas.get(0))) {
                    searchPath += ", public";
                }
                statement.execute("SET search_path TO " + searchPath);
            } else {
                for (String schema : schemas) {
                    statement.execute("CREATE DATABASE IF NOT EXISTS " + quoteMysql(schema));
                }
                statement.execute("USE " + quoteMysql(schemas.get(0)));
            }
        }
    }

    private static void applyDdl(Connection connection, List<DdlSource> ddlSources) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (DdlSource source : ddlSources) {
                for (String sql : SqlStatementSplitter.split(source.sql())) {
                    try {
                        statement.execute(sql);
                    } catch (SQLException e) {
                        throw new SQLException(
                                "Applying DDL to the scratch database failed in " + source.name() + ":\n\n    "
                                        + sql.strip().replace("\n", "\n    ") + "\n\n"
                                        + e.getMessage(), e);
                    }
                }
            }
        }
    }

    private static String quotePostgres(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }

    private static String quoteMysql(String identifier) {
        return '`' + identifier.replace("`", "``") + '`';
    }
}
