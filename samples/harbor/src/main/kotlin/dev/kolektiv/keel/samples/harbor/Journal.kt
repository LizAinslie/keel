package dev.kolektiv.keel.samples.harbor

data class Entry(
    val slug: String,
    val title: String,
    val excerpt: String,
    val body: String,
    val published: String,
)

object Journal {
    val entries: List<Entry> = listOf(
        Entry(
            slug = "first-watch",
            title = "First watch",
            excerpt = "The host owns the channel. Packs answer by id.",
            published = "2026-09-01",
            body = """
                Harbor is a Keel host with one Svelte pack. The Ktor process
                binds every URL. The pack implements page ids — home, list,
                post, about — and never mentions a path pattern.

                A visit hits /__keel/navigate. The seed in the response is the
                only payload the pack reads.
            """.trimIndent(),
        ),
        Entry(
            slug = "theme-chain",
            title = "A chain of one",
            excerpt = "One pack is an app. Many packs are themes. Same protocol.",
            published = "2026-09-02",
            body = """
                ChainThemeResolver walks installed manifests until one implements
                the page id. Harbor ships a single pack, so the chain is length
                one. Tomorrow that pack can sit beside a second theme that
                implements the same ids against the same payload types.
            """.trimIndent(),
        ),
        Entry(
            slug = "launching",
            title = "Launching",
            excerpt = "Routes are live. The journal is the contract in motion.",
            published = "2026-09-03",
            body = """
                Three public pages plus a not-found id. Payload types live in
                Kotlin. The Svelte files import matching TypeScript shapes.
                Typegen is next; until then the types are kept in lockstep by
                hand, which is honest for a sample this small.
            """.trimIndent(),
        ),
    )

    fun bySlug(slug: String): Entry? = entries.find { it.slug == slug }

    fun search(q: String?): List<Entry> {
        val needle = q?.trim()?.lowercase().orEmpty()
        if (needle.isEmpty()) return entries
        return entries.filter {
            it.title.lowercase().contains(needle) || it.body.lowercase().contains(needle)
        }
    }
}
