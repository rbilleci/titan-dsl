# Release preparation

Titan DSL is a standalone Java library. Its canonical
repository is <https://github.com/rbilleci/titan-dsl>. The current distribution
path is a source checkout or local Maven publication, not a configured public
artifact registry.

See the [public launch record](public-launch.md) for the tested source revision,
security audit, and initial source-release boundaries.

## Verify a candidate

1. Review the repository contents and history for secrets, private material, and
   third-party attribution. Confirm GPL-3.0-only metadata matches `LICENSE`.
2. Build from an isolated checkout with JDK 21 and an empty Gradle user home:
   `./gradlew clean build generatePomFileForMavenPublication --no-daemon`.
3. Run `./gradlew -p examples/quickstart run runFeatures --no-daemon`. No other
   repository or pre-existing Maven-local publication should be available.
   Also run `./gradlew -p examples/schema-codegen build run --no-daemon` and
   `./gradlew integrationTest --no-daemon` (the latter requires Docker).
4. Inspect `titan-dsl/build/libs/`: binary, source, and Javadoc JARs must contain
   `META-INF/LICENSE` and `META-INF/THIRD_PARTY_NOTICES.md`. The source JAR must
   include generated `SelectBuilder1..10` and `Tuple1..10` sources.
   Inspect the codegen/plugin subprojects' `build/libs/` for the same notices
   and their source/Javadoc artifacts.
5. Inspect `titan-dsl/build/publications/maven/pom-default.xml` for the correct coordinates,
   repository URL, GPLv3 license, and absence of third-party runtime dependencies.
   Generate the plugin and marker POMs with
   `./gradlew :titan-codegen-gradle-plugin:generatePomFileForPluginMavenPublication :titan-codegen-gradle-plugin:generatePomFileForTitanCodegenPluginMarkerMavenPublication`.
   Check the separate codegen/plugin POMs for their intentional build-tool
   dependencies and absence of any Titan transpiler dependency.
6. Record test results and update `CHANGELOG.md`. Choose an explicit release
   version in `gradle.properties`, updating documented/example dependency coordinates
   together. Keep the tag, release notes, and artifact version consistent.
7. Audit resolved runtime, test, annotation-processor, and buildscript dependencies
   against current advisories and refresh GitHub's manual dependency snapshot.
   Actions is intentionally disabled; the snapshot is not continuous CI.

For local publication only, run `./gradlew publishToMavenLocal`. Do not use
Maven-local contents as evidence of a clean external-consumer build.
The root project publishes no artifact; all three module coordinates and the
plugin marker remain unchanged. Build/publication conventions are maintained
once in `build-logic/`.

## Before public visibility or registry publication

- Complete the history/security and ownership review; current-tree cleanup alone
  does not remove historical material.
- Run and record the local verification commands on the candidate. GitHub Actions
  CI is disabled while the account has no runner funding; do not require absent
  Actions checks in branch protection. Configure private vulnerability reporting
  and an appropriate maintainer contact.
- Set the repository description and topics, and confirm all public links work.
- If publishing artifacts, select a registry, verify namespace ownership, and
  configure its required metadata, credentials, signing, and provenance. No
  remote publishing credentials or release workflow are configured here yet.
- Test a separate consumer against the exact staged artifacts and archive
  checksums and validation results with the release.

Changing repository visibility, creating a release/tag, and publishing artifacts
are separate release operations, not side effects of `build` or CI.
