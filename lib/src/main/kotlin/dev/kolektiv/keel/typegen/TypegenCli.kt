package dev.kolektiv.keel.typegen

import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText

/**
 * Offline classpath scanner used by Gradle `generateKeelTypes`.
 *
 * A running host also serves the same JSON at [dev.kolektiv.keel.Keel.SCHEMA_PATH]
 * (`GET /__keel/schema`). `keel-scaffold <origin> <dir>` fetches that document
 * and writes a blank pack. Prefer the live schema when a host is up.
 *
 * ```
 * TypegenCli --output pack/src/lib/page-types.ts --pages-name HarborPages --package dev.example.app
 * TypegenCli --output pack/src/lib/page-types.d.ts --format dts --emit-json pack/src/lib/page-types.json
 * ```
 */
fun main(args: Array<String>) {
    var output: Path? = null
    var pagesName = "Pages"
    var format = "ts"
    var emitJson: Path? = null
    val packages = ArrayList<String>()
    var index = 0
    while (index < args.size) {
        when (val arg = args[index]) {
            "--output" -> output = Path.of(args[++index])
            "--pages-name" -> pagesName = args[++index]
            "--package" -> packages.add(args[++index])
            "--format" -> format = args[++index]
            "--emit-json" -> emitJson = Path.of(args[++index])
            else -> {
                if (output == null) {
                    output = Path.of(arg)
                } else {
                    pagesName = arg
                }
            }
        }
        index += 1
    }
    val out = output ?: error(
        "usage: TypegenCli --output <file> [--format ts|dts] [--emit-json <file>] [--pages-name Pages] [--package pkg]...",
    )
    require(format == "ts" || format == "dts") { "unknown typegen format '$format' (expected ts|dts)" }
    val contract = ClasspathContract.scan(packages)
    out.createParentDirectories()
    out.writeText(Typegen.emit(contract, pagesName = pagesName))
    println("wrote $out")
    if (emitJson != null) {
        emitJson.createParentDirectories()
        emitJson.writeText(Typegen.emitJson(contract, pagesName) + "\n")
        println("wrote $emitJson")
    }
}
