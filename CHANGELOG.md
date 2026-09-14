# Changelog

## Unreleased

- Add automatic filters: `FilterPolicy` binds `Filter` keys to columns,
  foreign-key paths, or DSL lambdas for every catalog relation and validates the
  whole catalog when built; `DSLContext.filters(...)`/`scoped(Scope)` add the
  predicates to rendered SELECT, UPDATE, and DELETE statements. Generate
  `Catalog.TABLES`/`Catalog.VIEWS` registries alongside descriptors.
- Enforce write-side scope checks: directly bound columns are verified and
  filled on INSERT, guarded on UPDATE, and upserts guard the existing row
  (PostgreSQL `DO UPDATE ... WHERE`, MySQL `IF(...)` assignments);
  `INSERT ... SELECT` is limited to the copy-within-scope shape and rejects set
  operations. Reject scope values and mixed column bindings whose SQL types
  disagree. Validate the rendered shapes against PostgreSQL and MySQL in
  `integrationTest`.
- Read public descriptor fields of package-private table classes declared
  outside `titan.dsl` (`selectFrom` and filter policies).
- Parenthesize the extra predicate of `on(left, right, extra)` joins so an OR
  inside it no longer swallows the join equality.
- Update PostgreSQL JDBC to 42.7.13 and MySQL Connector/J to 26.7.0; constrain
  Commons Compress to 1.28.0 in codegen modules without adding DSL runtime dependencies.
- Align documented driver coordinates and missing-driver suggestions with the
  security-reviewed versions.
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
