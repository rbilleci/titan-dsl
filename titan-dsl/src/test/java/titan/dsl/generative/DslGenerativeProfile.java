package titan.dsl.generative;

enum DslGenerativeProfile {
    RENDERING_BASIC("dsl-rendering-basic"),
    INVALID_RENDERING("dsl-invalid-rendering"),
    PROJECTION_ARITY("dsl-projection-arity"),
    FETCH_MODES("dsl-fetch-modes"),
    SOURCE_FIELDS("dsl-source-fields"),
    COMPOSITION_EXPANSION("dsl-composition-expansion");

    private final String id;

    DslGenerativeProfile(String id) {
        this.id = id;
    }

    String id() {
        return id;
    }

    static DslGenerativeProfile fromId(String raw) {
        for (DslGenerativeProfile profile : values()) {
            if (profile.id.equalsIgnoreCase(raw) || profile.name().equalsIgnoreCase(raw)) {
                return profile;
            }
        }
        throw new IllegalArgumentException("Unknown DSL generative profile: " + raw);
    }
}
