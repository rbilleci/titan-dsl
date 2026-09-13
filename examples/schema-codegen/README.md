# Schema-first example

From the repository root:

```bash
./gradlew -p examples/schema-codegen build run
```

This standalone application uses only the enclosing Titan DSL checkout. The
`io.titan.codegen` plugin parses `users.sql`, generates Java descriptors and row
records, compiles the application, and checks the README's query and bind values.
It needs Java 21 and initial dependency downloads, but no Titan core checkout,
Maven-local artifacts, Docker, or live database.

For JDBC and container-backed DDL generation, see the
[schema walkthrough](../../README.md#generate-your-java-catalog-from-the-schema).
