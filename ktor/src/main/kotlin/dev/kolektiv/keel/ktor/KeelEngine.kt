package dev.kolektiv.keel.ktor

import dev.kolektiv.keel.Keel
import dev.kolektiv.keel.KeelJson
import dev.kolektiv.keel.bundle.FrontendBundle
import dev.kolektiv.keel.page.PageBinding
import dev.kolektiv.keel.page.PageRegistry
import dev.kolektiv.keel.seed.KeelSeed
import dev.kolektiv.keel.seed.KeelThemeRef
import dev.kolektiv.keel.seed.PageHead
import dev.kolektiv.keel.theme.ChainThemeResolver
import dev.kolektiv.keel.theme.MissingPageInThemeException
import dev.kolektiv.keel.theme.ThemeRequest
import dev.kolektiv.keel.visit.KeelHeaders
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.defaultForFilePath
import io.ktor.http.formUrlEncode
import io.ktor.http.parseQueryString
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.request.header
import io.ktor.server.request.path
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingResolveContext
import io.ktor.server.routing.application
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class KeelMissingBundleException : IllegalStateException(
    "keel { bundle = ... } is required when using the pages DSL or respondPage without a bundle",
)

class KeelUnknownHandlerException(id: String) : IllegalStateException("no loader registered for page '$id'")

@PublishedApi
internal class KeelEngine(private val config: KeelConfig) {
    private val packPrefix: String = config.packUrlPrefix.trimEnd('/')
    private val bundleOrder = CopyOnWriteArrayList<FrontendBundle>()
    private val bundlesById = ConcurrentHashMap<String, FrontendBundle>()

    init {
        for (bundle in config.configuredBundles()) {
            registerBundle(bundle)
        }
        if (config.registry.pages.isNotEmpty() && bundleOrder.isEmpty()) {
            throw KeelMissingBundleException()
        }
    }

    fun registerBundle(bundle: FrontendBundle) {
        if (bundlesById.putIfAbsent(bundle.id, bundle) == null) {
            bundleOrder.add(bundle)
        }
    }

    fun install(application: Application) {
        application.routing {
            get("$packPrefix/{bundleId}/{entry...}") { serveAsset(call) }
            post("${Keel.ACTION_PATH}/{id}") { respondAction(call) }
            if (config.registry.pages.isNotEmpty()) {
                get(Keel.NAVIGATE_PATH) { respondTarget(call) }
                post(Keel.NAVIGATE_PATH) { respondTarget(call) }
                for (binding in config.registry.pages) {
                    if (binding.path.startsWith("/__")) continue
                    get(binding.path) {
                        respondBinding(call, binding, call.request.path(), call.request.queryParameters)
                    }
                    post(binding.path) {
                        respondBinding(call, binding, call.request.path(), call.request.queryParameters)
                    }
                }
                get("{path...}") {
                    val parts = call.parameters.getAll("path").orEmpty()
                    val path = "/" + parts.joinToString("/")
                    respondNotFound(call, path.ifEmpty { "/" }, call.request.queryParameters)
                }
            }
        }
    }

    fun bundleFor(call: ApplicationCall, pageId: String, path: String): FrontendBundle {
        call.attributes.getOrNull(KeelRouteBundleKey)?.let { routed ->
            if (routed.manifest.implements(pageId)) return routed
        }
        if (bundleOrder.isEmpty()) throw KeelMissingBundleException()
        val override = call.request.header(KeelHeaders.THEME)
        val resolver = config.themeResolver
            ?: ChainThemeResolver(bundleOrder.map { it.manifest }, defaultThemeId())
        val selection = resolver.resolve(
            ThemeRequest(pageId = pageId, path = path, overrideId = override),
        )
        return bundlesById[selection.manifest.id]
            ?: throw MissingPageInThemeException(pageId, selection.manifest.id)
    }

    suspend fun respond(
        call: ApplicationCall,
        bundle: FrontendBundle,
        pageId: String,
        data: Any,
        serializer: KSerializer<*>,
        params: Map<String, String>,
        status: HttpStatusCode,
        path: String? = null,
        query: Parameters? = null,
        errors: Map<String, List<String>> = emptyMap(),
        redirect: String? = null,
        head: PageHead? = null,
    ) {
        val impl = bundle.page(pageId)
        val resolvedPath = path ?: call.request.path()
        val resolvedQuery = query ?: call.request.queryParameters
        val querySuffix = resolvedQuery.formUrlEncode().let { if (it.isEmpty()) "" else "?$it" }
        val seed = KeelSeed(
            page = pageId,
            path = resolvedPath + querySuffix,
            params = params,
            data = if (redirect != null) KeelSeed.emptyData() else encodeData(serializer, data),
            errors = errors,
            theme = KeelThemeRef(bundle.id, bundle.version),
            entry = assetUrl(bundle, impl.module),
            css = impl.css.map { assetUrl(bundle, it) },
            shared = config.shared?.load(call, resolvedPath, params, resolvedQuery),
            host = bundle.manifest.host,
            layout = impl.layout,
            redirect = redirect,
            head = head,
        )
        respondSeed(call, seed, status, bundle)
    }

    private suspend fun serveAsset(call: ApplicationCall) {
        val bundleId = call.parameters["bundleId"] ?: run {
            call.respond(HttpStatusCode.NotFound)
            return
        }
        val entry = call.parameters.getAll("entry")?.joinToString("/").orEmpty()
        val bundle = bundlesById[bundleId]
        if (bundle == null || entry.isEmpty() || !bundle.contains(entry)) {
            call.respond(HttpStatusCode.NotFound)
            return
        }
        val type = ContentType.defaultForFilePath(entry)
        val bytes = withContext(Dispatchers.IO) {
            bundle.openEntry(entry).use { it.readBytes() }
        }
        call.respondBytes(bytes, type)
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
        val request = PageRequest(call, binding, path, params, query)
        try {
            val handler = config.handlers[binding.id] ?: throw KeelUnknownHandlerException(binding.id)
            val data = request.handler()
            val bundle = bundleFor(call, binding.id, path)
            respond(call, bundle, binding.id, data, binding.serializer, params, HttpStatusCode.OK, path, query, head = request.head)
        } catch (invalid: PageValidationException) {
            val bundle = bundleFor(call, binding.id, path)
            respond(
                call,
                bundle,
                binding.id,
                invalid.data,
                binding.serializer,
                params,
                HttpStatusCode.UnprocessableEntity,
                path,
                query,
                errors = invalid.errors,
                head = request.head,
            )
        } catch (redirect: PageRedirectException) {
            val bundle = bundleFor(call, binding.id, path)
            respond(
                call,
                bundle,
                binding.id,
                Unit,
                binding.serializer,
                params,
                HttpStatusCode.OK,
                path,
                query,
                redirect = redirect.to,
                head = request.head,
            )
        } catch (missing: PageMissingException) {
            respondNotFound(call, missing.path, query)
        }
    }

    private suspend fun respondNotFound(call: ApplicationCall, path: String, query: Parameters) {
        val notFoundId = config.notFoundPageId ?: bundleOrder.firstOrNull { it.manifest.notFound != null }?.let { bundle ->
            bundle.manifest.pages.entries.find { it.value.module == bundle.manifest.notFound }?.key
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
        val handler = config.handlers[binding.id] ?: throw KeelUnknownHandlerException(binding.id)
        val request = PageRequest(call, binding, path, params, query)
        val data = request.handler()
        val bundle = bundleFor(call, binding.id, path)
        respond(call, bundle, binding.id, data, binding.serializer, params, HttpStatusCode.NotFound, path, query, head = request.head)
    }

    private suspend fun respondAction(call: ApplicationCall) {
        val id = call.parameters["id"] ?: run {
            call.respond(HttpStatusCode.NotFound)
            return
        }
        val binding = runCatching { config.actionRegistry.get(id) }.getOrNull()
        val handler = config.actionHandlers[id]
        if (binding == null || handler == null) {
            call.respond(HttpStatusCode.NotFound)
            return
        }
        try {
            val text = call.receiveText()
            val raw = if (text.isBlank()) "{}" else text
            @Suppress("UNCHECKED_CAST")
            val input = KeelJson.codec.decodeFromString(binding.input as KSerializer<Any>, raw)
            val request = ActionRequest(call, text)
            val output = ActionRequest.with(request) { request.handler(input) }
            @Suppress("UNCHECKED_CAST")
            val data = encodeData(binding.output as KSerializer<*>, output)
            val payload = JsonObject(mapOf("data" to data))
            call.respondText(
                KeelJson.codec.encodeToString(JsonObject.serializer(), payload),
                ContentType.Application.Json,
                HttpStatusCode.OK,
            )
        } catch (invalid: PageValidationException) {
            val errors = JsonObject(
                invalid.errors.mapValues { (_, messages) ->
                    JsonArray(messages.map { JsonPrimitive(it) })
                },
            )
            val payload = JsonObject(mapOf("errors" to errors))
            call.respondText(
                KeelJson.codec.encodeToString(JsonObject.serializer(), payload),
                ContentType.Application.Json,
                HttpStatusCode.UnprocessableEntity,
            )
        }
    }

    private suspend fun respondSeed(
        call: ApplicationCall,
        seed: KeelSeed,
        status: HttpStatusCode,
        bundle: FrontendBundle,
    ) {
        val raw = KeelJson.codec.encodeToString(KeelSeed.serializer(), seed)
        call.response.header(KeelHeaders.VERSION, seed.theme.version)
        call.response.header(KeelHeaders.THEME, seed.theme.id)
        if (isVisit(call)) {
            call.respondText(raw, ContentType.Application.Json, status)
            return
        }
        val json = HtmlDocument.encodeSeedJson(raw)
        val bootstrap = assetUrl(bundle, config.bootstrap)
        val html = HtmlDocument.render(config.title, json, seed, bootstrap)
        call.respondText(html, ContentType.Text.Html, status)
    }

    private fun assetUrl(bundle: FrontendBundle, module: String): String {
        if (module.startsWith("http://") || module.startsWith("https://") || module.startsWith("/")) {
            return module
        }
        return "$packPrefix/${bundle.id}/${module.trimStart('/')}"
    }

    private fun defaultThemeId(): String =
        config.defaultThemeId ?: bundleOrder.firstOrNull()?.id ?: throw KeelMissingBundleException()

    companion object {
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
    val engine = KeelEngine(config)
    attributes.put(KeelEngineKey, engine)
    engine.install(this)
}

private class KeelRouteBundleConfig {
    lateinit var bundle: FrontendBundle
}

private class KeelBundleRouteSelector(private val bundleId: String) : RouteSelector() {
    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation =
        RouteSelectorEvaluation.Transparent

    override fun toString(): String = "(keel:$bundleId)"
}

fun Route.keel(bundle: FrontendBundle, configure: Route.() -> Unit) {
    val app = application
    val engine = app.attributes.getOrNull(KeelEngineKey) ?: run {
        val created = KeelEngine(KeelConfig().apply { this.bundle = bundle })
        app.attributes.put(KeelEngineKey, created)
        created.install(app)
        created
    }
    engine.registerBundle(bundle)
    val scoped = createChild(KeelBundleRouteSelector(bundle.id))
    val plugin = createRouteScopedPlugin(
        name = "KeelRouteBundle:${bundle.id}",
        createConfiguration = ::KeelRouteBundleConfig,
    ) {
        val bound = pluginConfig.bundle
        onCall { call ->
            call.attributes.put(KeelRouteBundleKey, bound)
        }
    }
    scoped.install(plugin) {
        this.bundle = bundle
    }
    scoped.configure()
}
