package dev.kolektiv.keel.visit

/**
 * Request headers the host router and Ktor plugin share.
 *
 * A document request (no [VISIT]) returns the HTML shell.
 * A visit request returns a JSON [dev.kolektiv.keel.seed.KeelSeed].
 */
object KeelHeaders {
    const val VISIT: String = "X-Keel-Visit"
    const val ONLY: String = "X-Keel-Only"
    const val EXCEPT: String = "X-Keel-Except"
    const val THEME: String = "X-Keel-Theme"
    const val VERSION: String = "X-Keel-Version"
    const val PARTIAL: String = "X-Keel-Partial"
}
