plugins {
    application
    id("io.titan.codegen")
}

repositories { mavenCentral() }
java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
dependencies {
    implementation("io.titan:titan-dsl:0.1.0")
    compileOnly("org.jspecify:jspecify:1.0.0")
}
application { mainClass.set("example.GeneratedCatalogQuery") }
titan {
    database {
        dialect.set("postgresql")
        schemas.set(listOf("app"))
        ddlDir.set("src/main/resources/db/schema")
        ddlMode.set("parser")
    }
    catalog { targetPackage.set("generated.catalog") }
}
