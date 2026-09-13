plugins {
    id("titan.library-conventions")
}

description = "Schema introspection and typed Java catalog generation for Titan DSL."

dependencies {
    // titan-dsl is TEST-only here: the descriptor generators (MAIN) merely EMIT titan.dsl.* fully-
    // qualified names into generated source text and never compile against the DSL — but
    // GeneratedCatalogCompilesTest compiles AND loads the generated descriptors (extends
    // titan.dsl.Table) at test runtime, so the DSL must be on the test classpath.
    testImplementation(project(":titan-dsl"))
    // Container-backed DDL introspection (audit G-6/G-7): DDL mode applies the DDL files to a
    // scratch Testcontainers database and introspects it through the JDBC path. Only the core
    // artifact is needed — the scratch databases are driven as generic containers and JDBC
    // drivers are supplied by the caller (the Gradle plugin resolves them from 'titanJdbc').
    implementation(libs.testcontainers.core)

    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.mysql)
    testRuntimeOnly(libs.postgresql)
    // Keep the test driver aligned with the shared, security-reviewed version.
    testRuntimeOnly(libs.mysql.connector.j)
}
