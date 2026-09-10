package dev.kolektiv.keel

import dev.kolektiv.keel.action.DuplicateActionException
import dev.kolektiv.keel.action.actions
import dev.kolektiv.keel.manifest.KeelManifest
import dev.kolektiv.keel.manifest.KeelPageEntry
import dev.kolektiv.keel.page.PageRegistry
import dev.kolektiv.keel.page.PathPattern
import dev.kolektiv.keel.page.pages
import dev.kolektiv.keel.security.CsrfRequest
import dev.kolektiv.keel.security.CsrfVerdict
import dev.kolektiv.keel.security.SameOriginCsrfPolicy
import dev.kolektiv.keel.seed.KeelSeed
import dev.kolektiv.keel.seed.KeelThemeRef
import dev.kolektiv.keel.seed.PageHead
import dev.kolektiv.keel.seed.SeedFilter
import dev.kolektiv.keel.theme.ChainThemeResolver
import dev.kolektiv.keel.theme.ThemeRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KeelCoreTest {

    @Serializable
    data class BlogPost(val title: String)

    @Serializable
    data class EchoIn(val message: String)

    @Serializable
    data class EchoOut(val message: String)

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

    @Test
    fun `seed head round-trips through json`() {
        val seed = KeelSeed(
            page = "blog.post",
            path = "/p/hello",
            data = buildJsonObject { put("title", "Hello") },
            theme = KeelThemeRef("midnight", "1.0.0"),
            entry = "/themes/midnight/pages/blog.post.js",
            head = PageHead(title = "Hello · Blog", description = "A post.", canonical = "/p/hello"),
        )
        val encoded = KeelJson.codec.encodeToString(KeelSeed.serializer(), seed)
        val decoded = KeelJson.codec.decodeFromString(KeelSeed.serializer(), encoded)
        assertEquals(seed.head, decoded.head)
        assertTrue(encoded.contains("\"title\":\"Hello · Blog\""))
    }

    @Test
    fun `path grammar matches optional and tailcard`() {
        assertTrue(PathPattern.parse("/p/{slug}").matches("/p/hello"))
        assertEquals(mapOf("slug" to "hello"), PathPattern.parse("/p/{slug}").params("/p/hello"))
        assertEquals(mapOf("slug" to ""), PathPattern.parse("/p/{slug?}").params("/p"))
        assertEquals(mapOf("slug" to "hello"), PathPattern.parse("/p/{slug?}").params("/p/hello"))
        assertNull(PathPattern.parse("/p/{slug?}").params("/p/a/b"))
        assertEquals(mapOf("rest" to ""), PathPattern.parse("/p/{rest...}").params("/p"))
        assertEquals(mapOf("rest" to "a/b"), PathPattern.parse("/p/{rest...}").params("/p/a/b"))
        assertThrows(IllegalArgumentException::class.java) { PathPattern.parse("/p/{a}/{b?}/x") }
        assertThrows(IllegalArgumentException::class.java) { PathPattern.parse("/p/{a...}/x") }
        assertThrows(IllegalArgumentException::class.java) { PathPattern.parse("/p/{a}/{a}") }
        assertThrows(IllegalArgumentException::class.java) { PathPattern.parse("p/{a}") }
    }

    @Test
    fun `match prefers the most specific pattern`() {
        val registry = pages {
            page<BlogPost>("rest", "/p/{rest...}")
            page<BlogPost>("slug", "/p/{slug}")
            page<BlogPost>("new", "/p/new")
        }
        assertEquals("new", registry.match("/p/new")?.id)
        assertEquals("slug", registry.match("/p/hello")?.id)
        assertEquals("rest", registry.match("/p/a/b")?.id)
    }

    @Test
    fun `seed filter keeps top-level keys`() {
        val data = buildJsonObject {
            put("feed", "a")
            put("viewer", "b")
            put("meta", "c")
        }
        val only = SeedFilter.filterData(data, only = setOf("feed"), except = emptySet())
        assertEquals(setOf("feed"), (only as kotlinx.serialization.json.JsonObject).keys)
        val except = SeedFilter.filterData(data, only = emptySet(), except = setOf("meta"))
        assertEquals(setOf("feed", "viewer"), (except as kotlinx.serialization.json.JsonObject).keys)
    }

    @Test
    fun `same origin csrf allows json writes without origin`() {
        val policy = SameOriginCsrfPolicy()
        val allow = policy.check(
            CsrfRequest(method = "POST", contentType = "application/json", host = "localhost:8090"),
        )
        assertEquals(CsrfVerdict.Allow, allow)
        val deny = policy.check(
            CsrfRequest(method = "POST", contentType = "text/plain", host = "localhost:8090"),
        )
        assertTrue(deny is CsrfVerdict.Deny)
        val cross = policy.check(
            CsrfRequest(
                method = "POST",
                contentType = "application/json",
                origin = "https://evil.test",
                host = "localhost:8090",
            ),
        )
        assertTrue(cross is CsrfVerdict.Deny)
        val visit = policy.check(
            CsrfRequest(
                method = "POST",
                contentType = "multipart/form-data",
                host = "localhost:8090",
                keelVisit = true,
            ),
        )
        assertEquals(CsrfVerdict.Allow, visit)
    }

    @Test
    fun `duplicate action ids throw`() {
        assertThrows(DuplicateActionException::class.java) {
            actions {
                action<EchoIn, EchoOut>("echo")
                action<EchoIn, EchoOut>("echo")
            }
        }
    }
}
