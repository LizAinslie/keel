package dev.kolektiv.keel.ktor

import dev.kolektiv.keel.manifest.KeelManifest
import dev.kolektiv.keel.page.PageRegistry
import dev.kolektiv.keel.theme.ThemeResolver
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import kotlinx.serialization.json.JsonObject
import java.nio.file.Path

fun interface SharedProvider {
    fun load(call: ApplicationCall, path: String, params: Map<String, String>, query: Parameters): JsonObject?
}

class PagesDsl @PublishedApi internal constructor(
    @PublishedApi internal val registry: PageRegistry,
    @PublishedApi internal val handlers: MutableMap<String, suspend PageRequest.() -> Any>,
) {
    inline fun <reified T : Any> page(
        id: String,
        path: String,
        noinline load: suspend PageRequest.() -> T,
    ) {
        registry.page<T>(id, path)
        handlers[id] = load
    }
}

class KeelConfig {
    var packDir: Path? = null
    var packUrlPrefix: String = "/__keel/pack"
    var defaultThemeId: String? = null
    var manifests: List<KeelManifest> = emptyList()
    var themeResolver: ThemeResolver? = null
    var bootstrap: String = "bootstrap.js"
    var title: String = "Keel"
    var shared: SharedProvider? = null
    var notFoundPageId: String? = null

    @PublishedApi
    internal val registry: PageRegistry = PageRegistry()
    @PublishedApi
    internal val handlers: MutableMap<String, suspend PageRequest.() -> Any> = linkedMapOf()

    fun pages(block: PagesDsl.() -> Unit) {
        PagesDsl(registry, handlers).block()
    }
}
