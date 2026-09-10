import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

abstract class KeelTypegenExtension {
    abstract val output: RegularFileProperty
    abstract val pagesName: Property<String>
    abstract val packages: ListProperty<String>
    abstract val format: Property<String>
    abstract val json: RegularFileProperty
}

val keelTypegen = extensions.create<KeelTypegenExtension>("keelTypegen")
keelTypegen.pagesName.convention("Pages")
keelTypegen.format.convention("ts")

pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
    val compileKotlin = tasks.named<KotlinCompile>("compileKotlin")
    tasks.register<JavaExec>("generateKeelTypes") {
        group = "build"
        description = "Generate TypeScript types from @KeelType and @KeelAction."
        dependsOn(compileKotlin)
        // runtimeClasspath of the source set includes processResources; that
        // cycles with pack builds. Compile output + dependency classpath only.
        classpath = files(
            compileKotlin.map { it.destinationDirectory },
            configurations.named("runtimeClasspath"),
        )
        mainClass.set("dev.kolektiv.keel.typegen.TypegenCliKt")
        argumentProviders.add(
            CommandLineArgumentProvider {
                require(keelTypegen.output.isPresent) {
                    "keelTypegen.output must be set (path to the generated .ts file)"
                }
                buildList {
                    add("--output")
                    add(keelTypegen.output.get().asFile.absolutePath)
                    add("--pages-name")
                    add(keelTypegen.pagesName.get())
                    add("--format")
                    add(keelTypegen.format.get())
                    if (keelTypegen.json.isPresent) {
                        add("--emit-json")
                        add(keelTypegen.json.get().asFile.absolutePath)
                    }
                    for (pkg in keelTypegen.packages.get()) {
                        add("--package")
                        add(pkg)
                    }
                }
            },
        )
        inputs.files(compileKotlin.map { it.outputs })
        inputs.property("pagesName", keelTypegen.pagesName)
        inputs.property("packages", keelTypegen.packages)
        inputs.property("format", keelTypegen.format)
        outputs.file(keelTypegen.output)
        outputs.files(
            provider {
                if (keelTypegen.json.isPresent) listOf(keelTypegen.json.get().asFile) else emptyList()
            },
        )
    }
}
