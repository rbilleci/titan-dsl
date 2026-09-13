package titan.dsl.generative;

record CuratedDslSeedCase(
        DslGenerativeProfile profile,
        long seed,
        String rationale
) {
}
