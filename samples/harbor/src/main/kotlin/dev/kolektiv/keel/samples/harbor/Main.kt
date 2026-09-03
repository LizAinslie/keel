package dev.kolektiv.keel.samples.harbor

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.nio.file.Path

fun main(args: Array<String>) {
    val packDir = Path.of(
        args.getOrNull(0)
            ?: System.getProperty("keel.packDir")
            ?: error("pass the pack dist directory"),
    )
    val port = args.getOrNull(1)?.toIntOrNull() ?: 8090
    val host = args.getOrNull(2) ?: "0.0.0.0"
    println("Harbor listening on http://$host:$port")
    println("Pack $packDir")
    embeddedServer(Netty, port = port, host = host) {
        harbor(packDir)
    }.start(wait = true)
}
