package dev.kolektiv.keel.ktor

import dev.kolektiv.keel.page.PageBinding
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall

/**
 * Context handed to a page loader. [params] are `{name}` captures from the
 * host path pattern. [query] is the *target* query string — for a visit to
 * `/__keel/navigate?to=/posts?page=2` that is `page=2`, not `to=…`.
 */
class PageRequest(
    val call: ApplicationCall,
    val binding: PageBinding,
    val path: String,
    val params: Map<String, String>,
    val query: Parameters,
)

/** Throw from a loader to render the host's not-found page. */
class PageMissingException(val path: String) : Exception("no page for $path")
