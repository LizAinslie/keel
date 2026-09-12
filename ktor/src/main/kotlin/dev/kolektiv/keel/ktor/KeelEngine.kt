package dev.kolektiv.keel.ktor

import dev.kolektiv.keel.Keel
import dev.kolektiv.keel.KeelJson
import dev.kolektiv.keel.bundle.BundleOrigin
import dev.kolektiv.keel.bundle.FrontendBundle
import dev.kolektiv.keel.bundle.UnknownPageInBundleException
import dev.kolektiv.keel.page.PageBinding
import dev.kolektiv.keel.page.PageMethod
import dev.kolektiv.keel.page.PageRegistry
import dev.kolektiv.keel.security.CsrfRequest
import dev.kolektiv.keel.security.CsrfVerdict
import dev.kolektiv.keel.security.SameOriginCsrfPolicy
import dev.kolektiv.keel.seed.DocumentHead
import dev.kolektiv.keel.seed.KeelSeed
import dev.kolektiv.keel.seed.KeelThemeRef
import dev.kolektiv.keel.seed.PageHead
import dev.kolektiv.keel.seed.SeedFilter
import dev.kolektiv.keel.typegen.Typegen
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
import io.ktor.server.application.log
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
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
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.slf4j.Logger
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class KeelMissingBundleException : IllegalStateException(
    "keel { bundle = ... } is required when using the pages DSL or respondPage without a bundle",
)

class KeelUnknownHandlerException(id: String) : IllegalStateException("no loader registered for page '$id'")

/** No pack is available; the call site must supply one. */
class MissingPackException : IllegalStateException(
    "no pack to render with: pass a pack to respondPage(pack, ...) or scope the route with route.keel(pack) { ... }",
)

/** Several packs are installed and none was scoped for this route. */
class AmbiguousPackException(ids: List<String>) : IllegalStateException(
    "multiple packs configured (${ids.joinToString(", ")}): " +
        "scope the route with route.keel(pack) { ... } or pass a pack to respondPage(pack, ...)",
)

@PublishedApi
internal class KeelEngine(private val config: KeelConfig) {
    private val packPrefix: String = config.packUrlPrefix.trimEnd('/')
    private val bundleOrder = CopyOnWriteArrayList<FrontendBundle>()
    private val configuredOrder = CopyOnWriteArrayList<FrontendBundle>()
    private val bundlesById = ConcurrentHashMap<String, FrontendBundle>()

    private val swapLock = Any()
    private val reloadMutex = Mutex()
    private val watchFingerprints = ConcurrentHashMap<String, BundleFingerprint>()
    private val watchFailures = ConcurrentHashMap<String, Unit>()
    private var logger: Logger? = null

    init {
        for (bundle in config.configuredBundles()) {
            registerBundle(bundle)
            configuredOrder.add(bundle)
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
            get(Keel.SCHEMA_PATH) { respondSchema(call) }
            post("${Keel.ACTION_PATH}/{id}") { respondAction(call) }
            if (config.registry.pages.isNotEmpty()) {
                get(Keel.NAVIGATE_PATH) { respondTarget(call) }
                post(Keel.NAVIGATE_PATH) { respondTarget(call) }
                put(Keel.NAVIGATE_PATH) { respondTarget(call) }
                patch(Keel.NAVIGATE_PATH) { respondTarget(call) }
                delete(Keel.NAVIGATE_PATH) { respondTarget(call) }
                for (binding in config.registry.pages) {
                    if (binding.path.startsWith("/__")) continue
                    bindPage(binding)
                }
                get("{path...}") {
                    val parts = call.parameters.getAll("path").orEmpty()
                    val path = "/" + parts.joinToString("/")
                    respondNotFound(call, path.ifEmpty { "/" }, call.request.queryParameters)
                }
            }
        }
        startPackWatcher(application)
    }

    /**
     * Pick the pack for a page from the call site, never from a visitor header:
     *
     * 1. a route-scoped pack (`route.keel(pack)`) wins when it implements the page;
     * 2. otherwise exactly one configured pack is used — a pack that does not
     *    implement the page throws [UnknownPageInBundleException];
     * 3. no pack at all throws [MissingPackException], several configured packs
     *    without a scoped route throw [AmbiguousPackException]. Both tell the
     *    host to pass the pack explicitly or scope the route.
     */
    fun bundleFor(call: ApplicationCall, pageId: String): FrontendBundle {
        val routed = call.attributes.getOrNull(KeelRouteBundleKey)
        if (routed != null) {
            val live = bundlesById[routed.id] ?: routed
            if (live.manifest.page(pageId) != null) return live
        }
        val configured = liveBundles()
        if (configured.size == 1) {
            val bundle = configured.single()
            if (bundle.manifest.page(pageId) == null) {
                throw UnknownPageInBundleException(pageId, bundle.id)
            }
            return bundle
        }
        if (configured.isEmpty()) throw MissingPackException()
        throw AmbiguousPackException(configured.map { it.id })
    }

    /** Configured bundles resolved through the live registry so reloads win. */
    private fun liveBundles(): List<FrontendBundle> = configuredOrder.toList()

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
        val draft = KeelSeed(
            page = pageId,
            path = resolvedPath + querySuffix,
            params = params,
            data = if (redirect != null) KeelSeed.emptyData() else encodeData(serializer, data),
            errors = errors,
            theme = KeelThemeRef(bundle.id, bundle.version),
            entry = assetUrl(bundle, impl.module),
            css = impl.css.map { assetUrl(bundle, it) },
            build = bundle.contentHash,
            shared = config.shared?.load(call, resolvedPath, params, resolvedQuery),
            host = bundle.manifest.host,
            layout = impl.layout,
            redirect = redirect,
        )
        val visit = isVisit(call)
        val nonce = if (visit) null else CspPolicy.generateNonce()
        val seed = draft.copy(
            head = DocumentHead.resolve(
                packHtml = impl.head,
                host = head,
                seed = draft,
                documentUrl = documentUrl(call, resolvedPath),
                nonce = nonce,
            ),
        )
        val filtered = if (visit) applyPartial(call, seed) else seed
        respondSeed(call, filtered, status, bundle, nonce)
    }

    private fun applyPartial(call: ApplicationCall, seed: KeelSeed): KeelSeed {
        val only = parseHeaderSet(call.request.header(KeelHeaders.ONLY))
        val except = parseHeaderSet(call.request.header(KeelHeaders.EXCEPT))
        if (only.isEmpty() && except.isEmpty()) return seed
        val data = SeedFilter.filterData(seed.data, only, except)
        val retained = (data as? JsonObject)?.keys?.joinToString(",") ?: ""
        call.response.header(KeelHeaders.PARTIAL, retained)
        return seed.copy(data = data)
    }

    private fun io.ktor.server.routing.Route.bindPage(binding: PageBinding) {
        for (method in binding.methods) {
            when (method) {
                PageMethod.GET -> get(binding.path) {
                    respondBinding(call, binding, call.request.path(), call.request.queryParameters)
                }
                PageMethod.POST -> post(binding.path) {
                    respondBinding(call, binding, call.request.path(), call.request.queryParameters)
                }
                PageMethod.PUT -> put(binding.path) {
                    respondBinding(call, binding, call.request.path(), call.request.queryParameters)
                }
                PageMethod.PATCH -> patch(binding.path) {
                    respondBinding(call, binding, call.request.path(), call.request.queryParameters)
                }
                PageMethod.DELETE -> delete(binding.path) {
                    respondBinding(call, binding, call.request.path(), call.request.queryParameters)
                }
            }
        }
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
        val etag = bundle.etagFor(entry)
        val cacheControl = if (isImmutableAsset(entry)) {
            "public, max-age=31536000, immutable"
        } else {
            "public, max-age=0, must-revalidate"
        }
        call.response.header("ETag", etag)
        call.response.header("Cache-Control", cacheControl)
        if (etagMatches(call.request.header("If-None-Match"), etag)) {
            call.respond(HttpStatusCode.NotModified)
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
        val method = PageMethod.from(call.request.httpMethod.value)
        if (method == null || method !in binding.methods) {
            call.respond(HttpStatusCode.MethodNotAllowed)
            return
        }
        if (!guardCsrf(call)) return
        runLoader(call, binding, path, query, HttpStatusCode.OK, recurseMissing = true)
    }

    private suspend fun respondNotFound(call: ApplicationCall, path: String, query: Parameters) {
        val notFoundId = config.notFoundPageId ?: notFoundPageId(call)
        val binding = notFoundId?.let { id ->
            runCatching { config.registry.get(id) }.getOrNull()
        }
        if (binding == null) {
            call.respondText("Not found", ContentType.Text.Plain, HttpStatusCode.NotFound)
            return
        }
        runLoader(call, binding, path, query, HttpStatusCode.NotFound, recurseMissing = false)
    }

    /**
     * The route-scoped pack owns `notFound` when it declares one; otherwise
     * installed packs are scanned in registration order. Keel never picks a
     * pack here, it only maps a pack's declared module back to a host page id.
     */
    private fun notFoundPageId(call: ApplicationCall): String? {
        val routed = call.attributes.getOrNull(KeelRouteBundleKey)
        val ordered = if (routed == null) {
            bundleOrder
        } else {
            listOf(routed) + bundleOrder.filter { it.id != routed.id }
        }
        for (bundle in ordered) {
            val module = bundle.manifest.notFound ?: continue
            return bundle.manifest.pages.entries.find { it.value.module == module }?.key ?: "not-found"
        }
        return null
    }

    private suspend fun runLoader(
        call: ApplicationCall,
        binding: PageBinding,
        path: String,
        query: Parameters,
        status: HttpStatusCode,
        recurseMissing: Boolean,
    ) {
        val params = PageRegistry.params(binding.path, path).ifEmpty {
            if (status == HttpStatusCode.NotFound) mapOf("path" to path) else emptyMap()
        }
        val request = PageRequest(call, binding, path, params, query)
        try {
            val handler = config.handlers[binding.id] ?: throw KeelUnknownHandlerException(binding.id)
            val data = request.handler()
            val bundle = bundleFor(call, binding.id)
            respond(call, bundle, binding.id, data, binding.serializer, params, status, path, query, head = request.head)
        } catch (invalid: PageValidationException) {
            val bundle = bundleFor(call, binding.id)
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
            val bundle = bundleFor(call, binding.id)
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
            if (!recurseMissing) {
                call.respondText("Not found", ContentType.Text.Plain, HttpStatusCode.NotFound)
                return
            }
            respondNotFound(call, missing.path, query)
        }
    }

    private suspend fun respondSchema(call: ApplicationCall) {
        val raw = Typegen.emitJson(config.registry, config.actionRegistry)
        call.respondText(raw, ContentType.Application.Json, HttpStatusCode.OK)
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
        if (!guardCsrf(call)) return
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
        nonce: String? = null,
    ) {
        val raw = KeelJson.codec.encodeToString(KeelSeed.serializer(), seed)
        call.response.header(KeelHeaders.VERSION, seed.theme.version)
        call.response.header(KeelHeaders.THEME, seed.theme.id)
        call.response.header(KeelHeaders.BUILD, seed.build)
        if (isVisit(call)) {
            call.respondText(raw, ContentType.Application.Json, status)
            return
        }
        val csp = config.csp
        if (csp != null && nonce != null) {
            call.response.header("Content-Security-Policy", csp.header(nonce))
        }
        val json = HtmlDocument.encodeSeedJson(raw)
        val bootstrap = assetUrl(bundle, config.bootstrap)
        val html = HtmlDocument.render(config.title, json, seed, bootstrap, nonce)
        call.respondText(html, ContentType.Text.Html, status)
    }

    private fun assetUrl(bundle: FrontendBundle, module: String): String {
        if (module.startsWith("http://") || module.startsWith("https://") || module.startsWith("/")) {
            return module
        }
        return "$packPrefix/${bundle.id}/${module.trimStart('/')}"
    }

    // --- pack hot reload -------------------------------------------------

    private fun startPackWatcher(application: Application) {
        if (!config.watchPacks) return
        logger = application.log
        snapshotWatchFingerprints()
        application.launch {
            while (isActive) {
                delay(config.packWatchIntervalMs.coerceAtLeast(50L))
                try {
                    reloadBundles()
                } catch (failure: Throwable) {
                    application.log.warn("keel: pack watch poll failed", failure)
                }
            }
        }
    }

    private fun snapshotWatchFingerprints() {
        for (bundle in bundleOrder) {
            val origin = bundle.origin
            val fingerprint = fingerprintFor(origin) ?: continue
            watchFingerprints[origin.description] = fingerprint
        }
    }

    /**
     * Reopen every watched bundle whose source changed since the last check
     * and atomically swap it into the live registries. Failed opens leave the
     * previous bundle serving, warn once, and are retried on the next call.
     * Returns the ids of the bundles that were swapped.
     */
    internal suspend fun reloadBundles(): List<String> = reloadMutex.withLock {
        val reloaded = mutableListOf<String>()
        for (bundle in bundleOrder.toList()) {
            val origin = bundle.origin
            val fingerprint = fingerprintFor(origin) ?: continue
            val key = origin.description
            val previous = watchFingerprints[key]
            if (previous == fingerprint) continue
            if (previous == null) {
                watchFingerprints[key] = fingerprint
                continue
            }
            val fresh = try {
                withContext(Dispatchers.IO) { FrontendBundle.open(origin) }
            } catch (failure: Throwable) {
                if (watchFailures.putIfAbsent(key, Unit) == null) {
                    logger?.warn("keel: failed to reload pack from $key; keeping the previous pack", failure)
                }
                continue
            }
            swapBundle(bundle, fresh)
            watchFingerprints[key] = fingerprint
            watchFailures.remove(key)
            logger?.info("keel: reloaded pack ${fresh.id} from $key")
            reloaded.add(fresh.id)
        }
        reloaded
    }

    private fun swapBundle(old: FrontendBundle, fresh: FrontendBundle) {
        synchronized(swapLock) {
            if (fresh.id != old.id) {
                bundlesById.remove(old.id)
                watchFingerprints.remove(old.origin.description)
            }
            bundlesById[fresh.id] = fresh
            val index = bundleOrder.indexOfFirst { it.id == old.id }
            if (index >= 0) bundleOrder[index] = fresh else bundleOrder.add(fresh)
            val configured = configuredOrder.indexOfFirst { it.id == old.id }
            if (configured >= 0) configuredOrder[configured] = fresh
        }
        old.close()
    }

    private data class BundleFingerprint(val size: Long, val modified: Long)

    private fun fingerprintFor(origin: BundleOrigin): BundleFingerprint? = when (origin) {
        is BundleOrigin.File -> runCatching {
            BundleFingerprint(
                size = Files.size(origin.path),
                modified = Files.getLastModifiedTime(origin.path).toMillis(),
            )
        }.getOrNull()
        is BundleOrigin.Directory -> fingerprintDirectory(origin.path)
        is BundleOrigin.Resource -> null
    }

    private fun fingerprintDirectory(dir: Path): BundleFingerprint? {
        if (!Files.isDirectory(dir)) return null
        return runCatching {
            var size = 0L
            var modified = 0L
            Files.walk(dir).use { walk ->
                walk.filter { Files.isRegularFile(it) }.forEach { file ->
                    size += Files.size(file)
                    modified = maxOf(modified, Files.getLastModifiedTime(file).toMillis())
                }
            }
            BundleFingerprint(size, modified)
        }.getOrNull()
    }

    private fun csrfPolicy() = config.csrf ?: SameOriginCsrfPolicy(config.csrfAllowedOrigins)

    private suspend fun guardCsrf(call: ApplicationCall): Boolean {
        val verdict = csrfPolicy().check(
            CsrfRequest(
                method = call.request.httpMethod.value,
                contentType = call.request.header("Content-Type"),
                origin = call.request.header("Origin"),
                secFetchSite = call.request.header("Sec-Fetch-Site"),
                host = call.request.header("Host").orEmpty(),
                keelVisit = call.request.header(KeelHeaders.VISIT).equals("true", ignoreCase = true),
            ),
        )
        return when (verdict) {
            is CsrfVerdict.Allow -> true
            is CsrfVerdict.Deny -> {
                respondCsrfDenied(call, verdict.reason)
                false
            }
        }
    }

    private suspend fun respondCsrfDenied(call: ApplicationCall, reason: String) {
        val errors = JsonObject(
            mapOf("csrf" to JsonArray(listOf(JsonPrimitive(reason)))),
        )
        val payload = JsonObject(mapOf("errors" to errors))
        call.respondText(
            KeelJson.codec.encodeToString(JsonObject.serializer(), payload),
            ContentType.Application.Json,
            HttpStatusCode.Forbidden,
        )
    }

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

        internal fun parseHeaderSet(value: String?): Set<String> =
            value?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()

        internal fun isImmutableAsset(entry: String): Boolean {
            val path = entry.replace('\\', '/')
            if (path.startsWith("assets/")) return true
            return path.startsWith("chunks/") && path.contains("-") && path.endsWith(".js")
        }

        internal fun etagMatches(ifNoneMatch: String?, etag: String): Boolean {
            if (ifNoneMatch == null) return false
            val header = ifNoneMatch.trim()
            if (header == "*") return true
            return header.split(',').map { it.trim() }.any { it == etag || it == "W/$etag" }
        }
    }
}

/**
 * Install Keel. The host owns pack choice: set [KeelConfig.bundle] for the
 * pages DSL, or render custom routes from a pack passed at the call site.
 */
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

/**
 * Scope routes to [bundle]: pages rendered below this route use this pack.
 * A scoped pack beats the host's configured pack; Keel never consults a
 * visitor header.
 */
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
