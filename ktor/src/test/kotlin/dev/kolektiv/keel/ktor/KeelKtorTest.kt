package dev.kolektiv.keel.ktor

import dev.kolektiv.keel.Keel
import dev.kolektiv.keel.KeelJson
import dev.kolektiv.keel.bundle.FrontendBundle
import dev.kolektiv.keel.bundle.UnknownPageInBundleException
import dev.kolektiv.keel.page.PageMethod
import dev.kolektiv.keel.seed.KeelSeed
import dev.kolektiv.keel.visit.KeelHeaders
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

class KeelKtorTest {

    @Serializable
    data class HomePage(val greeting: String)

    @Serializable
    data class PostPage(val slug: String, val title: String)

    @Serializable
    data class MissingPage(val path: String)

    @Serializable
    data class ShopItem(val sku: String)

    @Serializable
    data class AdminHome(val label: String)

    @Serializable
    data class FormPage(val name: String)

    @Serializable
    data class EchoIn(val message: String)

    @Serializable
    data class EchoOut(val message: String)

    @TempDir
    lateinit var pack: Path

    @TempDir
    lateinit var extra: Path

    private fun writePack(
        dir: Path = pack,
        id: String = "harbor",
        pages: Map<String, String> = mapOf(
            "home" to "pages/home.js",
            "post" to "pages/post.js",
            "missing" to "pages/missing.js",
            "form" to "pages/form.js",
        ),
        notFound: String? = "pages/missing.js",
        heads: Map<String, String> = emptyMap(),
    ) {
        val manifest = buildJsonObject {
            put("format", "keel/1")
            put("id", id)
            put("version", "0.1.0")
            put("framework", "svelte")
            put("host", "#__keel_root")
            putJsonObject("pages") {
                for ((pageId, module) in pages) {
                    putJsonObject(pageId) {
                        put("module", module)
                        if (pageId == "home") {
                            putJsonArray("css") { add("assets/styles.css") }
                        }
                        heads[pageId]?.let { put("head", it) }
                    }
                }
            }
            if (notFound != null) put("notFound", notFound)
        }
        dir.resolve("manifest.json").writeText(manifest.toString())
        dir.resolve("bootstrap.js").writeText("export {}")
        dir.resolve("pages").createDirectories()
        for (module in pages.values) {
            dir.resolve(module).writeText("export async function mount() {}")
        }
        dir.resolve("assets").createDirectories()
        dir.resolve("assets/styles.css").writeText("body{}")
    }

    private fun Application.installSample(csp: CspPolicy? = null, watchPacks: Boolean = false) {
        keel {
            bundle = FrontendBundle.fromDirectory(pack)
            title = "Harbor"
            notFoundPageId = "missing"
            this.csp = csp
            this.watchPacks = watchPacks
            pages {
                page<HomePage>("home", "/") {
                    head("Home title", description = "A greeting.")
                    HomePage("hello")
                }
                page<PostPage>("post", "/p/{slug}") {
                    if (params.getValue("slug") == "missing") throw PageMissingException(path)
                    PostPage(slug = params.getValue("slug"), title = "Entry")
                }
                page<FormPage>("form", "/form", methods = setOf(PageMethod.GET, PageMethod.POST)) {
                    head("Form page")
                    if (method == HttpMethod.Post) {
                        val body = receiveJson()
                        val name = body["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        if (name.isBlank()) {
                            throw PageValidationException(mapOf("name" to listOf("required")), FormPage(name))
                        }
                        throw PageRedirectException("/form")
                    }
                    FormPage("")
                }
                page<MissingPage>("missing", "/__not-found") { MissingPage(path) }
            }
            actions {
                action<EchoIn, EchoOut>("echo") { input ->
                    if (input.message.isBlank()) {
                        throw PageValidationException(mapOf("message" to listOf("required")), Unit)
                    }
                    EchoOut(input.message)
                }
                action<EchoIn, EchoOut>("hop") { input ->
                    withContext(Dispatchers.IO) {
                        val current = ActionRequest.current()
                        require(current.call === call)
                        EchoOut(input.message)
                    }
                }
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
        assertTrue(body.contains("/__keel/pack/harbor/bootstrap.js"))
        assertTrue(body.contains("/__keel/pack/harbor/pages/home.js"))
        assertTrue(body.contains("hello"))
        assertTrue(body.contains("Home title"))
        assertTrue(body.contains("<title"))
        assertTrue(body.contains("name=\"description\""))
        assertTrue(body.contains("A greeting."))
        assertTrue(body.contains("property=\"og:title\""))
        assertTrue(body.contains("property=\"og:type\""))
        assertTrue(body.contains("rel=\"canonical\""))
        assertTrue(body.contains("application/ld+json"))
        assertTrue(body.contains("<noscript>"))
        assertTrue(body.contains("rel=\"modulepreload\""))
        assertTrue(body.contains("data-keel-css"))
    }

    @Test
    fun `document GET with csp stamps one nonce on shell scripts and pack head assets`() = testApplication {
        writePack(
            heads = mapOf(
                "home" to """<link rel="icon" href="/favicon.svg" /><script src="/head.js"></script>""",
            ),
        )
        application { installSample(CspPolicy.nonce()) }
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        val header = response.headers["Content-Security-Policy"]
        assertTrue(!header.isNullOrBlank())
        val nonce = Regex("'nonce-([^']+)'").find(header!!)!!.groupValues[1]
        assertEquals(
            "script-src 'nonce-$nonce' 'strict-dynamic'; style-src 'self'; object-src 'none'; base-uri 'none'",
            header,
        )
        val body = response.bodyAsText()
        assertTrue(body.contains("id=\"__keel_seed\" nonce=\"$nonce\""), body)
        assertTrue(body.contains("src=\"/__keel/pack/harbor/bootstrap.js\" nonce=\"$nonce\""), body)
        assertTrue(body.contains("href=\"http://localhost/favicon.svg\" nonce=\"$nonce\""), body)
        assertTrue(body.contains("src=\"http://localhost/head.js\" nonce=\"$nonce\""), body)
        assertEquals(4, Regex("nonce=\"$nonce\"").findAll(body).count(), body)
    }

    @Test
    fun `visit carries no csp header and no nonce`() = testApplication {
        writePack(
            heads = mapOf(
                "home" to """<link rel="icon" href="/favicon.svg" /><script src="/head.js"></script>""",
            ),
        )
        application { installSample(CspPolicy.nonce()) }
        val response = client.get("/") {
            header(KeelHeaders.VISIT, "true")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(null, response.headers["Content-Security-Policy"])
        assertTrue(!response.bodyAsText().contains("nonce"))
    }

    @Test
    fun `without csp no header but documents still carry a nonce`() = testApplication {
        writePack()
        application { installSample() }
        val response = client.get("/")
        assertEquals(null, response.headers["Content-Security-Policy"])
        val body = response.bodyAsText()
        val nonce = Regex("nonce=\"([^\"]+)\"").find(body)!!.groupValues[1]
        assertTrue(nonce.isNotBlank())
        assertEquals(3, Regex("nonce=\"$nonce\"").findAll(body).count(), body)
    }

    @Test
    fun `document 422 keeps loader head`() = testApplication {
        writePack()
        application { installSample() }
        val response = client.post("/form") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertTrue(response.bodyAsText().contains("Form page"))
    }

    @Test
    fun `action success returns data envelope`() = testApplication {
        writePack()
        application { installSample() }
        val response = client.post("${Keel.ACTION_PATH}/echo") {
            contentType(ContentType.Application.Json)
            setBody("""{"message":"ping"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"data\""))
        assertTrue(body.contains("ping"))
        assertTrue(!body.contains("\"page\""))
    }

    @Test
    fun `action validation returns 422 errors envelope`() = testApplication {
        writePack()
        application { installSample() }
        val response = client.post("${Keel.ACTION_PATH}/echo") {
            contentType(ContentType.Application.Json)
            setBody("""{"message":""}""")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"errors\""))
        assertTrue(body.contains("message"))
        assertTrue(!body.contains("\"page\""))
    }

    @Test
    fun `unknown action returns 404`() = testApplication {
        writePack()
        application { installSample() }
        val response = client.post("${Keel.ACTION_PATH}/nope") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `schema is served without the pages DSL`() = testApplication {
        writePack()
        val bundle = FrontendBundle.fromDirectory(pack)
        application {
            keel { this.bundle = bundle }
        }
        val response = client.get(Keel.SCHEMA_PATH)
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"pages\""))
    }

    @Test
    fun `schema endpoint lists pages and actions`() = testApplication {
        writePack()
        application { installSample() }
        val response = client.get(Keel.SCHEMA_PATH)
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.headers["Content-Type"]?.contains("application/json") == true)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"format\": \"keel/1\""))
        assertTrue(body.contains("\"home\""))
        assertTrue(body.contains("\"path\": \"/\""))
        assertTrue(body.contains("\"echo\""))
        assertTrue(body.contains("\"in\": \"EchoIn\""))
        assertTrue(body.contains("\"types\""))
    }

    @Test
    fun `action ThreadLocal survives a dispatcher hop`() = testApplication {
        writePack()
        application { installSample() }
        val response = client.post("${Keel.ACTION_PATH}/hop") {
            contentType(ContentType.Application.Json)
            setBody("""{"message":"ok"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("ok"))
    }

    @Test
    fun `action with text plain is csrf forbidden`() = testApplication {
        writePack()
        application { installSample() }
        val response = client.post("${Keel.ACTION_PATH}/echo") {
            contentType(ContentType.Text.Plain)
            setBody("""{"message":"x"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue(response.bodyAsText().contains("\"errors\""))
    }

    @Test
    fun `visit only header filters seed data and sets partial`() = testApplication {
        writePack()
        application { installSample() }
        val response = client.get("/") {
            header(KeelHeaders.VISIT, "true")
            header(KeelHeaders.ONLY, "greeting")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("greeting", response.headers[KeelHeaders.PARTIAL])
        val seed = KeelJson.codec.decodeFromString(KeelSeed.serializer(), response.bodyAsText())
        assertEquals(setOf("greeting"), (seed.data as kotlinx.serialization.json.JsonObject).keys)
    }

    @Test
    fun `document get is not a partial`() = testApplication {
        writePack()
        application { installSample() }
        val response = client.get("/") {
            header(KeelHeaders.ONLY, "greeting")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(null, response.headers[KeelHeaders.PARTIAL])
        assertTrue(response.bodyAsText().contains("hello"))
    }

    @Test
    fun `post to a get-only page is 405`() = testApplication {
        writePack()
        application { installSample() }
        val response = client.post("/") {
            contentType(ContentType.Application.Json)
            setBody("{}")
            header(KeelHeaders.VISIT, "true")
        }
        assertEquals(HttpStatusCode.MethodNotAllowed, response.status)
    }

    @Test
    fun `pack asset honors etag`() = testApplication {
        writePack()
        application { installSample() }
        val first = client.get("/__keel/pack/harbor/assets/styles.css")
        assertEquals(HttpStatusCode.OK, first.status)
        val etag = first.headers["ETag"]
        assertTrue(!etag.isNullOrBlank())
        assertEquals("public, max-age=31536000, immutable", first.headers["Cache-Control"])
        val again = client.get("/__keel/pack/harbor/assets/styles.css") {
            header("If-None-Match", etag!!)
        }
        assertEquals(HttpStatusCode.NotModified, again.status)
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
        assertEquals("/__keel/pack/harbor/pages/post.js", seed.entry)
    }

    @Test
    fun `visit header on the page url returns the same seed`() = testApplication {
        writePack()
        application { installSample() }
        val viaProxy = client.get(Keel.NAVIGATE_PATH) {
            parameter("to", "/p/hello")
            header(KeelHeaders.VISIT, "true")
        }
        val viaPage = client.get("/p/hello") {
            header(KeelHeaders.VISIT, "true")
        }
        assertEquals(HttpStatusCode.OK, viaPage.status)
        val proxySeed = KeelJson.codec.decodeFromString(KeelSeed.serializer(), viaProxy.bodyAsText())
        val pageSeed = KeelJson.codec.decodeFromString(KeelSeed.serializer(), viaPage.bodyAsText())
        assertEquals(proxySeed.page, pageSeed.page)
        assertEquals(proxySeed.path, pageSeed.path)
        assertEquals(proxySeed.data, pageSeed.data)
        assertEquals(proxySeed.entry, pageSeed.entry)
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
    fun `visit post with empty json returns 422 errors`() = testApplication {
        writePack()
        application { installSample() }
        val response = client.post("/form") {
            contentType(ContentType.Application.Json)
            setBody("{}")
            header(KeelHeaders.VISIT, "true")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val seed = KeelJson.codec.decodeFromString(KeelSeed.serializer(), response.bodyAsText())
        assertEquals("form", seed.page)
        assertEquals(listOf("required"), seed.errors["name"])
    }

    @Test
    fun `visit post with valid json returns redirect`() = testApplication {
        writePack()
        application { installSample() }
        val response = client.post("/form") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"ok"}""")
            header(KeelHeaders.VISIT, "true")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val seed = KeelJson.codec.decodeFromString(KeelSeed.serializer(), response.bodyAsText())
        assertEquals("/form", seed.redirect)
    }

    @Test
    fun `document post with valid json includes redirect in the html seed`() = testApplication {
        writePack()
        application { installSample() }
        val response = client.post("/form") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"ok"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("id=\"__keel_seed\""))
        assertTrue(body.contains("\"redirect\":\"/form\""))
    }

    @Test
    fun `bundle loads from the pack directory`() {
        writePack()
        FrontendBundle.fromDirectory(pack).use { bundle ->
            assertEquals("harbor", bundle.manifest.id)
            assertEquals("pages/home.js", bundle.manifest.page("home")?.module)
        }
    }

    @Test
    fun `document and visit advertise the pack build hash`() = testApplication {
        writePack()
        application { installSample() }
        val expected = FrontendBundle.fromDirectory(pack).use { it.contentHash }
        assertTrue(expected.isNotBlank())

        val document = client.get("/")
        assertEquals(expected, document.headers[KeelHeaders.BUILD])
        assertTrue(document.bodyAsText().contains("\"build\":\"$expected\""))

        val visit = client.get("/") {
            header(KeelHeaders.VISIT, "true")
        }
        assertEquals(expected, visit.headers[KeelHeaders.BUILD])
        val seed = KeelJson.codec.decodeFromString(KeelSeed.serializer(), visit.bodyAsText())
        assertEquals(expected, seed.build)
        assertEquals("0.1.0", seed.theme.version)
    }

    @Test
    fun `reloadBundles swaps new content without restarting`() = testApplication {
        writePack()
        var engine: KeelEngine? = null
        application {
            installSample(watchPacks = true)
            engine = attributes.getOrNull(KeelEngineKey)
        }
        startApplication()
        assertTrue(engine != null)
        val keel = engine!!
        val before = client.get("/").headers[KeelHeaders.BUILD]

        pack.resolve("pages/home.js").writeText("export async function mount() { /* v2 */ }")
        pack.resolve("manifest.json").writeText(
            pack.resolve("manifest.json").readText().replace("\"0.1.0\"", "\"0.2.0\""),
        )
        val reloaded = keel.reloadBundles()
        assertEquals(listOf("harbor"), reloaded)

        val document = client.get("/")
        val after = document.headers[KeelHeaders.BUILD]
        assertTrue(!after.isNullOrBlank())
        assertTrue(before != after)
        val entry = client.get("/__keel/pack/harbor/pages/home.js")
        assertTrue(entry.bodyAsText().contains("v2"), entry.bodyAsText())

        val visit = client.get("/") {
            header(KeelHeaders.VISIT, "true")
        }
        val seed = KeelJson.codec.decodeFromString(KeelSeed.serializer(), visit.bodyAsText())
        assertEquals("0.2.0", seed.theme.version)
        assertEquals(after, seed.build)
    }

    @Test
    fun `failed reload keeps the previous bundle serving`() = testApplication {
        writePack()
        var engine: KeelEngine? = null
        application {
            installSample(watchPacks = true)
            engine = attributes.getOrNull(KeelEngineKey)
        }
        startApplication()
        val before = client.get("/").headers[KeelHeaders.BUILD]

        val keel = engine!!
        pack.resolve("manifest.json").writeText("{ not json")
        assertTrue(keel.reloadBundles().isEmpty())
        assertEquals(before, client.get("/").headers[KeelHeaders.BUILD])
        assertEquals(HttpStatusCode.OK, client.get("/").status)

        writePack()
        pack.resolve("pages/home.js").writeText("export async function mount() { /* v3 */ }")
        assertEquals(listOf("harbor"), keel.reloadBundles())
        assertTrue(client.get("/__keel/pack/harbor/pages/home.js").bodyAsText().contains("v3"))
        assertTrue(before != client.get("/").headers[KeelHeaders.BUILD])
    }

    @Test
    fun `respondPage on a custom route returns html and json visit`() = testApplication {
        writePack()
        val bundle = FrontendBundle.fromDirectory(pack)
        application {
            keel {
                this.bundle = bundle
                title = "Harbor"
            }
            routing {
                get("/alt") {
                    call.respondPage(bundle, "home", HomePage("hello"))
                }
            }
        }
        val html = client.get("/alt")
        assertEquals(HttpStatusCode.OK, html.status)
        val body = html.bodyAsText()
        assertTrue(body.contains("id=\"__keel_seed\""))
        assertTrue(body.contains("/__keel/pack/harbor/pages/home.js"))
        assertTrue(body.contains("hello"))

        val json = client.get("/alt") {
            header(KeelHeaders.VISIT, "true")
        }
        val seed = KeelJson.codec.decodeFromString(KeelSeed.serializer(), json.bodyAsText())
        assertEquals("home", seed.page)
        assertEquals("/alt", seed.path)
        assertEquals("/__keel/pack/harbor/pages/home.js", seed.entry)
    }

    @Test
    fun `two bundles have distinct asset prefixes`() = testApplication {
        writePack(
            dir = pack,
            id = "shop",
            pages = mapOf("shop.item" to "pages/item.js"),
            notFound = null,
        )
        writePack(
            dir = extra,
            id = "admin",
            pages = mapOf("admin.home" to "pages/home.js"),
            notFound = null,
        )
        val shop = FrontendBundle.fromDirectory(pack)
        val admin = FrontendBundle.fromDirectory(extra)
        application {
            keel {
                bundles = listOf(shop, admin)
                title = "Host"
            }
            routing {
                keel(shop) {
                    get("/shop") {
                        call.respondPage("shop.item", ShopItem("sku-1"))
                    }
                }
                keel(admin) {
                    get("/admin") {
                        call.respondPage("admin.home", AdminHome("ok"))
                    }
                }
            }
        }
        val shopBody = client.get("/shop").bodyAsText()
        assertTrue(shopBody.contains("/__keel/pack/shop/pages/item.js"))
        assertTrue(shopBody.contains("/__keel/pack/shop/bootstrap.js"))
        val adminBody = client.get("/admin").bodyAsText()
        assertTrue(adminBody.contains("/__keel/pack/admin/pages/home.js"))
        assertTrue(adminBody.contains("/__keel/pack/admin/bootstrap.js"))

        val shopAsset = client.get("/__keel/pack/shop/pages/item.js")
        assertEquals(HttpStatusCode.OK, shopAsset.status)
        val adminAsset = client.get("/__keel/pack/admin/bootstrap.js")
        assertEquals(HttpStatusCode.OK, adminAsset.status)
        assertEquals("export {}", adminAsset.bodyAsText())
    }

    @Test
    fun `explicit pack wins over route scope`() = testApplication {
        writePack(dir = pack, id = "shop", pages = mapOf("shop.item" to "pages/item.js"), notFound = null)
        writePack(dir = extra, id = "admin", pages = mapOf("admin.home" to "pages/home.js"), notFound = null)
        val shop = FrontendBundle.fromDirectory(pack)
        val admin = FrontendBundle.fromDirectory(extra)
        application {
            keel {
                bundles = listOf(shop, admin)
                title = "Host"
            }
            routing {
                keel(admin) {
                    get("/explicit") {
                        call.respondPage(shop, "shop.item", ShopItem("sku-1"))
                    }
                }
            }
        }
        val body = client.get("/explicit").bodyAsText()
        assertTrue(body.contains("/__keel/pack/shop/pages/item.js"))
        assertTrue(!body.contains("/__keel/pack/admin/"))
    }

    @Test
    fun `bundle-less respondPage resolves the single configured pack`() = testApplication {
        writePack()
        val bundle = FrontendBundle.fromDirectory(pack)
        application {
            keel {
                this.bundle = bundle
                title = "Harbor"
            }
            routing {
                get("/alt") {
                    call.respondPage("home", HomePage("hello"))
                }
            }
        }
        val html = client.get("/alt")
        assertEquals(HttpStatusCode.OK, html.status)
        val body = html.bodyAsText()
        assertTrue(body.contains("/__keel/pack/harbor/pages/home.js"))
        assertTrue(body.contains("hello"))

        val visit = client.get("/alt") {
            header(KeelHeaders.VISIT, "true")
        }
        val seed = KeelJson.codec.decodeFromString(KeelSeed.serializer(), visit.bodyAsText())
        assertEquals("home", seed.page)
        assertEquals("/__keel/pack/harbor/pages/home.js", seed.entry)
    }

    @Test
    fun `bundle-less respondPage without a pack fails clearly`() = testApplication {
        var failure: Throwable? = null
        application {
            keel { }
            routing {
                get("/orphan") {
                    failure = runCatching { call.respondPage("home", HomePage("hello")) }.exceptionOrNull()
                    call.respondText("handled")
                }
            }
        }
        assertEquals(HttpStatusCode.OK, client.get("/orphan").status)
        assertTrue(failure is MissingPackException)
        assertTrue(failure?.message?.contains("route.keel(pack)") == true)
    }

    @Test
    fun `bundle-less respondPage with multiple packs asks for a scope`() = testApplication {
        writePack(dir = pack, id = "shop", pages = mapOf("shop.item" to "pages/item.js"), notFound = null)
        writePack(dir = extra, id = "admin", pages = mapOf("admin.home" to "pages/home.js"), notFound = null)
        val shop = FrontendBundle.fromDirectory(pack)
        val admin = FrontendBundle.fromDirectory(extra)
        var failure: Throwable? = null
        application {
            keel {
                bundles = listOf(shop, admin)
                title = "Host"
            }
            routing {
                get("/orphan") {
                    failure = runCatching { call.respondPage("shop.item", ShopItem("sku-1")) }.exceptionOrNull()
                    call.respondText("handled")
                }
            }
        }
        assertEquals(HttpStatusCode.OK, client.get("/orphan").status)
        assertTrue(failure is AmbiguousPackException)
        assertTrue(failure?.message?.contains("shop, admin") == true)
    }

    @Test
    fun `single configured pack must implement the page`() = testApplication {
        writePack(dir = pack, id = "shop", pages = mapOf("shop.item" to "pages/item.js"), notFound = null)
        val shop = FrontendBundle.fromDirectory(pack)
        var failure: Throwable? = null
        application {
            keel {
                this.bundle = shop
                title = "Host"
            }
            routing {
                get("/orphan") {
                    failure = runCatching { call.respondPage("home", HomePage("hello")) }.exceptionOrNull()
                    call.respondText("handled")
                }
            }
        }
        assertEquals(HttpStatusCode.OK, client.get("/orphan").status)
        assertTrue(failure is UnknownPageInBundleException)
    }
}
