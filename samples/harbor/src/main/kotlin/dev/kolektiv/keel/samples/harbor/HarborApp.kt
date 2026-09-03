package dev.kolektiv.keel.samples.harbor

import dev.kolektiv.keel.ktor.PageMissingException
import dev.kolektiv.keel.ktor.SharedProvider
import dev.kolektiv.keel.ktor.keel
import io.ktor.server.application.Application
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Path

fun Application.harbor(packDir: Path) {
    keel {
        this.packDir = packDir
        title = "Harbor"
        notFoundPageId = "harbor.notFound"
        shared = SharedProvider { _, _, _, _ ->
            buildJsonObject { put("site", "Harbor") }
        }
        pages {
            page<HomePage>("harbor.home", "/") {
                HomePage(
                    kicker = "Keel sample",
                    title = "Harbor journal",
                    lede = "A one-pack MPA. The host owns the routes. The Svelte pack implements the ids.",
                    posts = Journal.entries.map { it.summary() },
                )
            }
            page<ListPage>("harbor.list", "/posts") {
                val q = query["q"].orEmpty()
                ListPage(
                    title = if (q.isBlank()) "All entries" else "Search",
                    query = q,
                    posts = Journal.search(q).map { it.summary() },
                )
            }
            page<PostPage>("harbor.post", "/p/{slug}") {
                val entry = Journal.bySlug(params.getValue("slug"))
                    ?: throw PageMissingException(path)
                PostPage(
                    slug = entry.slug,
                    title = entry.title,
                    body = entry.body,
                    published = entry.published,
                )
            }
            page<AboutPage>("harbor.about", "/about") {
                AboutPage(
                    title = "About Harbor",
                    copy = "Harbor is the test host for Keel: Ktor binds the registry, Vite bundles a Svelte pack, visits go through /__keel/navigate.",
                    pack = "harbor@0.1.0",
                )
            }
            page<NotFoundPage>("harbor.notFound", "/__not-found") {
                NotFoundPage(path = path)
            }
        }
    }
}
