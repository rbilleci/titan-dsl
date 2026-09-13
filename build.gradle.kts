plugins { base }

// This root coordinates the product; only the three peer modules publish artifacts.
val modules = listOf(":titan-dsl", ":titan-codegen", ":titan-codegen-gradle-plugin")
for (lifecycle in listOf("assemble", "check", "build", "clean")) {
    tasks.named(lifecycle) {
        dependsOn(modules.map { "$it:$lifecycle" })
    }
}
