package io.titan.gradle;

import io.titan.catalog.CatalogGenerator;
import io.titan.introspect.DdlContainerIntrospector;
import io.titan.introspect.DdlSchemaParser;
import io.titan.introspect.Dialect;
import io.titan.introspect.SchemaIntrospector;
import io.titan.introspect.SchemaModel;
import io.titan.introspect.SchemaJsonReader;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

// GAP S8: this task previously kept a homegrown ".titan-generate.fingerprint" and early-returned
// inside the action, which fought Gradle's own incrementality (deleting an output left it missing
// forever because the fingerprint still matched). Incrementality is now exclusively Gradle's:
// inputs/outputs are declared below, the action always regenerates deterministically, and the
// plugin disables up-to-dateness/caching for live-database runs whose schema is not a tracked
// input.
@CacheableTask
public abstract class TitanGenerateTask extends DefaultTask {

    /** The plugin generates from the exact snapshot produced by titanIntrospect. */
    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getSchemaJsonFile();

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

    // Drivers also affect container-backed DDL introspection, so track their classpath.
    @org.gradle.api.tasks.Classpath
    public abstract ConfigurableFileCollection getJdbcDriverClasspath();

    @Input
    public abstract Property<String> getTargetPackage();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    @TaskAction
    public void run() throws Exception {
        Dialect selectedDialect = Dialect.valueOf(getDialect().get().toUpperCase());
        List<String> schemas = getSchemas().get();
        SchemaModel schemaModel;

        if (getSchemaJsonFile().isPresent()) {
            schemaModel = new SchemaJsonReader().read(getSchemaJsonFile().get().getAsFile().toPath());
        } else if (!getDdlFiles().isEmpty()) {
            TitanDdlMode ddlMode = TitanDdlMode.parse(getDdlMode().getOrElse(TitanDdlMode.DEFAULT));
            schemaModel = switch (ddlMode) {
                case CONTAINER -> introspectDdlViaScratchContainer(selectedDialect, schemas);
                case PARSER -> parseFromDdl(selectedDialect, schemas);
            };
        } else {
            String jdbcUrl = getJdbcUrl().getOrNull();
            if (jdbcUrl == null || jdbcUrl.isBlank()) {
                throw new IllegalStateException("titan.database.jdbcUrl must be configured when ddlDir is not provided.");
            }
            try (var session = TitanJdbcConnections.open(
                    getJdbcDriverClasspath().getFiles(), jdbcUrl, getUsername().getOrElse(""), getPassword().getOrElse(""))) {
                schemaModel = new SchemaIntrospector().introspect(session.connection(), selectedDialect, schemas);
            }
        }

        Path outputPath = getOutputDir().get().getAsFile().toPath();

        String targetPackage = getTargetPackage().get();
        // GAP G-8: the facade validates name-mangling collisions first (failing with a
        // diagnostic naming the offending tables/columns) and refuses duplicate output paths
        // instead of silently overwriting generated files.
        Map<String, String> generated = new CatalogGenerator().generate(schemaModel, targetPackage);

        GeneratedSources.write(outputPath, generated);

        getLogger().lifecycle("Titan catalog generation wrote {} files to {}", generated.size(), outputPath);
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
