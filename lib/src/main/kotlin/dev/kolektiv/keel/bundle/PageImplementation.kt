package dev.kolektiv.keel.bundle

/**
 * A page as the pack implements it: module path, css, optional layout.
 * Paths are relative to the bundle root (`pages/home.js`).
 */
data class PageImplementation(
    val pageId: String,
    val module: String,
    val css: List<String>,
    val layout: String?,
    val head: String? = null,
)
