package dev.kolektiv.keel.samples.harbor

import dev.kolektiv.keel.Keel
import dev.kolektiv.keel.KeelJson
import dev.kolektiv.keel.seed.KeelSeed
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class HarborAppTest {

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
                "harbor.home": { "module": "pages/home.js", "css": ["assets/styles.css"] },
                "harbor.list": { "module": "pages/list.js" },
                "harbor.post": { "module": "pages/post.js" },
                "harbor.about": { "module": "pages/about.js" },
                "harbor.notFound": { "module": "pages/not-found.js" }
              },
              "notFound": "pages/not-found.js"
            }
            """.trimIndent(),
        )
        pack.resolve("bootstrap.js").writeText("export function bootstrap() {}")
        pack.resolve("pages").createDirectories()
        pack.resolve("pages/home.js").writeText("export async function mount() {}")
        pack.resolve("assets").createDirectories()
        pack.resolve("assets/styles.css").writeText("body{}")
    }

    @Test
    fun `home document carries the journal seed`() = testApplication {
        writePack()
        application { harbor(pack) }
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("id=\"__keel_seed\""))
        assertTrue(body.contains("\"page\":\"harbor.home\""))
        assertTrue(body.contains("Harbor journal"))
        assertTrue(body.contains("/__keel/pack/pages/home.js"))
        assertTrue(body.contains("/__keel/pack/bootstrap.js"))
    }

    @Test
    fun `post visit returns typed seed`() = testApplication {
        writePack()
        application { harbor(pack) }
        val response = client.get(Keel.NAVIGATE_PATH) {
            parameter("to", "/p/first-watch")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val seed = KeelJson.codec.decodeFromString(KeelSeed.serializer(), response.bodyAsText())
        assertEquals("harbor.post", seed.page)
        assertEquals("/p/first-watch", seed.path)
        assertEquals("first-watch", seed.params["slug"])
        assertTrue(seed.data.toString().contains("First watch"))
    }

    @Test
    fun `list search filters entries`() = testApplication {
        writePack()
        application { harbor(pack) }
        val response = client.get("/posts") {
            parameter("q", "chain")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("A chain of one"))
        assertTrue(!body.contains("First watch"))
    }

    @Test
    fun `unknown slug and unknown path use notFound`() = testApplication {
        writePack()
        application { harbor(pack) }
        val missingPost = client.get("/p/does-not-exist")
        assertEquals(HttpStatusCode.NotFound, missingPost.status)
        assertTrue(missingPost.bodyAsText().contains("harbor.notFound"))

        val missingPath = client.get("/nope")
        assertEquals(HttpStatusCode.NotFound, missingPath.status)
        assertTrue(missingPath.bodyAsText().contains("\"path\":\"/nope\""))
    }
}
