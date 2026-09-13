plugins {
    application
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation("io.titan:titan-dsl:0.1.0")
}

application {
    mainClass.set("example.Quickstart")
}

tasks.register<JavaExec>("runFeatures") {
    group = "application"
    description = "Runs and checks the README and developer-guide SQL recipes for both dialects."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("example.FeatureExamples")
}
