package dev.kolektiv.keel.samples.harbor

import dev.kolektiv.keel.Keel
import dev.kolektiv.keel.KeelJson
import dev.kolektiv.keel.bundle.FrontendBundle
import dev.kolektiv.keel.seed.KeelSeed
import dev.kolektiv.keel.typegen.ClasspathContract
import dev.kolektiv.keel.typegen.Typegen
import dev.kolektiv.keel.visit.KeelHeaders
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

class HarborAppTest {

    @TempDir
    lateinit var pack: Path

    @BeforeEach
    fun resetBoard() {
        Board.clear()
    }

    private fun writePack() {
        val pages = mapOf(
            "harbor.home" to "pages/harbor.home.js",
            "harbor.user" to "pages/harbor.user.js",
            "harbor.notFound" to "pages/harbor.notFound.js",
        )
        val pageEntries = pages.entries.joinToString(",\n") { (pageId, module) ->
            val css = if (pageId == "harbor.home") """, "css": ["assets/styles.css"]""" else ""
            """"$pageId": { "module": "$module"$css }"""
        }
        pack.resolve("manifest.json").writeText(
            """
            {
              "format": "keel/1",
              "id": "harbor",
              "version": "0.1.0",
              "framework": "svelte",
              "host": "#__keel_root",
              "pages": {
                $pageEntries
              },
              "notFound": "pages/harbor.notFound.js"
            }
            """.trimIndent(),
        )
        pack.resolve("bootstrap.js").writeText("export function bootstrap() {}")
        pack.resolve("pages").createDirectories()
        for (module in pages.values) {
            pack.resolve(module).writeText("export async function mount() {}")
        }
        pack.resolve("assets").createDirectories()
        pack.resolve("assets/styles.css").writeText("body{}")
    }

    @Test
    fun `home document carries the board seed and seo tags`() = testApplication {
        writePack()
        application { harbor(FrontendBundle.fromDirectory(pack)) }
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("id=\"__keel_seed\""))
        assertTrue(body.contains("\"page\":\"harbor.home\""))
        assertTrue(body.contains("Harbor"))
        assertTrue(body.contains("name=\"description\""))
        assertTrue(body.contains("property=\"og:title\""))
        assertTrue(body.contains("property=\"og:type\""))
        assertTrue(body.contains("application/ld+json"))
        assertTrue(body.contains("<noscript>"))
        assertTrue(body.contains("/__keel/pack/harbor/pages/harbor.home.js"))
        assertTrue(body.contains("/__keel/pack/harbor/bootstrap.js"))
    }

    @Test
    fun `setName action sets a cookie and visit sees the viewer`() = testApplication {
        writePack()
        application { harbor(FrontendBundle.fromDirectory(pack)) }
        val created = client.post("${Keel.ACTION_PATH}/harbor.setName") {
            contentType(ContentType.Application.Json)
            setBody("""{"displayName":"Ada"}""")
        }
        assertEquals(HttpStatusCode.OK, created.status)
        assertTrue(created.bodyAsText().contains("Ada"))
        val cookie = cookieHeader(created.headers["Set-Cookie"])
        assertTrue(cookie.contains(Board.COOKIE))

        val home = client.get("/") {
            header(KeelHeaders.VISIT, "true")
            header("Cookie", cookie)
        }
        assertEquals(HttpStatusCode.OK, home.status)
        val seed = KeelJson.codec.decodeFromString(KeelSeed.serializer(), home.bodyAsText())
        assertEquals("harbor.home", seed.page)
        assertEquals("Harbor", seed.head?.title)
        assertTrue(home.bodyAsText().contains("Ada"))
    }

    @Test
    fun `setName validation returns 422 errors envelope`() = testApplication {
        writePack()
        application { harbor(FrontendBundle.fromDirectory(pack)) }
        val response = client.post("${Keel.ACTION_PATH}/harbor.setName") {
            contentType(ContentType.Application.Json)
            setBody("""{"displayName":"x"}""")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"errors\""))
        assertTrue(body.contains("displayName"))
        assertTrue(!body.contains("\"page\""))
    }

    @Test
    fun `postMessage requires a viewer then stores the message`() = testApplication {
        writePack()
        application { harbor(FrontendBundle.fromDirectory(pack)) }
        val denied = client.post("${Keel.ACTION_PATH}/harbor.postMessage") {
            contentType(ContentType.Application.Json)
            setBody("""{"body":"hello from the pier"}""")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, denied.status)

        val named = client.post("${Keel.ACTION_PATH}/harbor.setName") {
            contentType(ContentType.Application.Json)
            setBody("""{"displayName":"Ada"}""")
        }
        val cookie = cookieHeader(named.headers["Set-Cookie"])
        val posted = client.post("${Keel.ACTION_PATH}/harbor.postMessage") {
            contentType(ContentType.Application.Json)
            setBody("""{"body":"hello from the pier"}""")
            header("Cookie", cookie)
        }
        assertEquals(HttpStatusCode.OK, posted.status)
        assertTrue(posted.bodyAsText().contains("hello from the pier"))

        val home = client.get("/") {
            header(KeelHeaders.VISIT, "true")
        }
        assertTrue(home.bodyAsText().contains("hello from the pier"))
        assertTrue(home.bodyAsText().contains("Ada"))
    }

    @Test
    fun `user page shows display name and messages`() = testApplication {
        writePack()
        application { harbor(FrontendBundle.fromDirectory(pack)) }
        val named = client.post("${Keel.ACTION_PATH}/harbor.setName") {
            contentType(ContentType.Application.Json)
            setBody("""{"displayName":"Ada"}""")
        }
        val cookie = cookieHeader(named.headers["Set-Cookie"])
        client.post("${Keel.ACTION_PATH}/harbor.postMessage") {
            contentType(ContentType.Application.Json)
            setBody("""{"body":"first light on the channel"}""")
            header("Cookie", cookie)
        }
        val userId = decodeAction(named.bodyAsText(), SetNameOut.serializer()).user.id

        val response = client.get("/u/$userId") {
            header(KeelHeaders.VISIT, "true")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val seed = KeelJson.codec.decodeFromString(KeelSeed.serializer(), response.bodyAsText())
        assertEquals("harbor.user", seed.page)
        assertEquals(userId, seed.params["id"])
        assertTrue(response.bodyAsText().contains("Ada"))
        assertTrue(response.bodyAsText().contains("first light on the channel"))
        assertEquals("Ada — Harbor", seed.head?.title)

        val html = client.get("/u/$userId")
        assertTrue(html.bodyAsText().contains("<title"))
        assertTrue(html.bodyAsText().contains("Ada — Harbor"))
        assertTrue(html.bodyAsText().contains("Messages from Ada."))
    }

    @Test
    fun `unknown user and unknown path use notFound`() = testApplication {
        writePack()
        application { harbor(FrontendBundle.fromDirectory(pack)) }
        val missingUser = client.get("/u/does-not-exist")
        assertEquals(HttpStatusCode.NotFound, missingUser.status)
        assertTrue(missingUser.bodyAsText().contains("harbor.notFound"))

        val missingPath = client.get("/nope")
        assertEquals(HttpStatusCode.NotFound, missingPath.status)
        assertTrue(missingPath.bodyAsText().contains("\"path\":\"/nope\""))
    }

    @Test
    fun `generated page types stay in lockstep`() {
        val expected = Path.of("pack/src/lib/page-types.ts").readText()
        val actual = Typegen.emit(
            ClasspathContract.scan(listOf("dev.kolektiv.keel.samples.harbor")),
            pagesName = "HarborPages",
        )
        assertEquals(expected, actual)
    }

    private fun cookieHeader(setCookie: String?): String {
        assertTrue(!setCookie.isNullOrBlank(), "expected Set-Cookie")
        return setCookie!!.substringBefore(';')
    }

    private fun <T> decodeAction(body: String, serializer: kotlinx.serialization.KSerializer<T>): T {
        val data = KeelJson.codec.parseToJsonElement(body).jsonObject.getValue("data")
        return KeelJson.codec.decodeFromJsonElement(serializer, data)
    }
}
