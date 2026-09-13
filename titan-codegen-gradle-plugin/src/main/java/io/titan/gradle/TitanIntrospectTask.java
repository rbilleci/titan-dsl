package io.titan.gradle;

import io.titan.introspect.DdlContainerIntrospector;
import io.titan.introspect.DdlSchemaParser;
import io.titan.introspect.Dialect;
import io.titan.introspect.SchemaChangeDetector;
import io.titan.introspect.SchemaIntrospector;
import io.titan.introspect.SchemaJsonWriter;
import io.titan.introspect.SchemaModel;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

// GAP S8: incrementality and caching are Gradle's job. Inputs/outputs are declared below with
// relative path sensitivity so outputs are relocatable; the plugin disables both up-to-dateness
// and caching when the task reads a live database (the schema is not a tracked input).
@CacheableTask
public abstract class TitanIntrospectTask extends DefaultTask {

    @Input
    @Optional
    public abstract Property<String> getJdbcUrl();

    // GAP G-4: credentials must never enter Gradle's input fingerprints or build-cache keys.
    @Internal
    public abstract Property<String> getUsername();

    @Internal
    public abstract Property<String> getPassword();

    @Input
    public abstract Property<String> getDialect();

    @Input
    public abstract ListProperty<String> getSchemas();

    @InputFiles
    @org.gradle.api.tasks.IgnoreEmptyDirectories
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getDdlFiles();

    /**
     * GAP G-6/G-7: how DDL files become a schema model. {@code "container"} (default) applies
     * them to a scratch Testcontainers database and introspects via JDBC — equivalent to the
     * live-database path by construction. {@code "parser"} is the explicit no-Docker fallback
     * regex parser, which fails hard on statements it cannot represent.
     */
    @Input
    @Optional
    public abstract Property<String> getDdlMode();

    // JDBC drivers also affect container-backed DDL results; include them in cache inputs.
    @org.gradle.api.tasks.Classpath
    public abstract ConfigurableFileCollection getJdbcDriverClasspath();

    @OutputFile
    public abstract RegularFileProperty getSchemaJsonFile();

    // GAP S8: the sibling snapshot/checksum files the action writes are declared outputs so the
    // build cache stores and restores them together with schema.json.
    @OutputFile
    public Provider<File> getColumnsSnapshotFile() {
        return siblingOfSchemaJson(".columns.snapshot");
    }

    @OutputFile
    public Provider<File> getChecksumFile() {
        return siblingOfSchemaJson(".checksum");
    }

    private Provider<File> siblingOfSchemaJson(String suffix) {
        // getLocationOnly(): the schema.json location without its producer semantics, so the
        // derived sibling can be queried while fingerprinting this task's own outputs.
        return getSchemaJsonFile().getLocationOnly()
                .map(location -> {
                    File file = location.getAsFile();
                    return new File(file.getParentFile(), file.getName() + suffix);
                });
    }

    @TaskAction
    public void run() throws Exception {
        Dialect selectedDialect = Dialect.valueOf(getDialect().get().toUpperCase());
        List<String> schemas = getSchemas().get();

        SchemaModel model;
        if (!getDdlFiles().isEmpty()) {
            TitanDdlMode ddlMode = TitanDdlMode.parse(getDdlMode().getOrElse(TitanDdlMode.DEFAULT));
            model = switch (ddlMode) {
                case CONTAINER -> introspectDdlViaScratchContainer(selectedDialect, schemas);
                case PARSER -> parseFromDdl(selectedDialect, schemas);
            };
            getLogger().lifecycle("Titan introspection completed from DDL sources ({} files, ddlMode {}).",
                    getDdlFiles().getFiles().size(), ddlMode.name().toLowerCase(java.util.Locale.ROOT));
        } else {
            String jdbcUrl = getJdbcUrl().getOrNull();
            if (jdbcUrl == null || jdbcUrl.isBlank()) {
                throw new IllegalStateException("titan.database.jdbcUrl must be configured when ddlDir is not provided.");
            }
            String username = getUsername().getOrElse("");
            String password = getPassword().getOrElse("");
            try (var session = TitanJdbcConnections.open(getJdbcDriverClasspath().getFiles(), jdbcUrl, username, password)) {
                model = new SchemaIntrospector().introspect(session.connection(), selectedDialect, schemas);
            }
            getLogger().lifecycle("Titan introspection completed from JDBC URL {}.", redactedJdbcUrl(jdbcUrl));
        }

        String schemaJson = new SchemaJsonWriter().write(model);
        var outputPath = getSchemaJsonFile().get().getAsFile().toPath();
        Files.createDirectories(outputPath.getParent());

        SchemaChangeDetector changeDetector = new SchemaChangeDetector();
        var currentFingerprint = changeDetector.fingerprint(model);
        var snapshotPath = outputPath.resolveSibling(outputPath.getFileName() + ".columns.snapshot");
        var checksumPath = outputPath.resolveSibling(outputPath.getFileName() + ".checksum");

        if (Files.exists(snapshotPath)) {
            var previousFingerprint = changeDetector.deserialize(Files.readAllLines(snapshotPath, StandardCharsets.UTF_8));
            var diff = changeDetector.diff(previousFingerprint, currentFingerprint);
            if (diff.hasChanges()) {
                getLogger().lifecycle(
                        "Titan schema diff detected: +{} / -{} / ~{} columns",
                        diff.addedColumns().size(),
                        diff.removedColumns().size(),
                        diff.changedColumns().size()
                );
                diff.addedColumns().forEach(column -> getLogger().lifecycle("  + {}", column));
                diff.removedColumns().forEach(column -> getLogger().lifecycle("  - {}", column));
                diff.changedColumns().forEach(change -> getLogger().lifecycle("  ~ {}", change.columnPath()));
            } else {
                getLogger().lifecycle("Titan schema diff: no column changes detected.");
            }
        } else {
            getLogger().lifecycle("Titan schema diff baseline initialized at {}", snapshotPath);
        }

        Files.writeString(outputPath, schemaJson, StandardCharsets.UTF_8);
        Files.write(snapshotPath, changeDetector.serialize(currentFingerprint), StandardCharsets.UTF_8);
        String checksum = changeDetector.checksum(currentFingerprint);
        Files.writeString(checksumPath, checksum + System.lineSeparator(), StandardCharsets.UTF_8);

        getLogger().lifecycle("Titan schema metadata written to {}", outputPath);
        getLogger().lifecycle("Titan schema checksum written to {}: {}", checksumPath, checksum);
    }

    // GAP G-4: never log raw JDBC URLs; drop query parameters and embedded userinfo so
    // credentials cannot leak into build logs.
    static String redactedJdbcUrl(String jdbcUrl) {
        return TitanJdbcConnections.redactedJdbcUrl(jdbcUrl);
    }

    // GAP G-6/G-7: DDL mode delegates to JDBC mode. The DDL files are applied to a scratch
    // Testcontainers database for the configured dialect and introspected through the same
    // SchemaIntrospector the live-database path uses — equivalence by construction.
    private SchemaModel introspectDdlViaScratchContainer(Dialect dialect, List<String> schemas) throws Exception {
        return new DdlContainerIntrospector().introspect(
                TitanDdlSources.read(getDdlFiles()),
                dialect,
                schemas,
                TitanDdlSources.titanJdbcConnections(getJdbcDriverClasspath().getFiles())
        );
    }

    private SchemaModel parseFromDdl(Dialect dialect, List<String> schemas) throws IOException {
        String defaultSchema = schemas.isEmpty() ? "public" : schemas.get(0);
        return new DdlSchemaParser().parse(TitanDdlSources.concatenate(getDdlFiles()), dialect, defaultSchema);
    }
}
