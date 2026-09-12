package dev.kolektiv.keel

import dev.kolektiv.keel.seed.DocumentHead
import dev.kolektiv.keel.seed.KeelSeed
import dev.kolektiv.keel.seed.KeelThemeRef
import dev.kolektiv.keel.seed.PageHead
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DocumentHeadTest {

    private val seed = KeelSeed(
        page = "harbor.user",
        path = "/u/ada",
        params = mapOf("id" to "ada"),
        data = buildJsonObject {
            put("user", buildJsonObject { put("displayName", "Ada") })
            put("feed", buildJsonArray { add(buildJsonObject { put("id", "m1") }) })
        },
        theme = KeelThemeRef("harbor", "0.1.0"),
        entry = "/pages/harbor.user.js",
        shared = buildJsonObject { put("site", "Harbor") },
    )

    @Test
    fun `pack html becomes the seed head and is tagged for replacement`() {
        val head = DocumentHead.resolve(
            packHtml = """<title>Harbor</title><meta name="description" content="A board." />""",
            host = null,
            seed = seed,
            documentUrl = "http://localhost:8090/u/ada",
        )
        assertEquals("Harbor", head?.title)
        assertEquals("A board.", head?.description)
        assertEquals("http://localhost:8090/u/ada", head?.canonical)
        val html = head?.html.orEmpty()
        assertTrue(html.contains("""<title data-keel-head="">Harbor</title>"""), html)
        assertTrue(html.contains("""<meta data-keel-head="" name="description" content="A board.">"""), html)
    }

    @Test
    fun `seed-path templates substitute from data page path and params`() {
        val head = DocumentHead.resolve(
            packHtml = """<title>{{data.user.displayName}} — Harbor</title>""" +
                """<meta name="description" content="Messages from {{data.user.displayName}} at {{path}}." />""" +
                """<link rel="canonical" href="/u/{{params.id}}" />""",
            host = null,
            seed = seed,
            documentUrl = "http://localhost:8090/u/ada",
        )
        assertEquals("Ada — Harbor", head?.title)
        assertEquals("Messages from Ada at /u/ada.", head?.description)
        assertEquals("http://localhost:8090/u/ada", head?.canonical)
        val html = head?.html.orEmpty()
        assertTrue(html.contains("Ada — Harbor"), html)
        assertTrue(html.contains("Messages from Ada at /u/ada."), html)
        assertEquals("harbor.user", DocumentHead.substitute("{{page}}", DocumentHead.lookupRoot(seed)))
        assertEquals("Harbor", DocumentHead.substitute("{{shared.site}}", DocumentHead.lookupRoot(seed)))
        assertEquals("m1", DocumentHead.substitute("{{data.feed.0.id}}", DocumentHead.lookupRoot(seed)))
    }

    @Test
    fun `unknown template path drops the tag and the host fills the field`() {
        val head = DocumentHead.resolve(
            packHtml = """<title>{{data.missing}} — Harbor</title><meta name="description" content="Static description" />""",
            host = PageHead(title = "Host title"),
            seed = seed,
            documentUrl = "http://localhost:8090/",
        )
        assertEquals("Host title", head?.title)
        assertEquals("Static description", head?.description)
        assertTrue(!head?.html.orEmpty().contains("<title"), head?.html.orEmpty())
    }

    @Test
    fun `host fallback is used when the pack ships no head`() {
        val host = PageHead(title = "Host title", description = "Host desc")
        val head = DocumentHead.resolve(
            packHtml = null,
            host = host,
            seed = seed,
            documentUrl = "http://localhost:8090/",
        )
        assertEquals("Host title", head?.title)
        assertEquals("Host desc", head?.description)
        assertEquals("http://localhost:8090/", head?.canonical)
        assertNull(head?.html)
        assertNull(
            DocumentHead.resolve("   ", host, seed, "http://localhost:8090/")?.html,
        )
        assertNull(DocumentHead.resolve(null, null, seed, "http://localhost:8090/"))
    }

    @Test
    fun `unsafe tags attrs and urls are dropped`() {
        val head = DocumentHead.resolve(
            packHtml = """<title>Safe</title>""" +
                """<meta property="og:image" content="javascript:alert(1)" />""" +
                """<meta name="description" content="A board." onload="alert(1)" />""" +
                """<link rel="icon" href="/favicon.svg" />""" +
                """<link rel="stylesheet" href="data:text/css,body{}" />""" +
                """<script src="data:text/javascript,alert(1)"></script>""" +
                """<script>alert(1)</script>""" +
                """<script src="/client.js"></script>""" +
                """<div>nope</div>""",
            host = null,
            seed = seed,
            documentUrl = "http://localhost:8090/",
        )
        assertEquals("Safe", head?.title)
        assertNull(head?.image)
        assertNull(head?.description)
        val html = head?.html.orEmpty()
        assertTrue(!html.contains("javascript:"), html)
        assertTrue(!html.contains("data:"), html)
        assertTrue(!html.contains("<div"), html)
        assertTrue(!html.contains("onload"), html)
        assertTrue(html.contains("href=\"http://localhost:8090/favicon.svg\""), html)
        assertTrue(html.contains("src=\"http://localhost:8090/client.js\""), html)
    }

    @Test
    fun `url sanitizing keeps only site-relative and http urls`() {
        assertNull(DocumentHead.sanitizeUrl("javascript:alert(1)"))
        assertNull(DocumentHead.sanitizeUrl("data:text/html,x"))
        assertNull(DocumentHead.sanitizeUrl("blob:https://example.com/x"))
        assertNull(DocumentHead.sanitizeUrl("relative/path"))
        assertEquals("/favicon.svg", DocumentHead.sanitizeUrl("/favicon.svg"))
        assertEquals("https://example.com/x", DocumentHead.sanitizeUrl("https://example.com/x"))
    }
}
