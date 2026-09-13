plugins {
    `java-gradle-plugin`
    id("titan.java-conventions")
}

description = "Standalone schema introspection and Java catalog generation for Titan DSL."

gradlePlugin {
    plugins {
        create("titanCodegen") {
            id = "io.titan.codegen"
            implementationClass = "io.titan.gradle.TitanCodegenPlugin"
            displayName = "Titan schema code generation"
            description = "Generate typed Java catalogs from JDBC schemas or DDL, without the Titan transpiler."
        }
    }
}

dependencies {
    implementation(project(":titan-codegen"))
    testImplementation(project(":titan-dsl"))
    testImplementation(gradleTestKit())
    testImplementation(libs.postgresql)
    testImplementation(libs.mysql.connector.j)
}
