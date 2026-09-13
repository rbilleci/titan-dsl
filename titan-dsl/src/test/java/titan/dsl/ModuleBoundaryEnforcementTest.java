package titan.dsl;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleBoundaryEnforcementTest {

    @Test
    void dslModuleBuildDoesNotDeclareProjectDependenciesOnAnyTitanModules() throws IOException, URISyntaxException {
        Path buildFile = resolveDslModuleBuildFile();
        String buildScript = Files.readString(buildFile);

        boolean declaresTitanProjectDependency = java.util.regex.Pattern
                .compile("project\\(\\s*\"(:titan-[^\"]+)\"\\s*\\)")
                .matcher(buildScript)
                .find();

        assertTrue(
                !declaresTitanProjectDependency,
                "titan-dsl must remain standalone and must not declare any :titan-* project dependency");
    }

    @Test
    void dslModuleDoesNotDependOnTranspilerRuntimeOrToolingInternals() {
        JavaClasses classes = new ClassFileImporter().importPackages("titan.dsl");

        noClasses()
                .that().resideInAPackage("titan.dsl..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "io.titan.transpiler..",
                        "io.titan.catalog..",
                        "io.titan.introspect..",
                        "io.titan.runtime.jdbc..",
                        "io.titan.runtime.testing..",
                        "io.titan.gradle..",
                        "io.titan.intellij..")
                .check(classes);
    }

    private static Path resolveDslModuleBuildFile() throws URISyntaxException {
        Path classesDir = Path.of(ModuleBoundaryEnforcementTest.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());

        Path moduleDir = classesDir;
        while (moduleDir != null && !Files.exists(moduleDir.resolve("build.gradle.kts"))) {
            moduleDir = moduleDir.getParent();
        }

        if (moduleDir == null) {
            throw new IllegalStateException("Unable to locate titan-dsl module directory from test runtime path: " + classesDir);
        }

        Path buildFile = moduleDir.resolve("build.gradle.kts");
        if (!Files.exists(buildFile)) {
            throw new IllegalStateException("Unable to locate titan-dsl build.gradle.kts at " + buildFile);
        }
        return buildFile;
    }
}
