plugins {
    `maven-publish`
    signing
}

val febDisplayName = providers.gradleProperty("keel.displayName").orElse("Keel")
val febDescription = providers.gradleProperty("keel.description")
    .orElse("Host-owned routing and swappable frontend packs for Kotlin servers.")
val febUrl = providers.gradleProperty("keel.url").orElse("https://github.com/kolektiv/keel")
val febScm = providers.gradleProperty("keel.scm").orElse("scm:git:https://github.com/kolektiv/keel.git")
val febLicenseName = providers.gradleProperty("keel.licenseName").orElse("Apache-2.0")
val febLicenseUrl = providers.gradleProperty("keel.licenseUrl")
    .orElse("https://www.apache.org/licenses/LICENSE-2.0.txt")

publishing {
    publications {
        create<MavenPublication>("maven") {
            pom {
                name.set(febDisplayName.map { "$it (${project.name})" })
                description.set(febDescription)
                url.set(febUrl)
                licenses {
                    license {
                        name.set(febLicenseName)
                        url.set(febLicenseUrl)
                    }
                }
                developers {
                    developer {
                        id.set("kolektiv")
                        name.set("Kolektiv")
                        organization.set("Kolektiv")
                    }
                }
                scm {
                    url.set(febUrl)
                    connection.set(febScm)
                    developerConnection.set(febScm)
                }
            }
        }
    }

    repositories {
        mavenLocal()

        val yuriUser = providers.gradleProperty("keel.publishing.yuriCapitalRepoUsername")
            .orElse(providers.environmentVariable("YURI_CAPITAL_REPO_USERNAME"))
        val yuriPass = providers.gradleProperty("keel.publishing.yuriCapitalRepoPassword")
            .orElse(providers.environmentVariable("YURI_CAPITAL_REPO_PASSWORD"))
        if (yuriUser.isPresent && yuriPass.isPresent) {
            val user = yuriUser.get()
            val pass = yuriPass.get()
            maven {
                name = "keelMaven"
                url = uri("https://repo.yuri.capital/repository/keel-maven/")
                credentials {
                    username = user
                    password = pass
                }
            }
            val snapshot = project.version.toString().contains("SNAPSHOT", ignoreCase = true)
            maven {
                name = if (snapshot) "yuriSnapshots" else "yuriReleases"
                url = uri(
                    if (snapshot) "https://repo.yuri.capital/repository/maven-snapshots/"
                    else "https://repo.yuri.capital/repository/maven-releases/",
                )
                credentials {
                    username = user
                    password = pass
                }
            }
        }
    }
}

signing {
    val hasSigning = providers.gradleProperty("signing.keyId").isPresent
    isRequired = hasSigning && !project.version.toString().contains("SNAPSHOT", ignoreCase = true)
    if (hasSigning) {
        sign(publishing.publications)
    }
}

pluginManager.withPlugin("java") {
    publishing.publications.named<MavenPublication>("maven") {
        from(components["java"])
    }
}
