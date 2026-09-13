import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    java
    `maven-publish`
    id("net.ltgt.errorprone")
}

group = "io.titan"
version = providers.gradleProperty("titanVersion").get()

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    errorprone(catalog.findLibrary("errorprone-core").get())
    testImplementation(platform(catalog.findLibrary("junit-bom").get()))
    testImplementation(catalog.findLibrary("junit-jupiter").get())
    testImplementation(catalog.findLibrary("mockito-core").get())
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        excludedPaths.set(".*/build/generated/.*")
        disableWarningsInGeneratedCode.set(true)
        // Preserve the existing checker policy; generated code follows its templates.
        disable("VoidUsed", "PreferInstanceofOverGetKind", "StringCaseLocaleUsage",
            "StringSplitter", "ImmutableEnumChecker", "InjectOnConstructorOfAbstractClass")
    }
}

// Forward replay/golden-test flags into test JVMs, not just the Gradle daemon.
val testProperties = providers.systemPropertiesPrefixedBy("titan.").get()
tasks.withType<Test>().configureEach {
    testProperties.forEach { (key, value) -> systemProperty(key, value) }
}
tasks.named<Test>("test") { useJUnitPlatform { excludeTags("docker") } }

tasks.register<Test>("integrationTest") {
    description = "Runs Docker-backed integration tests locally."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("docker") }
    shouldRunAfter(tasks.named("test"))
}

tasks.withType<Jar>().configureEach {
    from(rootProject.files("LICENSE", "THIRD_PARTY_NOTICES.md")) { into("META-INF") }
    manifest {
        attributes("Implementation-Title" to project.name, "Implementation-Version" to project.version)
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set(provider { project.name })
            description.set(provider { project.description ?: project.name })
            url.set("https://github.com/rbilleci/titan-dsl")
            licenses {
                license {
                    name.set("GNU General Public License, version 3 only (GPL-3.0-only)")
                    url.set("https://www.gnu.org/licenses/gpl-3.0.html")
                    distribution.set("repo")
                }
            }
            scm {
                url.set("https://github.com/rbilleci/titan-dsl")
                connection.set("scm:git:https://github.com/rbilleci/titan-dsl.git")
                developerConnection.set("scm:git:ssh://git@github.com/rbilleci/titan-dsl.git")
            }
            issueManagement {
                system.set("GitHub")
                url.set("https://github.com/rbilleci/titan-dsl/issues")
            }
        }
    }
}
