package dev.kolektiv.keel.samples.harbor

import dev.kolektiv.keel.bundle.FrontendBundle
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.nio.file.Path

fun main(args: Array<String>) {
    val rest = args.toMutableList()
    val packOverride = rest.firstOrNull()?.let { Path.of(it) }?.takeIf { it.toFile().exists() }
        ?.also { rest.removeFirst() }
    val port = rest.removeFirstOrNull()?.toIntOrNull() ?: 8090
    val host = rest.removeFirstOrNull() ?: "0.0.0.0"
    val bundle = loadBundle(packOverride)
    if (packOverride != null) {
        println("Watching pack ${packOverride.toAbsolutePath()} for changes")
    }
    println("Harbor listening on http://$host:$port")
    println("Pack ${bundle.id}@${bundle.version}")
    embeddedServer(Netty, port = port, host = host) {
        harbor(bundle)
    }.start(wait = true)
}

private fun loadBundle(override: Path?): FrontendBundle {
    if (override == null) return FrontendBundle.fromResource("keel/harbor.feb")
    return if (override.toFile().isDirectory) {
        FrontendBundle.fromDirectory(override)
    } else {
        FrontendBundle.fromFile(override)
    }
}
