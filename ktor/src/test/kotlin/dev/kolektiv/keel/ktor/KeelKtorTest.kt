package dev.kolektiv.keel.ktor

import dev.kolektiv.keel.Keel
import dev.kolektiv.keel.KeelJson
import dev.kolektiv.keel.seed.KeelSeed
import dev.kolektiv.keel.visit.KeelHeaders
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

class KeelKtorTest {

    @Serializable
    data class HomePage(val greeting: String)

    @Serializable
    data class PostPage(val slug: String, val title: String)

    @Serializable
    data class MissingPage(val path: String)

    @TempDir
    lateinit var pack: Path

    private fun writePack() {
        pack.resolve("manifest.json").writeText(
            """
            {
              "format": "keel/1",
              "id": "harbor",
              "version": "0.1.0",
              "framework": "svelte",
              "host": "#__keel_root",
              "pages": {
                "home": { "module": "pages/home.js", "css": ["assets/styles.css"] },
                "post": { "module": "pages/post.js" },
                "missing": { "module": "pages/missing.js" }
              },
              "notFound": "pages/missing.js"
            }
            """.trimIndent(),
        )
        pack.resolve("bootstrap.js").writeText("export {}")
        pack.resolve("pages").toFile().mkdirs()
        pack.resolve("pages/home.js").writeText("export async function mount() {}")
    }

    private fun Application.installSample() {
        keel {
            packDir = pack
            title = "Harbor"
            notFoundPageId = "missing"
            pages {
                page<HomePage>("home", "/") { HomePage("hello") }
                page<PostPage>("post", "/p/{slug}") {
                    if (params.getValue("slug") == "missing") throw PageMissingException(path)
                    PostPage(slug = params.getValue("slug"), title = "Entry")
                }
                page<MissingPage>("missing", "/__not-found") { MissingPage(path) }
            }
        }
    }

    @Test
    fun `document request returns html shell with seed`() = testApplication {
        writePack()
        application { installSample() }
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("id=\"__keel_seed\""))
        assertTrue(body.contains("id=\"__keel_root\""))
        assertTrue(body.contains("/__keel/pack/bootstrap.js"))
        assertTrue(body.contains("/__keel/pack/pages/home.js"))
        assertTrue(body.contains("hello"))
    }

    @Test
    fun `visit returns seed json`() = testApplication {
        writePack()
        application { installSample() }
        val response = client.get(Keel.NAVIGATE_PATH) {
            parameter("to", "/p/hello")
            header(KeelHeaders.VISIT, "true")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val seed = KeelJson.codec.decodeFromString(KeelSeed.serializer(), response.bodyAsText())
        assertEquals("post", seed.page)
        assertEquals("/p/hello", seed.path)
        assertEquals("hello", seed.params["slug"])
        assertEquals("/__keel/pack/pages/post.js", seed.entry)
    }

    @Test
    fun `unknown path uses notFound page`() = testApplication {
        writePack()
        application { installSample() }
        val response = client.get(Keel.NAVIGATE_PATH) {
            parameter("to", "/nope")
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
        val seed = KeelJson.codec.decodeFromString(KeelSeed.serializer(), response.bodyAsText())
        assertEquals("missing", seed.page)
    }

    @Test
    fun `loader can throw PageMissingException`() = testApplication {
        writePack()
        application { installSample() }
        val response = client.get("/p/missing")
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertTrue(response.bodyAsText().contains("missing"))
    }

    @Test
    fun `manifest loader reads pack dir`() {
        writePack()
        val manifest = KeelEngine.loadManifest(pack)
        assertEquals("harbor", manifest.id)
        assertEquals("pages/home.js", manifest.page("home")?.module)
    }
}
