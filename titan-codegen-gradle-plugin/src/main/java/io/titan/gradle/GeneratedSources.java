package io.titan.gradle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Writes only generator-owned files; never recursively clears the configured directory. */
final class GeneratedSources {
    private static final String MANIFEST = ".titan-generated-files";

    private GeneratedSources() { }

    static void write(Path directory, Map<String, String> sources) throws IOException {
        Path root = directory.toAbsolutePath().normalize();
        rejectSymlinks(root);
        Path manifest = root.resolve(MANIFEST);
        rejectSymlinks(manifest);
        Set<String> previous = Files.exists(manifest)
                ? new HashSet<>(Files.readAllLines(manifest, StandardCharsets.UTF_8)) : Set.of();
        // Validate every target before creating, deleting or replacing any output.
        for (String name : previous) {
            checkedTarget(root, name);
        }
        for (String name : sources.keySet()) {
            Path target = checkedTarget(root, name);
            if (Files.exists(target) && !previous.contains(name)) {
                throw new IOException("Refusing to overwrite unowned generated-source path: " + target
                        + ". Use a fresh dedicated output directory when migrating from older generators.");
            }
        }
        Files.createDirectories(root);
        for (var entry : sources.entrySet()) {
            Path target = checkedTarget(root, entry.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, entry.getValue(), StandardCharsets.UTF_8);
        }
        for (String name : previous) {
            if (!sources.containsKey(name)) {
                Files.deleteIfExists(checkedTarget(root, name));
            }
        }
        Files.write(manifest, sources.keySet().stream().sorted().toList(), StandardCharsets.UTF_8);
    }

    private static Path checkedTarget(Path root, String name) throws IOException {
        Path relative = Path.of(name);
        Path target = root.resolve(relative).normalize();
        if (relative.isAbsolute() || !target.startsWith(root) || target.equals(root)
                || !name.endsWith(".java") || !relative.equals(relative.normalize())) {
            throw new IOException("Invalid generated-source path: " + name);
        }
        rejectSymlinks(target);
        return target;
    }

    private static void rejectSymlinks(Path target) throws IOException {
        for (Path path = target; path != null; path = path.getParent()) {
            if (Files.isSymbolicLink(path)) {
                throw new IOException("Generated-source paths must not traverse symbolic links: " + path);
            }
        }
    }
}
