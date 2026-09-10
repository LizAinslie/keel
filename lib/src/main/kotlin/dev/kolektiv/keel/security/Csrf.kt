package dev.kolektiv.keel.security

import java.net.URI

data class CsrfRequest(
    val method: String,
    val contentType: String? = null,
    val origin: String? = null,
    val secFetchSite: String? = null,
    val host: String,
    val keelVisit: Boolean = false,
)

sealed interface CsrfVerdict {
    data object Allow : CsrfVerdict
    data class Deny(val reason: String) : CsrfVerdict
}

fun interface CsrfPolicy {
    fun check(request: CsrfRequest): CsrfVerdict
}

/**
 * Always-on write protection: a non-safe method is allowed when it carries a
 * non-CORS-simple marker (`Content-Type: application/json` or `X-Keel-Visit`)
 * **and** Origin / Sec-Fetch-Site is same-origin or allowlisted.
 *
 * Missing Origin and Sec-Fetch-Site is treated as a non-browser client (tests,
 * curl, server-to-server) once the non-simple marker is present — a cross-site
 * form cannot omit those headers in a modern browser.
 */
class SameOriginCsrfPolicy(
    private val allowedOrigins: Set<String> = emptySet(),
) : CsrfPolicy {
    override fun check(request: CsrfRequest): CsrfVerdict {
        if (isSafe(request.method)) return CsrfVerdict.Allow
        if (!isJson(request.contentType) && !request.keelVisit) {
            return CsrfVerdict.Deny("write requires application/json or X-Keel-Visit")
        }
        val origin = request.origin?.trim()?.takeIf { it.isNotEmpty() }
        if (origin != null) {
            if (origin in allowedOrigins) return CsrfVerdict.Allow
            if (originMatchesHost(origin, request.host)) return CsrfVerdict.Allow
            return CsrfVerdict.Deny("origin is not same-origin")
        }
        return when (request.secFetchSite?.lowercase()) {
            null, "" -> CsrfVerdict.Allow
            "same-origin", "none" -> CsrfVerdict.Allow
            else -> CsrfVerdict.Deny("cross-site fetch")
        }
    }

    companion object {
        private val SAFE = setOf("GET", "HEAD", "OPTIONS")

        fun isSafe(method: String): Boolean = method.uppercase() in SAFE

        fun isJson(contentType: String?): Boolean {
            if (contentType == null) return false
            val mime = contentType.substringBefore(';').trim()
            return mime.equals("application/json", ignoreCase = true)
        }

        fun originMatchesHost(origin: String, host: String): Boolean {
            val uri = runCatching { URI(origin) }.getOrNull() ?: return false
            val originHost = uri.host ?: return false
            val originPort = uri.port
            val expected = if (originPort == -1) originHost else "$originHost:$originPort"
            return expected.equals(host, ignoreCase = true)
        }
    }
}
