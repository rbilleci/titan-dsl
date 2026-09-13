# Third-party notices

Project-owned Titan DSL, codegen, Gradle plugin code, documentation, examples, and generated DSL classes
are licensed under GPL-3.0-only; see `LICENSE`.

## Gradle wrapper

`gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar` are Gradle build
tooling, licensed separately under Apache License 2.0. Preserve their original
copyright and license notices. A copy of that license is provided in
`licenses/Apache-2.0.txt` in the source checkout. The wrapper is not part of the
published DSL library JAR.

## Resolved build and test dependencies

The DSL library declares no third-party runtime dependencies. Gradle resolves
JUnit, Mockito, ArchUnit, and Error Prone for build/test use; their versions are
listed in `gradle/libs.versions.toml`. Those artifacts retain their respective
licenses and are not bundled in the DSL library JAR. Review the resolved
dependency inventory again before distributing any bundled build environment.

The separate codegen artifact resolves Testcontainers for scratch-database DDL
introspection. Its transitive dependencies are not bundled into our JARs. JDBC
drivers are supplied by the generation consumer; PostgreSQL/MySQL drivers are
also used in integration tests. The separate plugin uses the Gradle API. These
dependencies retain their own licenses and do not enter the DSL runtime POM.

The DSL, codegen, and generation plugin are project-owned modules covered by
the same GPL-3.0-only license, without a separate licensing exception.
