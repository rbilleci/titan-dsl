# Standalone quickstart

This is a separate Gradle application consuming `io.titan:titan-dsl:0.1.0` through
an included source build. Only this repository and JDK 21 are required; the first
build needs internet access to download build tooling.

From the repository root:

```bash
./gradlew -p examples/quickstart run
```

On Windows, use `gradlew.bat`. Expected application output:

```text
POSTGRESQL
SELECT id, name FROM app.users WHERE name = ?
[Ada]
MYSQL
SELECT id, name FROM app.users WHERE name = ?
[Ada]
```

The application validates the SQL and bind values for both renderers and exits
with an error if they differ. No SQL is executed. `app` is an illustrative schema
or database name, not a connection configuration.

## Feature recipes

Run the feature examples from the repository root:

```bash
./gradlew -p examples/quickstart runFeatures
```

[FeatureExamples.java](src/main/java/example/FeatureExamples.java) exercises
optional filters/pagination, aliased self-joins, aggregation/HAVING, upserts,
CTEs, window functions, IN subqueries, CASE, batch inserts, updates, and deletes.
It also demonstrates the literal-SQL boundary inside CTE bodies. Each recipe
checks exact SQL and ordered bind values for both PostgreSQL and MySQL before
printing them. A mismatch fails the process; run this task before submitting changes.

The source includes an optional `findUserNames(DSLContext, Connection, String)` JDBC recipe.
It is compiled but not invoked by the demo; execution needs your own connection
and an existing `app.users` table. Read the
[README](../../README.md#contents) for complete usage and boundaries.

To adapt this for an existing application, change `includeBuild("../..")` in
`settings.gradle.kts` to the path of your DSL checkout. The dependency declaration
in `build.gradle.kts` stays the same. To use a locally published JAR instead,
remove the included build, add `repositories { mavenLocal() }`, and first run
`./gradlew publishToMavenLocal` from the DSL repository.
