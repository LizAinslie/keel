package dev.kolektiv.keel.ktor

import dev.kolektiv.keel.bundle.FrontendBundle
import dev.kolektiv.keel.seed.PageHead
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.util.AttributeKey
import kotlinx.serialization.serializer

@PublishedApi
internal val KeelEngineKey = AttributeKey<KeelEngine>("KeelEngine")

@PublishedApi
internal val KeelRouteBundleKey = AttributeKey<FrontendBundle>("KeelRouteBundle")

/**
 * Render [pageId] from an explicitly passed [bundle]. This is the canonical
 * call-site form: the host owns pack choice and Keel never picks a pack.
 */
suspend inline fun <reified T : Any> ApplicationCall.respondPage(
    bundle: FrontendBundle,
    pageId: String,
    data: T,
    params: Map<String, String> = emptyMap(),
    status: HttpStatusCode = HttpStatusCode.OK,
    head: PageHead? = null,
) {
    keelEngine().respond(this, bundle, pageId, data, serializer<T>(), params, status, head = head)
}

/**
 * Render [pageId] from the call-site pack: the route-scoped pack
 * (`route.keel(pack)`) when it implements the page, otherwise the host's
 * single configured pack. The pack is never chosen from a visitor header.
 * Throws [MissingPackException] or [AmbiguousPackException] when the call site
 * did not name one; pass a pack explicitly to disambiguate.
 */
suspend inline fun <reified T : Any> ApplicationCall.respondPage(
    pageId: String,
    data: T,
    params: Map<String, String> = emptyMap(),
    status: HttpStatusCode = HttpStatusCode.OK,
    head: PageHead? = null,
) {
    val engine = keelEngine()
    val bundle = engine.bundleFor(this, pageId)
    engine.respond(this, bundle, pageId, data, serializer<T>(), params, status, head = head)
}

@PublishedApi
internal fun ApplicationCall.keelEngine(): KeelEngine =
    application.attributes.getOrNull(KeelEngineKey)
        ?: throw IllegalStateException("Install keel { } before respondPage")
