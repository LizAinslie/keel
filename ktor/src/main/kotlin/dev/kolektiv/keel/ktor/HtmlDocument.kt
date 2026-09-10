package dev.kolektiv.keel.ktor

import dev.kolektiv.keel.Keel
import dev.kolektiv.keel.KeelJson
import dev.kolektiv.keel.seed.KeelSeed
import dev.kolektiv.keel.seed.PageHead
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object HtmlDocument {
    const val HEAD_ATTR: String = "data-keel-head"

    fun render(title: String, seedJson: String, seed: KeelSeed, bootstrapUrl: String): String {
        val css = seed.css.joinToString("\n") { href ->
            """<link rel="stylesheet" href="${escapeAttr(href)}">"""
        }
        val rootId = Keel.DEFAULT_HOST.removePrefix("#")
        val documentTitle = seed.head?.title ?: title
        return buildString {
            appendLine("<!doctype html>")
            appendLine("<html lang=\"en\">")
            appendLine("<head>")
            appendLine("<meta charset=\"utf-8\">")
            appendLine("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
            appendHeadTags(this, documentTitle, seed.head, seed.path)
            if (css.isNotEmpty()) {
                appendLine(css)
            }
            appendLine("</head>")
            appendLine("<body>")
            append("<div id=\"")
            append(rootId)
            append("\">")
            appendNoscript(this, documentTitle, seed.head)
            appendLine("</div>")
            append("<script type=\"application/json\" id=\"")
            append(Keel.SEED_ELEMENT_ID)
            append("\">")
            append(seedJson)
            appendLine("</script>")
            append("<script type=\"module\" src=\"")
            append(escapeAttr(bootstrapUrl))
            appendLine("\"></script>")
            appendLine("</body>")
            appendLine("</html>")
        }
    }

    private fun appendHeadTags(out: StringBuilder, documentTitle: String, head: PageHead?, path: String) {
        tagged(out, "title", emptyMap(), escapeHtml(documentTitle))
        if (head == null) return
        val description = head.description
        if (description != null) {
            emptyTag(out, "meta", mapOf("name" to "description", "content" to description))
            emptyTag(out, "meta", mapOf("property" to "og:description", "content" to description))
            emptyTag(out, "meta", mapOf("name" to "twitter:description", "content" to description))
        }
        val canonical = head.canonical ?: path
        emptyTag(out, "link", mapOf("rel" to "canonical", "href" to canonical))
        emptyTag(out, "meta", mapOf("property" to "og:url", "content" to canonical))
        emptyTag(out, "meta", mapOf("property" to "og:title", "content" to head.title))
        emptyTag(out, "meta", mapOf("name" to "twitter:title", "content" to head.title))
        val ogType = head.type ?: "website"
        emptyTag(out, "meta", mapOf("property" to "og:type", "content" to ogType))
        val image = head.image
        if (image != null) {
            emptyTag(out, "meta", mapOf("property" to "og:image", "content" to image))
            emptyTag(out, "meta", mapOf("name" to "twitter:image", "content" to image))
            emptyTag(out, "meta", mapOf("name" to "twitter:card", "content" to "summary_large_image"))
        } else {
            emptyTag(out, "meta", mapOf("name" to "twitter:card", "content" to "summary"))
        }
        val ld = buildJsonObject {
            put("@context", "https://schema.org")
            put("@type", "WebPage")
            put("name", head.title)
            if (description != null) put("description", description)
            put("url", canonical)
        }
        tagged(
            out,
            "script",
            mapOf("type" to "application/ld+json"),
            encodeSeedJson(KeelJson.codec.encodeToString(ld)),
        )
    }

    private fun appendNoscript(out: StringBuilder, documentTitle: String, head: PageHead?) {
        out.append("<noscript>")
        out.append("<h1>")
        out.append(escapeHtml(documentTitle))
        out.append("</h1>")
        val description = head?.description
        if (description != null) {
            out.append("<p>")
            out.append(escapeHtml(description))
            out.append("</p>")
        }
        out.append("</noscript>")
    }

    private fun tagged(out: StringBuilder, tag: String, attrs: Map<String, String>, inner: String) {
        out.append('<').append(tag)
        out.append(" ").append(HEAD_ATTR).append("=\"\"")
        for ((key, value) in attrs) {
            out.append(' ').append(key).append("=\"").append(escapeAttr(value)).append('"')
        }
        out.append('>')
        out.append(inner)
        out.append("</").append(tag).appendLine('>')
    }

    private fun emptyTag(out: StringBuilder, tag: String, attrs: Map<String, String>) {
        out.append('<').append(tag)
        out.append(" ").append(HEAD_ATTR).append("=\"\"")
        for ((key, value) in attrs) {
            out.append(' ').append(key).append("=\"").append(escapeAttr(value)).append('"')
        }
        out.appendLine('>')
    }

    fun encodeSeedJson(raw: String): String =
        raw.replace("<", "\\u003c")
            .replace("\u2028", "\\u2028")
            .replace("\u2029", "\\u2029")

    private fun escapeHtml(value: String): String = buildString(value.length) {
        for (ch in value) {
            when (ch) {
                '&' -> append("&#38;")
                '<' -> append("&#60;")
                '>' -> append("&#62;")
                else -> append(ch)
            }
        }
    }

    private fun escapeAttr(value: String): String = buildString(value.length) {
        for (ch in value) {
            when (ch) {
                '&' -> append("&#38;")
                '<' -> append("&#60;")
                '>' -> append("&#62;")
                '"' -> append("&#34;")
                else -> append(ch)
            }
        }
    }
}
