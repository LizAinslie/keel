plugins {
    id("keel.kotlin-conventions")
    id("keel.typegen")
    kotlin("plugin.serialization")
    application
}

keelTypegen {
    output.set(layout.projectDirectory.file("pack/src/lib/page-types.ts"))
    json.set(layout.projectDirectory.file("pack/src/lib/page-types.json"))
    pagesName.set("HarborPages")
    packages.add("dev.kolektiv.keel.samples.harbor")
}

application {
    mainClass.set("dev.kolektiv.keel.samples.harbor.MainKt")
}

val packDir = layout.projectDirectory.dir("pack/dist")

val buildPack by tasks.registering(Exec::class) {
    group = "build"
    description = "Bundle the Harbor Svelte pack into dist/harbor.feb."
    dependsOn("generateKeelTypes")
    workingDir = rootProject.projectDir
    commandLine("pnpm", "--filter", "@kolektiv/harbor-pack...", "build")
    inputs.dir(layout.projectDirectory.dir("pack/src"))
    outputs.dir(packDir)
}

tasks.named<Copy>("processResources") {
    dependsOn(buildPack)
    from(packDir) {
        include("harbor.feb")
        into("keel")
    }
}

tasks.named<JavaExec>("run") {
    dependsOn(buildPack)
}

tasks.named("jar") {
    dependsOn(buildPack)
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
