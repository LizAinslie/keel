package dev.kolektiv.keel.page

/**
 * Keel path grammar, independent of any HTTP framework:
 *
 * - `{name}` required single segment
 * - `{name?}` optional trailing segment
 * - `{name...}` tailcard (remainder, possibly empty)
 * - anything else is a literal segment
 *
 * Tailcards and optionals must be last. Duplicate names are rejected. Paths
 * must be absolute.
 */
class PathPattern private constructor(
    val source: String,
    internal val segments: List<Segment>,
) {
    sealed class Segment {
        data class Literal(val value: String) : Segment()
        data class Param(val name: String, val kind: Kind) : Segment()
        enum class Kind { REQUIRED, OPTIONAL, TAIL }
    }

    class Specificity internal constructor(internal val ranks: IntArray) : Comparable<Specificity> {
        override fun compareTo(other: Specificity): Int {
            val n = maxOf(ranks.size, other.ranks.size)
            for (i in 0 until n) {
                val a = ranks.getOrElse(i) { PAD }
                val b = other.ranks.getOrElse(i) { PAD }
                if (a != b) return a.compareTo(b)
            }
            return 0
        }

        override fun equals(other: Any?): Boolean =
            other is Specificity && ranks.contentEquals(other.ranks)

        override fun hashCode(): Int = ranks.contentHashCode()

        companion object {
            /** A pattern that ended is more specific than one that still captures. */
            private const val PAD = 5
        }
    }

    val specificity: Specificity = Specificity(
        IntArray(segments.size) { i ->
            when (val segment = segments[i]) {
                is Segment.Literal -> 4
                is Segment.Param -> when (segment.kind) {
                    Segment.Kind.REQUIRED -> 3
                    Segment.Kind.OPTIONAL -> 2
                    Segment.Kind.TAIL -> 1
                }
            }
        },
    )

    fun matches(pathname: String): Boolean = params(pathname) != null

    fun params(pathname: String): Map<String, String>? {
        val parts = splitPath(pathname)
        val out = linkedMapOf<String, String>()
        var index = 0
        for (segment in segments) {
            when (segment) {
                is Segment.Literal -> {
                    if (index >= parts.size || parts[index] != segment.value) return null
                    index += 1
                }
                is Segment.Param -> when (segment.kind) {
                    Segment.Kind.REQUIRED -> {
                        if (index >= parts.size) return null
                        out[segment.name] = parts[index]
                        index += 1
                    }
                    Segment.Kind.OPTIONAL -> {
                        if (index < parts.size) {
                            out[segment.name] = parts[index]
                            index += 1
                        } else {
                            out[segment.name] = ""
                        }
                    }
                    Segment.Kind.TAIL -> {
                        out[segment.name] = parts.drop(index).joinToString("/")
                        index = parts.size
                    }
                }
            }
        }
        if (index != parts.size) return null
        return out
    }

    companion object {
        private val NAME = Regex("^[A-Za-z_][A-Za-z0-9_]*$")

        fun parse(path: String): PathPattern {
            require(path.startsWith("/")) { "path must be absolute, got '$path'" }
            val parts = splitPath(path)
            val segments = ArrayList<Segment>(parts.size)
            val names = HashSet<String>()
            for ((i, part) in parts.withIndex()) {
                val last = i == parts.lastIndex
                val segment = parseSegment(part, last)
                if (segment is Segment.Param) {
                    require(names.add(segment.name)) { "duplicate path parameter '${segment.name}' in '$path'" }
                }
                segments.add(segment)
            }
            return PathPattern(path, segments)
        }

        private fun parseSegment(part: String, last: Boolean): Segment {
            if (!part.startsWith("{") || !part.endsWith("}")) {
                return Segment.Literal(part)
            }
            val inner = part.removePrefix("{").removeSuffix("}")
            val (name, kind) = when {
                inner.endsWith("...") -> inner.removeSuffix("...") to Segment.Kind.TAIL
                inner.endsWith("?") -> inner.removeSuffix("?") to Segment.Kind.OPTIONAL
                else -> inner to Segment.Kind.REQUIRED
            }
            require(NAME.matches(name)) { "invalid path parameter '$name'" }
            if (kind == Segment.Kind.OPTIONAL || kind == Segment.Kind.TAIL) {
                require(last) { "${kind.name.lowercase()} parameter {$name} must be the last segment" }
            }
            return Segment.Param(name, kind)
        }

        internal fun splitPath(path: String): List<String> {
            if (path.isEmpty() || path == "/") return listOf("")
            return path.trimEnd('/').split('/')
        }
    }
}
