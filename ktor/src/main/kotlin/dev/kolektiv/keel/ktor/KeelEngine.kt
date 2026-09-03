package dev.kolektiv.keel.ktor

import dev.kolektiv.keel.Keel
import dev.kolektiv.keel.KeelJson
import dev.kolektiv.keel.manifest.KeelManifest
import dev.kolektiv.keel.page.PageBinding
import dev.kolektiv.keel.page.PageRegistry
import dev.kolektiv.keel.seed.KeelSeed
import dev.kolektiv.keel.seed.KeelThemeRef
import dev.kolektiv.keel.theme.ChainThemeResolver
import dev.kolektiv.keel.theme.MissingPageInThemeException
import dev.kolektiv.keel.theme.ThemeRequest
import dev.kolektiv.keel.theme.ThemeResolver
import dev.kolektiv.keel.visit.KeelHeaders
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.formUrlEncode
import io.ktor.http.parseQueryString
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.http.content.staticFiles
import io.ktor.server.request.header
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import java.nio.file.Path

class KeelMissingPackException(dir: Path) : IllegalStateException(
    "Keel pack directory does not exist: $dir. Build the frontend pack first.",
)

class KeelUnknownHandlerException(id: String) : IllegalStateException("no loader registered for page '$id'")

internal class KeelEngine(private val config: KeelConfig) {
    private val packDir: Path = config.packDir
        ?: throw IllegalStateException("keel { packDir = ... } is required")
    private val manifests: List<KeelManifest> = config.manifests.ifEmpty { listOf(loadManifest(packDir)) }
    private val defaultThemeId: String = config.defaultThemeId ?: manifests.first().id
    private val resolver: ThemeResolver = config.themeResolver
        ?: ChainThemeResolver(manifests, defaultThemeId)
    private val packPrefix: String = config.packUrlPrefix.trimEnd('/')

    fun install(application: Application) {
        if (!packDir.toFile().isDirectory) {
            throw KeelMissingPackException(packDir)
        }
        application.routing {
            staticFiles(packPrefix, packDir.toFile())
            get(Keel.NAVIGATE_PATH) { respondTarget(call) }
            post(Keel.NAVIGATE_PATH) { respondTarget(call) }
            for (binding in config.registry.pages) {
                if (binding.path.startsWith("/__")) continue
                get(binding.path) { respondBinding(call, binding, call.request.path(), call.request.queryParameters) }
                post(binding.path) { respondBinding(call, binding, call.request.path(), call.request.queryParameters) }
            }
            get("{path...}") {
                val parts = call.parameters.getAll("path").orEmpty()
                val path = "/" + parts.joinToString("/")
                respondNotFound(call, path.ifEmpty { "/" }, call.request.queryParameters)
            }
        }
    }

    private suspend fun respondTarget(call: ApplicationCall) {
        val to = call.request.queryParameters["to"] ?: call.request.path()
        val (path, query) = splitTarget(to)
        val binding = config.registry.match(path)
        if (binding == null) {
            respondNotFound(call, path, query)
            return
        }
        respondBinding(call, binding, path, query)
    }

    private suspend fun respondBinding(
        call: ApplicationCall,
        binding: PageBinding,
        path: String,
        query: Parameters,
    ) {
        val params = PageRegistry.params(binding.path, path)
        try {
            val seed = buildSeed(call, binding, path, params, query)
            respondSeed(call, seed, HttpStatusCode.OK)
        } catch (missing: PageMissingException) {
            respondNotFound(call, missing.path, query)
        }
    }

    private suspend fun respondNotFound(call: ApplicationCall, path: String, query: Parameters) {
        val notFoundId = config.notFoundPageId ?: manifests.firstOrNull { it.notFound != null }?.let { manifest ->
            manifest.pages.entries.find { it.value.module == manifest.notFound }?.key
                ?: "not-found"
        }
        val binding = notFoundId?.let { id ->
            runCatching { config.registry.get(id) }.getOrNull()
        }
        if (binding == null) {
            call.respondText("Not found", ContentType.Text.Plain, HttpStatusCode.NotFound)
            return
        }
        val params = PageRegistry.params(binding.path, path).ifEmpty { mapOf("path" to path) }
        val seed = buildSeed(call, binding, path, params, query)
        respondSeed(call, seed, HttpStatusCode.NotFound)
    }

    private suspend fun buildSeed(
        call: ApplicationCall,
        binding: PageBinding,
        path: String,
        params: Map<String, String>,
        query: Parameters,
    ): KeelSeed {
        val handler = config.handlers[binding.id] ?: throw KeelUnknownHandlerException(binding.id)
        val request = PageRequest(call, binding, path, params, query)
        val data = request.handler()
        val override = call.request.header(KeelHeaders.THEME)
        val selection = resolver.resolve(
            ThemeRequest(
                pageId = binding.id,
                path = path,
                overrideId = override,
            ),
        )
        val entry = assetUrl(selection.entry.module)
        val css = selection.entry.css.map { assetUrl(it) }
        val querySuffix = query.formUrlEncode().let { if (it.isEmpty()) "" else "?$it" }
        return KeelSeed(
            page = binding.id,
            path = path + querySuffix,
            params = params,
            data = encodeData(binding.serializer, data),
            theme = KeelThemeRef(selection.manifest.id, selection.manifest.version),
            entry = entry,
            css = css,
            shared = config.shared?.load(call, path, params, query),
            host = selection.manifest.host,
            layout = selection.entry.layout,
        )
    }

    private suspend fun respondSeed(call: ApplicationCall, seed: KeelSeed, status: HttpStatusCode) {
        val raw = KeelJson.codec.encodeToString(KeelSeed.serializer(), seed)
        call.response.header(KeelHeaders.VERSION, seed.theme.version)
        call.response.header(KeelHeaders.THEME, seed.theme.id)
        if (isVisit(call)) {
            call.respondText(raw, ContentType.Application.Json, status)
            return
        }
        val json = HtmlDocument.encodeSeedJson(raw)
        val bootstrap = assetUrl(config.bootstrap)
        val html = HtmlDocument.render(config.title, json, seed, bootstrap)
        call.respondText(html, ContentType.Text.Html, status)
    }

    private fun assetUrl(module: String): String {
        if (module.startsWith("http://") || module.startsWith("https://") || module.startsWith("/")) {
            return module
        }
        return "$packPrefix/${module.trimStart('/')}"
    }

    companion object {
        fun loadManifest(packDir: Path): KeelManifest {
            val file = packDir.resolve("manifest.json").toFile()
            check(file.isFile) { "missing manifest.json in $packDir" }
            return KeelJson.codec.decodeFromString(KeelManifest.serializer(), file.readText())
        }

        fun isVisit(call: ApplicationCall): Boolean {
            if (call.request.header(KeelHeaders.VISIT).equals("true", ignoreCase = true)) return true
            return call.request.path() == Keel.NAVIGATE_PATH
        }

        fun splitTarget(to: String): Pair<String, Parameters> {
            val trimmed = to.trim()
            val q = trimmed.indexOf('?')
            val path = if (q >= 0) trimmed.substring(0, q) else trimmed
            val query = if (q >= 0) parseQueryString(trimmed.substring(q + 1)) else Parameters.Empty
            val normalized = if (path.isEmpty()) "/" else path
            return normalized to query
        }

        fun encodeData(serializer: KSerializer<*>, value: Any): JsonElement {
            @Suppress("UNCHECKED_CAST")
            return KeelJson.codec.encodeToJsonElement(serializer as KSerializer<Any>, value)
        }
    }
}

fun Application.keel(configure: KeelConfig.() -> Unit) {
    val config = KeelConfig().apply(configure)
    KeelEngine(config).install(this)
}
