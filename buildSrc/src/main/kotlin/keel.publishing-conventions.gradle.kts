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

        val ossrhUser = providers.gradleProperty("keel.ossrhUsername")
        val ossrhPass = providers.gradleProperty("keel.ossrhPassword")
        if (ossrhUser.isPresent && ossrhPass.isPresent) {
            maven {
                name = "OSSRH"
                url = uri(
                    if (project.version.toString().endsWith("SNAPSHOT")) {
                        "https://s01.oss.sonatype.org/content/repositories/snapshots/"
                    } else {
                        "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
                    },
                )
                credentials {
                    username = ossrhUser.get()
                    password = ossrhPass.get()
                }
            }
        }

        val ghUser = providers.gradleProperty("keel.githubPackagesUsername")
        val ghToken = providers.gradleProperty("keel.githubPackagesToken")
        if (ghUser.isPresent && ghToken.isPresent) {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/kolektiv/keel")
                credentials {
                    username = ghUser.get()
                    password = ghToken.get()
                }
            }
        }
    }
}

signing {
    val hasSigning = providers.gradleProperty("signing.keyId").isPresent
    isRequired = hasSigning && !project.version.toString().endsWith("SNAPSHOT")
    if (hasSigning) {
        sign(publishing.publications)
    }
}

pluginManager.withPlugin("java") {
    publishing.publications.named<MavenPublication>("maven") {
        from(components["java"])
    }
}
