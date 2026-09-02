package dev.kolektiv.keel

import dev.kolektiv.keel.manifest.KeelManifest
import dev.kolektiv.keel.manifest.KeelPageEntry
import dev.kolektiv.keel.page.PageRegistry
import dev.kolektiv.keel.page.pages
import dev.kolektiv.keel.seed.KeelSeed
import dev.kolektiv.keel.seed.KeelThemeRef
import dev.kolektiv.keel.theme.ChainThemeResolver
import dev.kolektiv.keel.theme.ThemeRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KeelCoreTest {

    @Serializable
    data class BlogPost(val title: String)

    @Test
    fun `registry matches parameterized paths`() {
        val registry = pages {
            page<BlogPost>("blog.post", "/p/{slug}")
            page<BlogPost>("blog.home", "/")
        }
        val match = registry.match("/p/hello")
        assertEquals("blog.post", match?.id)
        assertEquals(mapOf("slug" to "hello"), PageRegistry.params("/p/{slug}", "/p/hello"))
    }

    @Test
    fun `seed round-trips through json`() {
        val seed = KeelSeed(
            page = "blog.post",
            path = "/p/hello",
            params = mapOf("slug" to "hello"),
            data = buildJsonObject { put("title", "Hello") },
            theme = KeelThemeRef("midnight", "1.0.0"),
            entry = "/themes/midnight/pages/blog.post.js",
            css = listOf("/themes/midnight/assets/blog.post.css"),
        )
        val encoded = KeelJson.codec.encodeToString(KeelSeed.serializer(), seed)
        val decoded = KeelJson.codec.decodeFromString(KeelSeed.serializer(), encoded)
        assertEquals(seed, decoded)
        assertTrue(encoded.contains("\"v\":1"))
    }

    @Test
    fun `theme chain falls back when a pack omits a page`() {
        val midnight = KeelManifest(
            id = "midnight",
            version = "1.0.0",
            framework = "svelte",
            pages = mapOf("blog.home" to KeelPageEntry("pages/blog.home.js")),
        )
        val fallback = KeelManifest(
            id = "default",
            version = "1.0.0",
            framework = "svelte",
            pages = mapOf(
                "blog.home" to KeelPageEntry("pages/blog.home.js"),
                "blog.post" to KeelPageEntry("pages/blog.post.js"),
            ),
        )
        val resolver = ChainThemeResolver(listOf(midnight, fallback), defaultId = "default")
        val selected = resolver.resolve(
            ThemeRequest(pageId = "blog.post", path = "/p/hello", overrideId = "midnight"),
        )
        assertEquals("default", selected.manifest.id)
        assertEquals("pages/blog.post.js", selected.entry.module)
    }
}
