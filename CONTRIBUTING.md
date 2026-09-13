# Contributing to Titan DSL

Development takes place at <https://github.com/rbilleci/titan-dsl>. Bug reports
and focused pull requests are welcome. Discuss API changes in an issue before
starting a large implementation. See [security reporting](SECURITY.md) for
potential vulnerabilities.

For library usage, supported features, and complete recipes, start with the
[README developer documentation](README.md#contents).

## Local workflow

Install JDK 21, set `JAVA_HOME`, and use the checked-in Gradle wrapper. No sibling
repositories, Maven-local artifacts, Docker, or database credentials are needed.

```bash
./gradlew build
./gradlew -p examples/quickstart run runFeatures
```

Use `gradlew.bat` on Windows. Run a focused test during development with:

```bash
./gradlew :titan-dsl:test --tests titan.dsl.DslRenderingModesTest
```

GitHub Actions CI is disabled because the account has no runner funding. Before
submitting changes, run the full build, `generatePomFileForMavenPublication`, and
the quickstart and feature recipes locally. Each module's `build/` contains
`reports/tests/test/`, `test-results/test/`, and `docs/javadoc/`.

## Project boundaries

- The root build coordinates three peer modules and publishes nothing. Keep
  common Java/testing/publication settings in `build-logic/` convention plugins,
  module-specific dependencies and tasks in each module's build script, and the
  shared release version in `gradle.properties`.
- Keep the DSL runtime artifact free of third-party runtime dependencies and coupling to
  application frameworks, database execution layers, or other repositories.
- Schema introspection and catalog generation belong in `titan-codegen`; Gradle
  task wiring belongs in `titan-codegen-gradle-plugin`. Neither may depend on
  Titan core. Test generated sources against the DSL in this same build.
- Run `./gradlew -p examples/schema-codegen build run` for the complete standalone
  generation workflow, and `./gradlew integrationTest` for Docker-backed schema
  tests. Keep these separate from the Docker-free default build.
- Preserve ordered bind values and test relevant PostgreSQL/MySQL rendering
  when changing SQL behavior. Literal and parameterized rendering are distinct.
- Do not hand-edit module `build/generated/` directories. The typed tuple/builder
  sources come from `titan-dsl/build.gradle.kts`; regenerate with
  `./gradlew :titan-dsl:generateAritySources`.
- Typed tuple arity is capped at ten; see the
  [arity policy](docs/post-loop-design-considerations.md).
- Use existing Java formatting and add regression coverage for behavior changes.
  Error Prone runs during compilation.

## Pull requests

Create a branch for one cohesive change. Describe the problem, resulting behavior,
and commands you ran. Update examples and documentation when public behavior
changes. Avoid including build outputs, credentials, or unrelated edits.

Contributions to project-owned files are under GPL-3.0-only, as described in
[LICENSE](LICENSE). Submit only material you have the right to contribute and
retain existing third-party license notices.

Be respectful in issues and reviews: discuss the code, explain disagreements,
and avoid personal attacks or harassment.
