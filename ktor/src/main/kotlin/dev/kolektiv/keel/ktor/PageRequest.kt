package dev.kolektiv.keel.ktor

import dev.kolektiv.keel.KeelJson
import dev.kolektiv.keel.page.PageBinding
import dev.kolektiv.keel.seed.PageHead
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receiveText
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Context handed to a page loader. [params] are `{name}` captures from the
 * host path pattern. [query] is the *target* query string — for a visit to
 * `/posts?page=2` that is `page=2`. The `/__keel/navigate?to=` proxy strips
 * its own wrapper so loaders still see the page query.
 */
class PageRequest(
    val call: ApplicationCall,
    val binding: PageBinding,
    val path: String,
    val params: Map<String, String>,
    val query: Parameters,
) {
    val method: HttpMethod get() = call.request.httpMethod

    var head: PageHead? = null
        private set

    /**
     * Absolute URL for this document. Used as the default canonical / `og:url`
     * so crawlers that do not run JS still see a stable locator.
     */
    fun documentUrl(): String = documentUrl(call, path)

    fun head(
        title: String,
        description: String? = null,
        canonical: String? = documentUrl(),
        image: String? = null,
        type: String? = "website",
    ): PageHead {
        val value = PageHead(
            title = title,
            description = description,
            canonical = canonical,
            image = image,
            type = type,
        )
        this.head = value
        return value
    }

    // Do not call on GET.
    suspend fun receiveJson(): JsonObject {
        val text = call.receiveText()
        if (text.isBlank()) return JsonObject(emptyMap())
        return KeelJson.codec.parseToJsonElement(text).jsonObject
    }
}

/** Throw from a loader to render the host's not-found page. */
class PageMissingException(val path: String) : Exception("no page for $path")

class PageValidationException(
    val errors: Map<String, List<String>>,
    val data: Any,
) : Exception("page validation failed")

class PageRedirectException(val to: String) : Exception("redirect to $to") {
    init {
        require(to.startsWith("/")) { "redirect must be an absolute path, got '$to'" }
    }
}

internal fun documentUrl(call: ApplicationCall, path: String): String {
    val origin = call.request.origin
    val hostHeader = call.request.header("Host")
    val host = hostHeader?.ifBlank { null } ?: origin.serverHost.ifBlank { return path }
    val scheme = call.request.header("X-Forwarded-Proto")?.ifBlank { null }
        ?: origin.scheme.ifBlank { "http" }
    val hostHasPort = host.contains(':')
    val port = origin.serverPort
    val portPart = when {
        hostHasPort -> ""
        scheme == "https" && (port == 443 || port == 0) -> ""
        scheme == "http" && (port == 80 || port == 0) -> ""
        else -> ":$port"
    }
    return "$scheme://$host$portPart$path"
}
