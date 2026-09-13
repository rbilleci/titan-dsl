# Changelog

## Unreleased

- Add immutable `DSL.using(dialect)` query contexts and no-argument `render()`
  on SELECT/DML builders. Preserve typed projections and explicit-dialect APIs.
- Context CTE helpers retain structured snapshots, dialect propagation, and bind
  values; static literal-capture helpers remain compatible. Update examples to
  configure the dialect once per context.

- Normalize the DSL, codegen, and generation plugin as peer modules. Keep the
  root build as an aggregator and share Java/testing/publication conventions in
  `build-logic`, without changing artifact coordinates or plugin IDs.

- Move schema introspection and catalog generation into this repository as
  `titan-codegen`, with the standalone `io.titan.codegen` Gradle plugin and a
  runnable schema-first example. Keep the DSL runtime artifact dependency-free.
- Generate from the introspection snapshot and track generated-file ownership
  instead of recursively clearing the output directory.

- Consolidate developer documentation in a schema-first README, covering catalog
  generation, feature recipes, JDBC integration, and rendering limits. Document
  the companion generator's current packaging and validate feature recipes locally.

- Prepare Titan DSL as an independently buildable Java 21 library at
  `rbilleci/titan-dsl`.
- Apply GPL-3.0-only licensing to project-owned files and include license notices
  in binary, source, and Javadoc artifacts.
- Add standalone onboarding, a runnable parameterized-query example, contributor
  guidance, and security guidance.
- Generate source/Javadoc JARs and correct Maven publication metadata.
- Remove GitHub Actions CI while the account has no runner funding; keep build
  and consumer-example verification available locally.

The build coordinate remains `io.titan:titan-dsl:0.1.0` for existing source
consumers. This entry does not announce a published `0.1.0` release.
