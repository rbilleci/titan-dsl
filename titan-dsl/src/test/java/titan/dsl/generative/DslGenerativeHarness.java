package titan.dsl.generative;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class DslGenerativeHarness {

    record HarnessResult(
            GeneratedDslCase generatedCase,
            String sql,
            String errorMessage,
            Path artifactDir
    ) {
    }

    HarnessResult run(DslGenerativeProfile profile, long seed, Path tempDir) throws Exception {
        return switch (profile) {
            case RENDERING_BASIC -> execute(new DslRenderingGenerator().generate(seed), tempDir);
            case INVALID_RENDERING -> execute(new DslInvalidRenderingGenerator().generate(seed), tempDir);
            case PROJECTION_ARITY -> execute(new DslProjectionArityGenerator().generate(seed), tempDir);
            case FETCH_MODES -> execute(new DslFetchModeGenerator().generate(seed), tempDir);
            case SOURCE_FIELDS -> execute(new DslSourceFieldGenerator().generate(seed), tempDir);
            case COMPOSITION_EXPANSION -> execute(new DslCompositionExpansionGenerator().generate(seed), tempDir);
        };
    }

    private HarnessResult execute(GeneratedDslCase generatedCase, Path tempDir) throws Exception {
        Path artifactDir = Files.createDirectories(tempDir.resolve(generatedCase.profile() + "-seed-" + Long.toUnsignedString(generatedCase.seed())));
        writeMetadata(artifactDir, generatedCase);
        writeJsonMetadata(artifactDir, generatedCase);
        writeReplayInstructions(artifactDir, generatedCase);

        try {
            String sql = generatedCase.executable().run();
            Files.writeString(artifactDir.resolve("sql.txt"), sql);
            return new HarnessResult(generatedCase, sql, null, artifactDir);
        } catch (IllegalArgumentException error) {
            Files.writeString(artifactDir.resolve("error.txt"), error.getMessage() == null ? "<null>" : error.getMessage());
            return new HarnessResult(generatedCase, null, error.getMessage(), artifactDir);
        }
    }

    private static void writeMetadata(Path artifactDir, GeneratedDslCase generatedCase) throws IOException {
        Files.writeString(artifactDir.resolve("case.txt"), """
                seed: %s
                profile: %s
                family: %s
                summary: %s
                shouldFail: %s
                expectedFragments: %s
                """.formatted(
                Long.toUnsignedString(generatedCase.seed()),
                generatedCase.profile(),
                generatedCase.family(),
                generatedCase.summary(),
                generatedCase.shouldFail(),
                generatedCase.expectedFragments()
        ));
    }

    private static void writeJsonMetadata(Path artifactDir, GeneratedDslCase generatedCase) throws IOException {
        Files.writeString(artifactDir.resolve("case.json"), """
                {
                  "seed": "%s",
                  "profile": "%s",
                  "family": "%s",
                  "summary": "%s",
                  "shouldFail": %s,
                  "expectedFragments": %s
                }
                """.formatted(
                Long.toUnsignedString(generatedCase.seed()),
                jsonEscape(generatedCase.profile()),
                jsonEscape(generatedCase.family()),
                jsonEscape(generatedCase.summary()),
                generatedCase.shouldFail(),
                jsonStringArray(generatedCase.expectedFragments())
        ));
    }

    private static void writeReplayInstructions(Path artifactDir, GeneratedDslCase generatedCase) throws IOException {
        Files.writeString(artifactDir.resolve("repro.txt"), """
                Replay this generated DSL case with:

                source ~/.sdkman/bin/sdkman-init.sh && ./gradlew :titan-dsl:test \
                  --tests titan.dsl.generative.DslGenerativeReplayTest \
                  -Dtitan.dsl.generative.profile=%s \
                  -Dtitan.dsl.generative.seed=%s \
                  --no-daemon
                """.formatted(generatedCase.profile(), Long.toUnsignedString(generatedCase.seed())));
    }

    private static String jsonStringArray(java.util.List<String> values) {
        return values.stream()
                .map(DslGenerativeHarness::jsonEscape)
                .map(value -> "\"" + value + "\"")
                .collect(java.util.stream.Collectors.joining(", ", "[", "]"));
    }

    private static String jsonEscape(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
