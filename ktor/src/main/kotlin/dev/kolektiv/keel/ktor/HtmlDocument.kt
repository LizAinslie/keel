package dev.kolektiv.keel.ktor

import dev.kolektiv.keel.Keel
import dev.kolektiv.keel.seed.KeelSeed

internal object HtmlDocument {
    fun render(title: String, seedJson: String, seed: KeelSeed, bootstrapUrl: String): String {
        val css = seed.css.joinToString("\n") { href ->
            """<link rel="stylesheet" href="${escapeAttr(href)}">"""
        }
        val rootId = Keel.DEFAULT_HOST.removePrefix("#")
        return buildString {
            appendLine("<!doctype html>")
            appendLine("<html lang=\"en\">")
            appendLine("<head>")
            appendLine("<meta charset=\"utf-8\">")
            appendLine("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
            append("<title>")
            append(escapeHtml(title))
            appendLine("</title>")
            if (css.isNotEmpty()) {
                appendLine(css)
            }
            appendLine("</head>")
            appendLine("<body>")
            append("<div id=\"")
            append(rootId)
            appendLine("\"></div>")
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
