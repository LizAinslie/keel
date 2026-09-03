plugins {
    id("keel.published-library")
}

publishing {
    publications.named<MavenPublication>("maven") {
        artifactId = "ktor"
    }
}

dependencies {
    api(project(":lib"))
    api(libs.ktor.server.core)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
