package io.titan.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;

import java.util.List;

// GAP G-9: tasks are registered lazily (tasks.register), all locations flow through
// ProjectLayout/Provider chains, and no Project instance is captured by anything that runs at
// execution time — the plugin is configuration-cache compatible (verified by a TestKit test that
// reuses the cache on the second build).
public class TitanCodegenPlugin implements Plugin<Project> {
    public static final String EXTENSION_NAME = "titan";

    /**
     * Resolvable configuration the introspection tasks ({@code titanIntrospect},
     * {@code titanGenerate}) load JDBC drivers from in JDBC mode:
     * {@code dependencies { titanJdbc("org.postgresql:postgresql:42.7.4") }}.
     */
    public static final String JDBC_CONFIGURATION_NAME = "titanJdbc";

    private static final String LIVE_DATABASE_NOT_AN_INPUT =
            "live database schema is not a tracked task input";

    @Override
    public void apply(Project project) {
        ProjectLayout layout = project.getLayout();

        TitanCodegenExtension existing = project.getExtensions().findByType(TitanCodegenExtension.class);
        TitanCodegenExtension extension = existing == null
                ? project.getExtensions().create(EXTENSION_NAME, TitanCodegenExtension.class) : existing;
        extension.getDatabase().getDialect().convention("postgresql");
        extension.getDatabase().getSchemas().convention(List.of("public"));
        // GAP G-6/G-7: DDL-file introspection defaults to the scratch-container mode (apply DDL
        // to a Testcontainers database, introspect via JDBC); 'parser' is the explicit
        // no-Docker fallback.
        extension.getDatabase().getDdlMode().convention(TitanDdlMode.DEFAULT);
        extension.getCatalog().getTargetPackage().convention("generated.titan");
        extension.getCatalog().getOutputDir().convention(layout.getBuildDirectory().dir("generated/sources/titan").map(d -> d.getAsFile().getAbsolutePath()));
        Configuration titanJdbc = project.getConfigurations().create(JDBC_CONFIGURATION_NAME, configuration -> {
            configuration.setCanBeConsumed(false);
            configuration.setCanBeResolved(true);
            configuration.setVisible(false);
            configuration.setDescription("JDBC drivers resolved by Titan's introspection tasks (titanIntrospect, titanGenerate).");
        });

        Provider<RegularFile> schemaJson = layout.getBuildDirectory().file("titan/schema.json");
        // Resolved lazily so a ddlDir set after plugin application is honored; a blank value is
        // treated like an unset one instead of silently scanning the whole project directory.
        Provider<Object> ddlSqlFiles = extension.getDatabase().getDdlDir()
                .<Object>map(ddlDir -> ddlDir.isBlank()
                        ? List.of()
                        : layout.getProjectDirectory().dir(ddlDir).getAsFileTree().matching(spec -> spec.include("**/*.sql")))
                .orElse(List.of());
        Provider<Directory> catalogOutputDir = extension.getCatalog().getOutputDir()
                .map(path -> layout.getProjectDirectory().dir(path));
        TaskProvider<TitanIntrospectTask> introspect = project.getTasks().register("titanIntrospect", TitanIntrospectTask.class, task -> {
            task.setGroup("titan");
            task.setDescription("Introspect schema metadata from JDBC or DDL sources.");
            task.getJdbcUrl().set(extension.getDatabase().getJdbcUrl());
            task.getUsername().set(extension.getDatabase().getUsername());
            task.getPassword().set(extension.getDatabase().getPassword());
            task.getDialect().set(extension.getDatabase().getDialect());
            task.getSchemas().set(extension.getDatabase().getSchemas());
            task.getSchemaJsonFile().set(schemaJson);
            task.getDdlFiles().from(ddlSqlFiles);
            task.getDdlMode().set(extension.getDatabase().getDdlMode());
            task.getJdbcDriverClasspath().from(titanJdbc);
            task.getOutputs().upToDateWhen(t -> {
                TitanIntrospectTask introspectTask = (TitanIntrospectTask) t;
                return !runsAgainstLiveDatabase(introspectTask.getJdbcUrl(), introspectTask.getDdlFiles());
            });
            task.getOutputs().doNotCacheIf(LIVE_DATABASE_NOT_AN_INPUT, t -> {
                TitanIntrospectTask introspectTask = (TitanIntrospectTask) t;
                return runsAgainstLiveDatabase(introspectTask.getJdbcUrl(), introspectTask.getDdlFiles());
            });
        });

        TaskProvider<TitanGenerateTask> generate = project.getTasks().register("titanGenerate", TitanGenerateTask.class, task -> {
            task.setGroup("titan");
            task.setDescription("Generate Titan catalog source files from schema metadata.");
            task.getSchemaJsonFile().set(introspect.flatMap(TitanIntrospectTask::getSchemaJsonFile));
            task.getJdbcUrl().set(extension.getDatabase().getJdbcUrl());
            task.getUsername().set(extension.getDatabase().getUsername());
            task.getPassword().set(extension.getDatabase().getPassword());
            task.getDialect().set(extension.getDatabase().getDialect());
            task.getSchemas().set(extension.getDatabase().getSchemas());
            task.getDdlFiles().from(ddlSqlFiles);
            task.getDdlMode().set(extension.getDatabase().getDdlMode());
            task.getJdbcDriverClasspath().from(titanJdbc);
            task.getTargetPackage().set(extension.getCatalog().getTargetPackage());
            task.getOutputDir().set(catalogOutputDir);
            task.getOutputs().upToDateWhen(t -> {
                TitanGenerateTask generateTask = (TitanGenerateTask) t;
                return !runsAgainstLiveDatabase(generateTask.getJdbcUrl(), generateTask.getDdlFiles());
            });
            task.getOutputs().doNotCacheIf(LIVE_DATABASE_NOT_AN_INPUT, t -> {
                TitanGenerateTask generateTask = (TitanGenerateTask) t;
                return runsAgainstLiveDatabase(generateTask.getJdbcUrl(), generateTask.getDdlFiles());
            });
        });

        project.getPlugins().withType(JavaPlugin.class, ignored -> {
            SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
            sourceSets.named(SourceSet.MAIN_SOURCE_SET_NAME, main ->
                    main.getJava().srcDir(generate.flatMap(TitanGenerateTask::getOutputDir)));
        });
    }

    // GAP G-3: the live database schema is not a tracked task input, so JDBC-backed runs must
    // never be considered UP-TO-DATE (or restored from a build cache).
    private static boolean runsAgainstLiveDatabase(Property<String> jdbcUrl, ConfigurableFileCollection ddlFiles) {
        String url = jdbcUrl.getOrNull();
        return ddlFiles.isEmpty() && url != null && !url.isBlank();
    }
}
