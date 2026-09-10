package dev.kolektiv.keel.manifest

import dev.kolektiv.keel.Keel
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class KeelPageEntry(
    val module: String,
    @EncodeDefault
    val css: List<String> = emptyList(),
    val layout: String? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class KeelCompat(
    val contract: String,
)

/**
 * Theme pack manifest (`manifest.json` at the root of a `.feb` zip).
 *
 * Pages are keyed by **stable page ids**, never URL paths. Paths live on the
 * host. A pack may implement a subset of ids; the host falls back to the
 * default pack, then `notFound`.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class KeelManifest(
    @EncodeDefault
    val format: String = Keel.FORMAT,
    val id: String,
    val version: String,
    val framework: String,
    @EncodeDefault
    val host: String = Keel.DEFAULT_HOST,
    val pages: Map<String, KeelPageEntry>,
    @EncodeDefault
    val layouts: Map<String, String> = emptyMap(),
    val notFound: String? = null,
    val compat: KeelCompat? = null,
) {
    init {
        require(id.isNotBlank()) { "manifest id is required" }
        require(version.isNotBlank()) { "manifest version is required" }
        require(framework.isNotBlank()) { "framework is required" }
    }

    fun page(pageId: String): KeelPageEntry? = pages[pageId]

    fun implements(pageId: String): Boolean = pages.containsKey(pageId)
}
