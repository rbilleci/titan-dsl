package io.titan.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TitanCodegenPluginFunctionalTest {
    @TempDir Path directory;

    @Test void standaloneBuildCompilesAndRunsGeneratedCatalogAndReusesConfigurationCache() throws Exception {
        fixture();
        BuildResult first = run("build", "run", "--configuration-cache");
        assertEquals(TaskOutcome.SUCCESS, first.task(":titanIntrospect").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, first.task(":titanGenerate").getOutcome());
        assertTrue(first.getOutput().contains("SELECT id FROM app.users WHERE name = ?"));
        assertNull(first.task(":titanTranspile"));
        assertNull(first.task(":titanPackage"));
        BuildResult second = run("build", "run", "--configuration-cache");
        assertTrue(second.getOutput().contains("Reusing configuration cache."));
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":titanGenerate").getOutcome(), second.getOutput());
        Path descriptor = directory.resolve("build/generated/sources/titan/generated/titan/app/tables/Users.java");
        Files.delete(descriptor);
        assertEquals(TaskOutcome.SUCCESS, run("titanGenerate").task(":titanGenerate").getOutcome());
        assertTrue(Files.exists(descriptor));
        Files.writeString(directory.resolve("schema.sql"), "CREATE TABLE app.accounts (id INTEGER NOT NULL);");
        run("titanGenerate");
        assertFalse(Files.exists(descriptor), "removed tables must not leave stale Java classes");
    }

    @Test void generationUsesSnapshotWithoutReopeningSchemaSource() throws Exception {
        fixture();
        run("titanIntrospect");
        Files.writeString(directory.resolve("schema.sql"), "THIS IS INVALID DDL;");
        BuildResult generated = run("titanGenerate", "-x", "titanIntrospect");
        assertEquals(TaskOutcome.SUCCESS, generated.task(":titanGenerate").getOutcome());
        assertTrue(Files.exists(directory.resolve("build/generated/sources/titan/generated/titan/app/tables/Users.java")));
    }

    private void fixture() throws Exception {
        String dslPath = Path.of(Class.forName("titan.dsl.Table").getProtectionDomain()
                .getCodeSource().getLocation().toURI()).toString().replace("\\", "/");
        Files.writeString(directory.resolve("settings.gradle.kts"), "rootProject.name = \"standalone-fixture\"\n");
        Files.writeString(directory.resolve("build.gradle.kts"), """
                plugins { application; id("io.titan.codegen") }
                dependencies { implementation(files("%s")) }
                application { mainClass.set("example.Query") }
                titan {
                    database {
                        dialect.set("postgresql")
                        schemas.set(listOf("app"))
                        ddlDir.set(".")
                        ddlMode.set("parser")
                    }
                }
                """.formatted(dslPath));
        Files.writeString(directory.resolve("schema.sql"), "CREATE TABLE app.users (id INTEGER NOT NULL, name VARCHAR(100) NOT NULL);");
        Path source = directory.resolve("src/main/java/example/Query.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package example;
                import static generated.titan.app.tables.Users.USERS;
                import titan.dsl.DSL;
                import titan.dsl.SqlDialect;
                public class Query {
                    public static void main(String[] args) {
                        var db = DSL.using(SqlDialect.POSTGRESQL);
                        var sql = db.select(USERS.ID).from(USERS).where(USERS.NAME.eq("Ada"))
                                .render();
                        if (sql.parameters().size() != 1) throw new AssertionError(sql);
                        System.out.println(sql.sql());
                    }
                }
                """);
    }

    private BuildResult run(String... tasks) {
        var arguments = new ArrayList<>(List.of(tasks));
        arguments.addAll(List.of("--stacktrace", "--info", "--max-workers=2"));
        return GradleRunner.create().withProjectDir(directory.toFile()).withPluginClasspath()
                .withArguments(arguments).build();
    }
}
