package titan.dsl.generative;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DslCuratedSeedCorpusTest {

    @TempDir
    Path tempDir;

    @Test
    void curatedDslSeedCorpusEntriesReplayWithRationaleAndArtifacts() throws Exception {
        DslGenerativeHarness harness = new DslGenerativeHarness();

        for (CuratedDslSeedCase curated : DslSeedCorpus.curatedCorpus()) {
            var result = harness.run(curated.profile(), curated.seed(), tempDir);
            assertNotNull(curated.rationale(), "Missing rationale for curated seed " + curated);
            assertFalse(curated.rationale().isBlank(), "Blank rationale for curated seed " + curated);
            assertNotNull(result.generatedCase(), "Missing generated case for curated seed " + curated);
            assertNotNull(result.artifactDir(), "Missing artifact dir for curated seed " + curated);
        }
    }
}
