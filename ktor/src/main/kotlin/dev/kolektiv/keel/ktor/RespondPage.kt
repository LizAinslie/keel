package dev.kolektiv.keel.ktor

import dev.kolektiv.keel.bundle.FrontendBundle
import dev.kolektiv.keel.seed.PageHead
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.path
import io.ktor.util.AttributeKey
import kotlinx.serialization.serializer

@PublishedApi
internal val KeelEngineKey = AttributeKey<KeelEngine>("KeelEngine")

@PublishedApi
internal val KeelRouteBundleKey = AttributeKey<FrontendBundle>("KeelRouteBundle")

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

suspend inline fun <reified T : Any> ApplicationCall.respondPage(
    pageId: String,
    data: T,
    params: Map<String, String> = emptyMap(),
    status: HttpStatusCode = HttpStatusCode.OK,
    head: PageHead? = null,
) {
    val engine = keelEngine()
    val bundle = engine.bundleFor(this, pageId, request.path())
    engine.respond(this, bundle, pageId, data, serializer<T>(), params, status, head = head)
}

@PublishedApi
internal fun ApplicationCall.keelEngine(): KeelEngine =
    application.attributes.getOrNull(KeelEngineKey)
        ?: throw IllegalStateException("Install keel { } before respondPage")
