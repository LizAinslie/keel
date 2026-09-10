package dev.kolektiv.keel.bundle

/**
 * Reject zip-slip and absolute paths. Returns a relative path with `/`
 * separators, or null if [path] must not be served.
 */
internal fun normalizeEntryPath(path: String): String? {
    if (path.isEmpty() || '\u0000' in path) return null
    val unified = path.replace('\\', '/')
    if (unified.startsWith("/") || unified.startsWith("./")) return null
    val parts = unified.split('/')
    if (parts.isEmpty() || parts.any { it.isEmpty() || it == "." || it == ".." }) return null
    if (parts[0].endsWith(":")) return null
    return parts.joinToString("/")
}

internal fun requireEntryPath(path: String): String =
    normalizeEntryPath(path) ?: throw UnsafeBundleEntryException(path)
