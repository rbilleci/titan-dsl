package io.titan.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

/** GAP G-6/G-7: DDL-mode selection — container by default, parser as explicit fallback. */
class TitanDdlModeTest {

    @Test
    void parsesBothModesCaseInsensitively() {
        assertEquals(TitanDdlMode.CONTAINER, TitanDdlMode.parse("container"));
        assertEquals(TitanDdlMode.CONTAINER, TitanDdlMode.parse("CONTAINER"));
        assertEquals(TitanDdlMode.PARSER, TitanDdlMode.parse("parser"));
    }

    @Test
    void unknownModeFailsWithBothValidValuesListed() {
        GradleException failure = assertThrows(GradleException.class, () -> TitanDdlMode.parse("regex"));
        assertTrue(failure.getMessage().contains("'regex'"), failure.getMessage());
        assertTrue(failure.getMessage().contains("'container'"), failure.getMessage());
        assertTrue(failure.getMessage().contains("'parser'"), failure.getMessage());
        assertTrue(failure.getMessage().contains("titanJdbc"), failure.getMessage());
    }

    @Test
    void containerModeIsTheDefaultAndFlowsIntoBothTasks() {
        Project project = ProjectBuilder.builder().build();
        project.getPlugins().apply(TitanCodegenPlugin.class);

        TitanCodegenExtension extension = (TitanCodegenExtension) project.getExtensions().getByName(TitanCodegenPlugin.EXTENSION_NAME);
        assertEquals("container", extension.getDatabase().getDdlMode().get(),
                "scratch-container introspection is the default DDL behavior (G-6/G-7)");

        TitanIntrospectTask introspect = (TitanIntrospectTask) project.getTasks().getByName("titanIntrospect");
        TitanGenerateTask generate = (TitanGenerateTask) project.getTasks().getByName("titanGenerate");
        assertEquals("container", introspect.getDdlMode().get());
        assertEquals("container", generate.getDdlMode().get());

        extension.getDatabase().getDdlMode().set("parser");
        assertEquals("parser", introspect.getDdlMode().get(), "extension value must flow into the task");
        assertEquals("parser", generate.getDdlMode().get());
    }
}
