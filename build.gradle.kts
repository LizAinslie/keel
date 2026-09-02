allprojects {
    group = providers.gradleProperty("group").get()
    version = providers.gradleProperty("version").get()
}

tasks.register("publishAllToMavenLocal") {
    group = "publishing"
    description = "Publish all library modules to Maven Local."
    dependsOn(subprojects.map { "${it.path}:publishToMavenLocal" })
}
