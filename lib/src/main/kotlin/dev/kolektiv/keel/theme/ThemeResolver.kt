package dev.kolektiv.keel.theme

import dev.kolektiv.keel.manifest.KeelManifest
import dev.kolektiv.keel.manifest.KeelPageEntry

data class ThemeRequest(
    val pageId: String,
    val path: String,
    val overrideId: String? = null,
    val tenantId: String? = null,
    val userPreference: String? = null,
)

data class ThemeSelection(
    val manifest: KeelManifest,
    val entry: KeelPageEntry,
)

class MissingPageInThemeException(
    val pageId: String,
    val themeId: String,
) : NoSuchElementException("theme '$themeId' does not implement page '$pageId'")

/**
 * Picks which installed pack implements a page.
 *
 * Default order: request override → host-owned preference → tenant → default pack.
 * A pack that does not implement [ThemeRequest.pageId] is skipped.
 * Theme preference is host policy, never a visitor cookie.
 */
fun interface ThemeResolver {
    fun resolve(request: ThemeRequest): ThemeSelection
}

class ChainThemeResolver(
    private val packs: List<KeelManifest>,
    private val defaultId: String,
) : ThemeResolver {
    override fun resolve(request: ThemeRequest): ThemeSelection {
        val preferred = listOfNotNull(request.overrideId, request.userPreference, request.tenantId, defaultId)
            .distinct()
        val ordered = preferred.mapNotNull { id -> packs.find { it.id == id } } +
            packs.filter { it.id !in preferred }
        val chosen = ordered.firstOrNull { it.implements(request.pageId) }
            ?: throw MissingPageInThemeException(request.pageId, preferred.firstOrNull() ?: defaultId)
        val entry = chosen.page(request.pageId)
            ?: throw MissingPageInThemeException(request.pageId, chosen.id)
        return ThemeSelection(chosen, entry)
    }
}
