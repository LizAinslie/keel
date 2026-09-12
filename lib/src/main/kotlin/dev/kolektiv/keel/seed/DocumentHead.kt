package dev.kolektiv.keel.seed

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * Pack-defines / host-renders the document head.
 *
 * The pack ships an HTML template (compiled from `+head.svelte` or equivalent).
 * The host substitutes `{{path}}` from the seed, allowlists tags/attrs, drops
 * unsafe URLs, and never throws on a document GET.
 */
object DocumentHead {
    const val HEAD_ATTR: String = "data-keel-head"

    private val placeholder = Regex("""\{\{\s*([A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)*)\s*\}\}""")

    private val allowedTags = setOf("title", "meta", "link", "script")
    private val voidTags = setOf("meta", "link")
    private val urlAttrs = setOf("href", "src")
    private val allowedAttrs = mapOf(
        "title" to emptySet(),
        "meta" to setOf("name", "property", "content", "media"),
        "link" to setOf("rel", "href", "type", "as", "crossorigin", "hreflang", "sizes", "media", "title"),
        "script" to setOf("src", "type", "async", "defer", "nomodule", "crossorigin"),
    )

    fun resolve(
        packHtml: String?,
        host: PageHead?,
        seed: KeelSeed,
        documentUrl: String?,
    ): PageHead? {
        if (packHtml.isNullOrBlank()) {
            return host?.let { fillCanonical(it, documentUrl) }
        }
        val root = lookupRoot(seed)
        val tags = parse(packHtml).mapNotNull { tag -> substituteTag(tag, root, documentUrl) }
        val html = emit(tags).ifBlank { null }
        val title = textOf(tags, "title") ?: host?.title
        val description = meta(tags, "name", "description") ?: host?.description
        val canonical = attrOf(tags, "link", "rel", "canonical", "href") ?: host?.canonical
        val image = meta(tags, "property", "og:image") ?: host?.image
        val type = meta(tags, "property", "og:type") ?: host?.type
        if (title == null && description == null && canonical == null && html == null && host == null) {
            return null
        }
        return PageHead(
            title = title.orEmpty(),
            description = description,
            canonical = canonical?.let { absolutize(it, documentUrl) } ?: documentUrl,
            image = image?.let { absolutize(it, documentUrl) },
            type = type,
            html = html,
        )
    }

    fun substitute(template: String, root: JsonElement): String? {
        var failed = false
        val result = placeholder.replace(template) { match ->
            val value = lookup(root, match.groupValues[1].split('.'))
            if (value == null) {
                failed = true
                ""
            } else {
                value
            }
        }
        if (failed) return null
        return result
    }

    internal fun lookupRoot(seed: KeelSeed): JsonObject = buildJsonObject {
        put("page", seed.page)
        put("path", seed.path)
        put(
            "params",
            buildJsonObject {
                for ((key, value) in seed.params) put(key, value)
            },
        )
        put("data", seed.data)
        seed.shared?.let { put("shared", it) }
        put(
            "theme",
            buildJsonObject {
                put("id", seed.theme.id)
                put("version", seed.theme.version)
            },
        )
    }

    internal fun lookup(root: JsonElement, path: List<String>): String? {
        if (path.isEmpty()) return null
        var current: JsonElement = root
        for (segment in path) {
            current = when (current) {
                is JsonObject -> current[segment] ?: return null
                is JsonArray -> {
                    val index = segment.toIntOrNull() ?: return null
                    current.getOrNull(index) ?: return null
                }
                else -> return null
            }
        }
        return when (current) {
            is JsonNull -> null
            is JsonPrimitive -> current.contentOrNull ?: current.content
            else -> null
        }
    }

    internal fun sanitizeUrl(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        val lower = trimmed.lowercase()
        if (lower.startsWith("javascript:") ||
            lower.startsWith("data:") ||
            lower.startsWith("vbscript:") ||
            lower.startsWith("blob:")
        ) {
            return null
        }
        if (trimmed.startsWith("/") || lower.startsWith("https:") || lower.startsWith("http:")) {
            return trimmed
        }
        return null
    }

    internal fun originOf(documentUrl: String): String? {
        val schemeEnd = documentUrl.indexOf("://")
        if (schemeEnd < 0) return null
        val pathStart = documentUrl.indexOf('/', schemeEnd + 3)
        return if (pathStart < 0) documentUrl else documentUrl.substring(0, pathStart)
    }

    internal fun absolutize(url: String, documentUrl: String?): String? {
        val safe = sanitizeUrl(url) ?: return null
        if (safe.startsWith("http://") || safe.startsWith("https://")) return safe
        val origin = documentUrl?.let { originOf(it) } ?: return safe
        return origin + safe
    }

    internal data class Tag(
        val name: String,
        val attrs: LinkedHashMap<String, String>,
        val text: String? = null,
    )

    internal fun parse(html: String): List<Tag> {
        val tags = mutableListOf<Tag>()
        var i = 0
        while (i < html.length) {
            val start = html.indexOf('<', i)
            if (start < 0) break
            if (html.startsWith("<!--", start)) {
                val end = html.indexOf("-->", start + 4)
                i = if (end < 0) html.length else end + 3
                continue
            }
            if (html.startsWith("</", start)) {
                val end = html.indexOf('>', start)
                i = if (end < 0) html.length else end + 1
                continue
            }
            val parsed = parseTag(html, start) ?: break
            tags.add(parsed.tag)
            i = parsed.end
        }
        return tags
    }

    private data class Parsed(val tag: Tag, val end: Int)

    private fun parseTag(html: String, start: Int): Parsed? {
        if (start >= html.length || html[start] != '<') return null
        var i = start + 1
        if (i >= html.length || html[i] == '/' || html[i] == '!') return null
        val nameStart = i
        while (i < html.length && html[i].isLetterOrDigit()) i += 1
        val name = html.substring(nameStart, i).lowercase()
        val attrs = LinkedHashMap<String, String>()
        i = skipWs(html, i)
        while (i < html.length && html[i] != '>' && html[i] != '/') {
            val attrStart = i
            while (i < html.length && (html[i].isLetterOrDigit() || html[i] == '-' || html[i] == ':')) i += 1
            if (i == attrStart) break
            val attrName = html.substring(attrStart, i).lowercase()
            i = skipWs(html, i)
            var value = ""
            if (i < html.length && html[i] == '=') {
                i = skipWs(html, i + 1)
                if (i < html.length && (html[i] == '"' || html[i] == '\'')) {
                    val quote = html[i]
                    val close = html.indexOf(quote, i + 1)
                    if (close < 0) return null
                    value = html.substring(i + 1, close)
                    i = close + 1
                } else {
                    val valueStart = i
                    while (i < html.length && !html[i].isWhitespace() && html[i] != '>' && html[i] != '/') i += 1
                    value = html.substring(valueStart, i)
                }
            }
            attrs[attrName] = value
            i = skipWs(html, i)
        }
        var selfClose = false
        if (i < html.length && html[i] == '/') {
            selfClose = true
            i += 1
        }
        if (i >= html.length || html[i] != '>') return null
        i += 1
        if (name in voidTags || selfClose) {
            return Parsed(Tag(name, attrs), i)
        }
        val close = html.indexOf("</$name>", i, ignoreCase = true)
        if (close < 0) return Parsed(Tag(name, attrs, ""), i)
        val text = html.substring(i, close)
        return Parsed(Tag(name, attrs, text), close + name.length + 3)
    }

    private fun skipWs(html: String, start: Int): Int {
        var i = start
        while (i < html.length && html[i].isWhitespace()) i += 1
        return i
    }

    private fun substituteTag(tag: Tag, root: JsonElement, documentUrl: String?): Tag? {
        if (tag.name !in allowedTags) return null
        val allowed = allowedAttrs[tag.name] ?: return null
        val attrs = LinkedHashMap<String, String>()
        for ((rawName, rawValue) in tag.attrs) {
            if (rawName.startsWith("on")) return null
            if (rawName !in allowed) continue
            val value = substitute(rawValue, root) ?: return null
            val next = if (rawName in urlAttrs || isUrlMeta(tag, rawName, value)) {
                absolutize(value, documentUrl) ?: return null
            } else {
                value
            }
            attrs[rawName] = next
        }
        if (tag.name == "script") {
            val src = attrs["src"] ?: return null
            if (src.isBlank()) return null
            val inner = tag.text?.trim().orEmpty()
            if (inner.isNotEmpty()) return null
            return Tag(tag.name, attrs, "")
        }
        val text = tag.text?.let { substitute(it, root) ?: return null }
        if (tag.name == "title" && text.isNullOrBlank()) return null
        return Tag(tag.name, attrs, text)
    }

    private fun isUrlMeta(tag: Tag, attr: String, value: String): Boolean {
        if (tag.name != "meta" || attr != "content") return false
        val key = tag.attrs["property"] ?: tag.attrs["name"] ?: return false
        if (key == "og:image" || key == "og:url" || key == "twitter:image") return true
        return value.startsWith("/") || value.startsWith("http:") || value.startsWith("https:")
    }

    private fun emit(tags: List<Tag>): String = buildString {
        for (tag in tags) {
            append('<').append(tag.name)
            append(' ').append(HEAD_ATTR).append("=\"\"")
            for ((name, value) in tag.attrs) {
                append(' ').append(name)
                if (value.isEmpty() && name in setOf("async", "defer", "nomodule")) continue
                append("=\"").append(escapeAttr(value)).append('"')
            }
            if (tag.name in voidTags) {
                append('>')
                append('\n')
                continue
            }
            append('>')
            if (tag.text != null) append(escapeHtml(tag.text))
            append("</").append(tag.name).append('>')
            append('\n')
        }
    }

    private fun fillCanonical(head: PageHead, documentUrl: String?): PageHead {
        val canonical = head.canonical?.let { absolutize(it, documentUrl) } ?: head.canonical ?: documentUrl
        val image = head.image?.let { absolutize(it, documentUrl) } ?: head.image
        return head.copy(canonical = canonical, image = image)
    }

    private fun textOf(tags: List<Tag>, name: String): String? =
        tags.firstOrNull { it.name == name }?.text?.trim()?.takeIf { it.isNotEmpty() }

    private fun meta(tags: List<Tag>, attr: String, key: String): String? =
        tags.firstOrNull { it.name == "meta" && it.attrs[attr].equals(key, ignoreCase = true) }
            ?.attrs?.get("content")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun attrOf(tags: List<Tag>, tag: String, matchAttr: String, matchValue: String, take: String): String? =
        tags.firstOrNull { it.name == tag && it.attrs[matchAttr].equals(matchValue, ignoreCase = true) }
            ?.attrs?.get(take)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    internal fun escapeHtml(value: String): String = buildString(value.length) {
        for (ch in value) {
            when (ch) {
                '&' -> append("&#38;")
                '<' -> append("&#60;")
                '>' -> append("&#62;")
                else -> append(ch)
            }
        }
    }

    internal fun escapeAttr(value: String): String = buildString(value.length) {
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
