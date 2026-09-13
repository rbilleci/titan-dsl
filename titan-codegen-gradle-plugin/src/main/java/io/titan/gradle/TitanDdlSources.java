package io.titan.gradle;

import io.titan.introspect.DdlContainerIntrospector;
import org.gradle.api.file.ConfigurableFileCollection;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Shared DDL-source plumbing for {@link TitanIntrospectTask} and {@link TitanGenerateTask}:
 * deterministic file ordering and the bridge between {@link DdlContainerIntrospector}'s
 * connection factory and {@link TitanJdbcConnections} (drivers from the {@code titanJdbc}
 * configuration).
 */
final class TitanDdlSources {

    private TitanDdlSources() {
    }

    /** DDL files in deterministic (path-sorted) order as named sources for error messages. */
    static List<DdlContainerIntrospector.DdlSource> read(ConfigurableFileCollection ddlFiles) {
        var sources = new ArrayList<DdlContainerIntrospector.DdlSource>();
        for (File file : sortedFiles(ddlFiles)) {
            try {
                sources.add(new DdlContainerIntrospector.DdlSource(
                        file.getName(),
                        Files.readString(file.toPath(), StandardCharsets.UTF_8)));
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot read DDL file " + file, e);
            }
        }
        return sources;
    }

    /** All DDL files concatenated in deterministic order (single-parse for the fallback parser). */
    static String concatenate(ConfigurableFileCollection ddlFiles) {
        var sb = new StringBuilder();
        for (var source : read(ddlFiles)) {
            sb.append(source.sql());
            if (!source.sql().endsWith("\n")) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    /** Connection factory resolving JDBC drivers from the {@code titanJdbc} configuration. */
    static DdlContainerIntrospector.ConnectionFactory titanJdbcConnections(Set<File> driverClasspath) {
        return (jdbcUrl, username, password) -> {
            TitanJdbcConnections.JdbcSession session =
                    TitanJdbcConnections.open(driverClasspath, jdbcUrl, username, password);
            return new DdlContainerIntrospector.ConnectionHandle() {
                @Override
                public Connection connection() {
                    return session.connection();
                }

                @Override
                public void close() throws Exception {
                    session.close();
                }
            };
        };
    }

    private static List<File> sortedFiles(ConfigurableFileCollection ddlFiles) {
        var files = new ArrayList<>(ddlFiles.getFiles());
        files.sort(Comparator.comparing(File::getAbsolutePath));
        return files;
    }
}
