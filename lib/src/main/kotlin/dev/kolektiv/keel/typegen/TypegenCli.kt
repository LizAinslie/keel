package dev.kolektiv.keel.typegen

import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText

/**
 * Gradle `JavaExec` entry point. Scans compile output for `@KeelType` and
 * `@KeelAction` and writes a TypeScript contract file.
 *
 * ```
 * TypegenCli --output pack/src/lib/page-types.ts --pages-name HarborPages --package dev.example.app
 * ```
 */
fun main(args: Array<String>) {
    var output: Path? = null
    var pagesName = "Pages"
    val packages = ArrayList<String>()
    var index = 0
    while (index < args.size) {
        when (val arg = args[index]) {
            "--output" -> output = Path.of(args[++index])
            "--pages-name" -> pagesName = args[++index]
            "--package" -> packages.add(args[++index])
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
        "usage: TypegenCli --output <file> [--pages-name Pages] [--package pkg]...",
    )
    val contract = ClasspathContract.scan(packages)
    out.createParentDirectories()
    out.writeText(Typegen.emit(contract, pagesName = pagesName))
    println("wrote $out")
}
