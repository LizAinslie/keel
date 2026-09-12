package dev.kolektiv.keel.seed

import dev.kolektiv.keel.Keel
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class KeelThemeRef(
    val id: String,
    val version: String,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PageHead(
    val title: String,
    val description: String? = null,
    val canonical: String? = null,
    val image: String? = null,
    val type: String? = null,
    /** Sanitized pack-authored head tags. Present when the pack shipped a head template. */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val html: String? = null,
)

/**
 * The JSON document placed in `#__keel_seed` and returned by visit requests.
 *
 * [data] is the page's `@Serializable` payload. Themes never parse the seed
 * themselves — bootstrap does, then passes a typed context into `mount()`.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class KeelSeed(
    @EncodeDefault
    val v: Int = Keel.SEED_VERSION,
    val page: String,
    val path: String,
    @EncodeDefault
    val params: Map<String, String> = emptyMap(),
    val data: JsonElement,
    @EncodeDefault
    val errors: Map<String, List<String>> = emptyMap(),
    val theme: KeelThemeRef,
    val entry: String,
    @EncodeDefault
    val css: List<String> = emptyList(),
    /** Content hash of the serving pack; clients reload when it changes. */
    @EncodeDefault
    val build: String = "",
    val shared: JsonObject? = null,
    @EncodeDefault
    val host: String = Keel.DEFAULT_HOST,
    val layout: String? = null,
    val redirect: String? = null,
    val head: PageHead? = null,
) {
    init {
        require(page.isNotBlank()) { "page id is required" }
        require(path.startsWith("/")) { "path must be absolute, got '$path'" }
        require(entry.isNotBlank()) { "entry module url is required" }
    }

    val isRedirect: Boolean get() = redirect != null
    val hasErrors: Boolean get() = errors.isNotEmpty()

    companion object {
        fun emptyData(): JsonElement = JsonObject(emptyMap())
    }
}
