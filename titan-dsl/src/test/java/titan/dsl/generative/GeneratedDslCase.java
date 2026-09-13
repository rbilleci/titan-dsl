package titan.dsl.generative;

import java.util.List;

record GeneratedDslCase(
        long seed,
        String profile,
        String family,
        String summary,
        boolean shouldFail,
        List<String> expectedFragments,
        DslCaseExecutable executable
) {
}
