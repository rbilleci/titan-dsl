# Public source launch — 2026-09-14

Titan DSL is a standalone, early-access GPL-3.0-only source release. Tested code
revision: `863f78a7575a435ec307dc39d90e1ad630053b7a`. Later launch-documentation
and ignore-rule commits do not change the tested code.

The build, plugin validation, PostgreSQL/MySQL integration suites, and Maven
publication metadata generation passed. Unit tests: 264 passed, one skipped;
integration tests: 69 passed, none skipped; no failures or errors. The skipped
unit test is an opt-in seeded replay entry point.

Fresh source-checkout validation with an initially empty Gradle user home also
passed the full build/integration suites, `examples/quickstart`'s `run runFeatures`,
and `examples/schema-codegen`'s `build run`, without Maven-local publications.

The library, codegen, and Gradle plugin binary/source/Javadoc JARs carry
GPL-3.0-only licensing and third-party notices. The `titan-dsl` artifact still
has no third-party runtime dependencies; codegen's build-tool dependencies are
kept separate. No Titan transpiler checkout is required to build or use this repo.

## Security checks

PostgreSQL JDBC is updated to 42.7.13, MySQL Connector/J to 26.7.0, and the
Testcontainers archive dependency is constrained to Commons Compress 1.28.0.
The MySQL driver version is separate from the tested MySQL 8.4 server version.
An OSV query found no matched advisories in the resolved Maven dependencies
audited for runtime, tests, annotation processors, and buildscript configurations.
This is a point-in-time check, not a guarantee of security; container images,
Gradle distributions, and other non-Maven software were outside that query.

Gitleaks 8.30.1 found no secrets in the cleaned reachable Git history, object
contents, or exported GitHub discussion text, including the five existing
dependency-update PR heads. Recovery archives and audit logs remain private and
outside the repository.

The resolved dependency graph was submitted directly to GitHub for Dependabot
alerts without Actions. Refresh this manual snapshot and repeat the local audit
when dependencies change; this does not provide continuous CI.

The repository is public with owner approval. Private vulnerability reporting,
Dependabot alerts, secret scanning, and secret-scanning push protection are
enabled. Protected `main` blocks force pushes/deletion and requires linear history
and resolved review conversations, including administrator enforcement. No CI
checks or additional reviewer are mandatory; normal maintainer pushes remain
possible. GitHub Actions is disabled and no release workflow was added.

## Distribution scope

Use the README's source-checkout examples. No artifact registry, release tag,
signing/provenance workflow, or stable support branch was published. See the
[release checklist](releasing.md) before a future artifact release.
