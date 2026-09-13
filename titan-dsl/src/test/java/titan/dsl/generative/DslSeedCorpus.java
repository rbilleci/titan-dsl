package titan.dsl.generative;

import java.util.List;

final class DslSeedCorpus {

    private DslSeedCorpus() {
    }

    static List<Long> curatedRenderingSeeds() {
        return List.of(601L, 602L, 603L, 604L, 605L, 606L, 607L, 608L);
    }

    static List<Long> curatedInvalidRenderingSeeds() {
        return List.of(701L, 702L, 703L, 704L, 705L, 706L, 707L, 708L);
    }

    static List<Long> curatedProjectionAritySeeds() {
        return List.of(801L, 802L, 803L, 804L, 805L, 806L);
    }

    static List<Long> curatedFetchModeSeeds() {
        return List.of(901L, 902L, 903L, 904L, 905L, 906L);
    }

    static List<Long> curatedSourceFieldSeeds() {
        return List.of(1001L, 1002L, 1003L, 1004L, 1005L, 1006L);
    }

    static List<Long> curatedCompositionExpansionSeeds() {
        return List.of(1101L, 1102L, 1103L, 1104L, 1105L);
    }

    static List<CuratedDslSeedCase> curatedCorpus() {
        return List.of(
                new CuratedDslSeedCase(DslGenerativeProfile.RENDERING_BASIC, 601L, "simple filter/order rendering"),
                new CuratedDslSeedCase(DslGenerativeProfile.RENDERING_BASIC, 603L, "inline-view rendering"),
                new CuratedDslSeedCase(DslGenerativeProfile.RENDERING_BASIC, 605L, "scalar subquery rendering"),
                new CuratedDslSeedCase(DslGenerativeProfile.RENDERING_BASIC, 607L, "fetchInto rendering hint"),
                new CuratedDslSeedCase(DslGenerativeProfile.INVALID_RENDERING, 701L, "scalar multi-column guardrail"),
                new CuratedDslSeedCase(DslGenerativeProfile.INVALID_RENDERING, 703L, "blank CTE SQL guardrail"),
                new CuratedDslSeedCase(DslGenerativeProfile.INVALID_RENDERING, 705L, "fetchInto type mismatch guardrail"),
                new CuratedDslSeedCase(DslGenerativeProfile.INVALID_RENDERING, 706L, "fetchInto projection count guardrail"),
                new CuratedDslSeedCase(DslGenerativeProfile.INVALID_RENDERING, 707L, "fetchInto identifier-shape guardrail"),
                new CuratedDslSeedCase(DslGenerativeProfile.INVALID_RENDERING, 708L, "forEach callback arity guardrail"),
                new CuratedDslSeedCase(DslGenerativeProfile.PROJECTION_ARITY, 801L, "single-column projection width baseline"),
                new CuratedDslSeedCase(DslGenerativeProfile.PROJECTION_ARITY, 806L, "upper-cap projection width coverage"),
                new CuratedDslSeedCase(DslGenerativeProfile.FETCH_MODES, 901L, "basic fetch rendering"),
                new CuratedDslSeedCase(DslGenerativeProfile.FETCH_MODES, 904L, "fetchInto terminal coverage"),
                new CuratedDslSeedCase(DslGenerativeProfile.FETCH_MODES, 906L, "fetchInto with inline-view coverage"),
                new CuratedDslSeedCase(DslGenerativeProfile.SOURCE_FIELDS, 1001L, "inline-view field selection coverage"),
                new CuratedDslSeedCase(DslGenerativeProfile.SOURCE_FIELDS, 1004L, "cte field propagation through join coverage"),
                new CuratedDslSeedCase(DslGenerativeProfile.COMPOSITION_EXPANSION, 1101L, "inline-view join ordering + fetchOne composition"),
                new CuratedDslSeedCase(DslGenerativeProfile.COMPOSITION_EXPANSION, 1102L, "cte join grouping/having + fetchCount composition"),
                new CuratedDslSeedCase(DslGenerativeProfile.COMPOSITION_EXPANSION, 1103L, "cte scalar-subquery + fetchExists composition"),
                new CuratedDslSeedCase(DslGenerativeProfile.COMPOSITION_EXPANSION, 1104L, "inline-view ordering + fetchInto composition"),
                new CuratedDslSeedCase(DslGenerativeProfile.COMPOSITION_EXPANSION, 1105L, "cte join + exists-subquery + fetch composition")
        );
    }
}
