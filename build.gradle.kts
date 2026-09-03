allprojects {
    group = providers.gradleProperty("group").get()
    version = providers.gradleProperty("version").get()
}

tasks.register("publishAllToMavenLocal") {
    group = "publishing"
    description = "Publish library modules to Maven Local."
    dependsOn(":lib:publishToMavenLocal", ":ktor:publishToMavenLocal")
}
