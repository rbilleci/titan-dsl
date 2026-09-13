package io.titan.gradle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class GeneratedSourcesTest {
    @TempDir Path directory;

    @Test void removesStaleOwnedSourcesButPreservesUnrelatedFiles() throws Exception {
        Files.writeString(directory.resolve("manual.txt"), "keep");
        GeneratedSources.write(directory, Map.of("gen/Old.java", "old"));
        GeneratedSources.write(directory, Map.of("gen/New.java", "new"));
        assertFalse(Files.exists(directory.resolve("gen/Old.java")));
        assertEquals("new", Files.readString(directory.resolve("gen/New.java")));
        assertEquals("keep", Files.readString(directory.resolve("manual.txt")));
    }

    @Test void refusesUnownedCollisionsBeforeChangingAnyOutput() throws Exception {
        GeneratedSources.write(directory, Map.of("Old.java", "old"));
        Files.writeString(directory.resolve("Manual.java"), "manual");
        assertThrows(IOException.class, () -> GeneratedSources.write(directory, Map.of("Manual.java", "new")));
        assertEquals("old", Files.readString(directory.resolve("Old.java")));
        assertEquals("manual", Files.readString(directory.resolve("Manual.java")));
    }

    @Test void rejectsTraversalAndSymlinks() throws Exception {
        assertThrows(IOException.class, () -> GeneratedSources.write(directory, Map.of("../Escape.java", "bad")));
        Path outside = Files.createDirectory(directory.resolve("outside"));
        Path root = Files.createDirectory(directory.resolve("generated"));
        Files.createSymbolicLink(root.resolve("link"), outside);
        assertThrows(IOException.class, () -> GeneratedSources.write(root, Map.of("link/Escape.java", "bad")));
        assertFalse(Files.exists(outside.resolve("Escape.java")));
    }
}
