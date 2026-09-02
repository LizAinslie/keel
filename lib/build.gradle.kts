plugins {
    id("keel.published-library")
}

publishing {
    publications.named<MavenPublication>("maven") {
        artifactId = "core"
    }
}

dependencies {
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
