plugins {
    id("keel.kotlin-conventions")
    kotlin("plugin.serialization")
    application
}

application {
    mainClass.set("dev.kolektiv.keel.samples.harbor.MainKt")
}

val packDir = layout.projectDirectory.dir("pack/dist")

val buildPack by tasks.registering(Exec::class) {
    group = "build"
    description = "Bundle the Harbor Svelte pack."
    workingDir = rootProject.projectDir
    commandLine("pnpm", "--filter", "@kolektiv/harbor-pack", "build")
    inputs.dir(layout.projectDirectory.dir("pack/src"))
    outputs.dir(packDir)
}

tasks.named<JavaExec>("run") {
    dependsOn(buildPack)
    args(packDir.asFile.absolutePath, "8090")
}

dependencies {
    implementation(project(":ktor"))
    implementation(libs.ktor.server.netty)
    implementation(libs.slf4j.simple)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
