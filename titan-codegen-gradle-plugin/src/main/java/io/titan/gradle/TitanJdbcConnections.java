package io.titan.gradle;

import org.gradle.api.GradleException;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * Opens JDBC connections for Titan's introspection tasks, resolving drivers from the
 * {@code titanJdbc} configuration instead of relying on the plugin's own classpath.
 *
 * <p>Drivers registered with {@link DriverManager} (for example when a build script adds a
 * driver to the buildscript classpath) are still honored, but the supported provisioning
 * channel is the {@code titanJdbc} configuration. When no driver accepts the configured URL
 * the failure is actionable: it names the configuration and suggests the coordinates for
 * well-known database prefixes rather than surfacing a raw "No suitable driver" error.</p>
 */
final class TitanJdbcConnections {

    private TitanJdbcConnections() {
    }

    /**
     * A live JDBC connection plus the classloader the driver was loaded from. Closing the
     * session closes both.
     */
    static final class JdbcSession implements AutoCloseable {
        private final Connection connection;
        private final URLClassLoader driverLoader;

        private JdbcSession(Connection connection, URLClassLoader driverLoader) {
            this.connection = connection;
            this.driverLoader = driverLoader;
        }

        Connection connection() {
            return connection;
        }

        @Override
        public void close() throws SQLException, IOException {
            try {
                connection.close();
            } finally {
                if (driverLoader != null) {
                    driverLoader.close();
                }
            }
        }
    }

    static JdbcSession open(Collection<File> driverClasspath, String jdbcUrl, String username, String password)
            throws SQLException {
        Properties properties = new Properties();
        if (username != null && !username.isEmpty()) {
            properties.setProperty("user", username);
        }
        if (password != null && !password.isEmpty()) {
            properties.setProperty("password", password);
        }

        URLClassLoader driverLoader = driverClassLoader(driverClasspath);
        try {
            for (Driver driver : candidateDrivers(driverLoader)) {
                if (!accepts(driver, jdbcUrl)) {
                    continue;
                }
                Connection connection = driver.connect(jdbcUrl, properties);
                if (connection != null) {
                    return new JdbcSession(connection, driverLoader);
                }
            }
        } catch (SQLException | RuntimeException e) {
            closeQuietly(driverLoader);
            throw e;
        }

        closeQuietly(driverLoader);
        throw new GradleException(missingDriverMessage(jdbcUrl, driverClasspath));
    }

    static String missingDriverMessage(String jdbcUrl, Collection<File> driverClasspath) {
        String redactedUrl = redactedJdbcUrl(jdbcUrl);
        StringBuilder message = new StringBuilder();
        message.append("No JDBC driver accepts URL '").append(redactedUrl).append("'.\n");
        if (driverClasspath == null || driverClasspath.isEmpty()) {
            message.append("The 'titanJdbc' configuration is empty. ");
        } else {
            message.append("None of the ").append(driverClasspath.size())
                    .append(" file(s) on the 'titanJdbc' configuration provide a driver for this URL. ");
        }
        message.append("Add the driver for your database to the 'titanJdbc' configuration, e.g.:\n\n")
                .append("    dependencies {\n")
                .append("        titanJdbc(\"").append(suggestedDriverCoordinates(jdbcUrl)).append("\")\n")
                .append("    }\n");
        return message.toString();
    }

    static String suggestedDriverCoordinates(String jdbcUrl) {
        String url = jdbcUrl == null ? "" : jdbcUrl.toLowerCase(Locale.ROOT);
        if (url.startsWith("jdbc:postgresql:")) {
            return "org.postgresql:postgresql:42.7.4";
        }
        if (url.startsWith("jdbc:mysql:")) {
            return "com.mysql:mysql-connector-j:8.4.0";
        }
        if (url.startsWith("jdbc:mariadb:")) {
            return "org.mariadb.jdbc:mariadb-java-client:3.4.1";
        }
        return "<groupId>:<artifactId>:<version>";
    }

    // GAP G-4: never surface raw JDBC URLs; drop query parameters and embedded userinfo so
    // credentials cannot leak into build logs or error messages.
    static String redactedJdbcUrl(String jdbcUrl) {
        return jdbcUrl.split("\\?", 2)[0].replaceFirst("//[^/@]+@", "//");
    }

    private static URLClassLoader driverClassLoader(Collection<File> driverClasspath) {
        if (driverClasspath == null || driverClasspath.isEmpty()) {
            return null;
        }
        List<URL> urls = new ArrayList<>(driverClasspath.size());
        for (File file : driverClasspath) {
            try {
                urls.add(file.toURI().toURL());
            } catch (MalformedURLException e) {
                throw new GradleException("Cannot use '" + file + "' from the 'titanJdbc' configuration as a driver classpath entry", e);
            }
        }
        return new URLClassLoader("titanJdbc", urls.toArray(new URL[0]), TitanJdbcConnections.class.getClassLoader());
    }

    private static List<Driver> candidateDrivers(URLClassLoader driverLoader) {
        List<Driver> drivers = new ArrayList<>();
        if (driverLoader != null) {
            // Load via ServiceLoader because DriverManager refuses drivers that are not
            // visible to the calling classloader; tolerate broken provider entries.
            ServiceLoader.load(Driver.class, driverLoader).stream().forEach(provider -> {
                try {
                    drivers.add(provider.get());
                } catch (ServiceConfigurationError | RuntimeException ignored) {
                    // A driver jar with an unloadable service entry must not fail provisioning
                    // of the remaining candidates.
                }
            });
        }
        Enumeration<Driver> registered = DriverManager.getDrivers();
        while (registered.hasMoreElements()) {
            drivers.add(registered.nextElement());
        }
        return drivers;
    }

    private static boolean accepts(Driver driver, String jdbcUrl) {
        try {
            return driver.acceptsURL(jdbcUrl);
        } catch (SQLException e) {
            return false;
        }
    }

    private static void closeQuietly(URLClassLoader loader) {
        if (loader == null) {
            return;
        }
        try {
            loader.close();
        } catch (IOException ignored) {
            // Nothing actionable: the loader leaks until GC, the build still fails with the root cause.
        }
    }
}
