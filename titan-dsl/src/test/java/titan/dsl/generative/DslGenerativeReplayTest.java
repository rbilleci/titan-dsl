package titan.dsl.generative;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DslGenerativeReplayTest {

    @TempDir
    Path tempDir;

    @Test
    void replaysRequestedDslSeedFromSystemProperties() throws Exception {
        String rawProfile = System.getProperty("titan.dsl.generative.profile");
        String rawSeed = System.getProperty("titan.dsl.generative.seed");
        Assumptions.assumeTrue(rawProfile != null && rawSeed != null,
                "Replay requested only when -Dtitan.dsl.generative.profile and -Dtitan.dsl.generative.seed are set");

        DslGenerativeProfile profile = DslGenerativeProfile.fromId(rawProfile);
        long seed = Long.parseUnsignedLong(rawSeed);
        DslGenerativeHarness.HarnessResult result = new DslGenerativeHarness().run(profile, seed, tempDir);

        assertNotNull(result.generatedCase());
        if (result.generatedCase().shouldFail()) {
            assertNotNull(result.errorMessage());
            for (String fragment : result.generatedCase().expectedFragments()) {
                assertTrue(result.errorMessage().contains(fragment));
            }
        } else {
            assertNotNull(result.sql());
            assertFalse(result.sql().isBlank());
        }
    }
}
