plugins { id("titan.java-conventions") }

publishing {
    publications {
        create<MavenPublication>("maven") { from(components["java"]) }
    }
}
