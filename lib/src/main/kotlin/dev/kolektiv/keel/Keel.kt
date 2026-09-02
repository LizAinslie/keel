package dev.kolektiv.keel

/**
 * Protocol constants for Keel.
 *
 * Coordinates: [GROUP]:[ARTIFACT] (`dev.kolektiv.keel:core`).
 * Future framework renderers (Svelte, React, …) live in sibling artifacts.
 */
object Keel {
    const val GROUP: String = "dev.kolektiv.keel"
    const val ARTIFACT: String = "core"
    const val FORMAT: String = "keel/1"
    const val SEED_VERSION: Int = 1
    const val DEFAULT_HOST: String = "#__keel_root"
    const val SEED_ELEMENT_ID: String = "__keel_seed"
    const val NAVIGATE_PATH: String = "/__keel/navigate"
}
