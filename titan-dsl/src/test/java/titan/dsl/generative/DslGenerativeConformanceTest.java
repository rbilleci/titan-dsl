package titan.dsl.generative;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DslGenerativeConformanceTest {

    @TempDir
    Path tempDir;

    @Test
    void dslRenderingProfileProducesExpectedSqlFragmentsForCuratedSeeds() throws Exception {
        DslGenerativeHarness harness = new DslGenerativeHarness();

        for (long seed : DslSeedCorpus.curatedRenderingSeeds()) {
            DslGenerativeHarness.HarnessResult result = harness.run(DslGenerativeProfile.RENDERING_BASIC, seed, tempDir);
            assertValidSql(result);
        }
    }

    @Test
    void dslInvalidRenderingProfileFailsWithExpectedDiagnostics() throws Exception {
        DslGenerativeHarness harness = new DslGenerativeHarness();

        for (long seed : DslSeedCorpus.curatedInvalidRenderingSeeds()) {
            DslGenerativeHarness.HarnessResult result = harness.run(DslGenerativeProfile.INVALID_RENDERING, seed, tempDir);
            assertInvalidDiagnostic(result);
        }
    }

    @Test
    void dslProjectionArityProfileProducesExpectedSqlFragmentsForCuratedSeeds() throws Exception {
        DslGenerativeHarness harness = new DslGenerativeHarness();

        for (long seed : DslSeedCorpus.curatedProjectionAritySeeds()) {
            DslGenerativeHarness.HarnessResult result = harness.run(DslGenerativeProfile.PROJECTION_ARITY, seed, tempDir);
            assertValidSql(result);
        }
    }

    @Test
    void dslFetchModesProfileProducesExpectedSqlFragmentsForCuratedSeeds() throws Exception {
        DslGenerativeHarness harness = new DslGenerativeHarness();

        for (long seed : DslSeedCorpus.curatedFetchModeSeeds()) {
            DslGenerativeHarness.HarnessResult result = harness.run(DslGenerativeProfile.FETCH_MODES, seed, tempDir);
            assertValidSql(result);
        }
    }

    @Test
    void dslSourceFieldsProfileProducesExpectedSqlFragmentsForCuratedSeeds() throws Exception {
        DslGenerativeHarness harness = new DslGenerativeHarness();

        for (long seed : DslSeedCorpus.curatedSourceFieldSeeds()) {
            DslGenerativeHarness.HarnessResult result = harness.run(DslGenerativeProfile.SOURCE_FIELDS, seed, tempDir);
            assertValidSql(result);
        }
    }

    @Test
    void dslCompositionExpansionProfileProducesExpectedSqlFragmentsForCuratedSeeds() throws Exception {
        DslGenerativeHarness harness = new DslGenerativeHarness();

        for (long seed : DslSeedCorpus.curatedCompositionExpansionSeeds()) {
            DslGenerativeHarness.HarnessResult result = harness.run(DslGenerativeProfile.COMPOSITION_EXPANSION, seed, tempDir);
            assertValidSql(result);
        }
    }

    private static void assertValidSql(DslGenerativeHarness.HarnessResult result) {
        GeneratedDslCase generatedCase = result.generatedCase();
        String failureContext = "seed=" + Long.toUnsignedString(generatedCase.seed())
                + ", profile=" + generatedCase.profile()
                + ", family=" + generatedCase.family()
                + ", artifacts=" + result.artifactDir()
                + ", summary=" + generatedCase.summary();

        assertFalse(generatedCase.shouldFail(), failureContext);
        assertNotNull(result.sql(), failureContext);
        assertFalse(result.sql().isBlank(), failureContext);
        for (String fragment : generatedCase.expectedFragments()) {
            assertTrue(result.sql().toLowerCase().contains(fragment.toLowerCase()), failureContext + ", missingFragment=" + fragment);
        }
    }

    private static void assertInvalidDiagnostic(DslGenerativeHarness.HarnessResult result) {
        GeneratedDslCase generatedCase = result.generatedCase();
        String failureContext = "seed=" + Long.toUnsignedString(generatedCase.seed())
                + ", profile=" + generatedCase.profile()
                + ", family=" + generatedCase.family()
                + ", artifacts=" + result.artifactDir()
                + ", summary=" + generatedCase.summary();

        assertTrue(generatedCase.shouldFail(), failureContext);
        assertNotNull(result.errorMessage(), failureContext);
        for (String fragment : generatedCase.expectedFragments()) {
            assertTrue(result.errorMessage().contains(fragment), failureContext + ", missingFragment=" + fragment);
        }
    }
}
