package dev.kolektiv.keel.ktor

import java.security.SecureRandom
import java.util.Base64

/**
 * Opt-in `Content-Security-Policy` for document responses.
 *
 * Set [KeelConfig.csp] to emit a policy header whose nonce matches the
 * scripts Keel stamps into the document shell and the `<script>`/`<link>`
 * tags it emits from a pack head. A visit returns JSON only and never
 * carries a nonce or the header.
 */
fun interface CspPolicy {
    /** The header value to send for a document stamped with [nonce]. */
    fun header(nonce: String): String

    companion object {
        private val random = SecureRandom()

        /** One nonce per document response: 16 random bytes, Base64 URL-safe, unpadded. */
        internal fun generateNonce(): String {
            val bytes = ByteArray(16)
            random.nextBytes(bytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }

        /**
         * A small default policy: nonce scripts with [scriptSrc] appended
         * (`'strict-dynamic'` by default), [styleSrc] styles, no plugins, no
         * `<base>`.
         *
         * Produces e.g.
         * `script-src 'nonce-…' 'strict-dynamic'; style-src 'self'; object-src 'none'; base-uri 'none'`.
         */
        fun nonce(
            scriptSrc: String = "'strict-dynamic'",
            styleSrc: String = "'self'",
        ): CspPolicy = CspPolicy { nonce ->
            "script-src 'nonce-$nonce' $scriptSrc; style-src $styleSrc; object-src 'none'; base-uri 'none'"
        }
    }
}
