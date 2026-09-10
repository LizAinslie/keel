package dev.kolektiv.keel.page

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/**
 * A page the host knows how to load. [id] is the contract key themes implement.
 * [path] is a Keel pattern (`/p/{slug}`, `/p/{slug?}`, `/p/{rest...}`), owned
 * by the server. [methods] defaults to GET; POST is opt-in.
 */
data class PageBinding(
    val id: String,
    val path: String,
    val serializer: KSerializer<*>,
    val methods: Set<PageMethod> = setOf(PageMethod.GET),
) {
    val pattern: PathPattern = PathPattern.parse(path)
}

class DuplicatePageException(id: String) : IllegalStateException("page '$id' is already registered")

open class UnknownPageException(
    id: String,
    message: String = "unknown page '$id'",
) : NoSuchElementException(message) {
    val pageId: String = id
}

/**
 * Source of truth for page ids and payload serializers.
 *
 * Ktor (or any host) walks this registry to bind routes. Typegen walks it to
 * emit `.d.ts`. This module does not depend on Ktor so renderers and CLI
 * tools can reuse it.
 */
class PageRegistry {
    private val byId = linkedMapOf<String, PageBinding>()
    private val byPath = linkedMapOf<String, PageBinding>()

    val pages: Collection<PageBinding> get() = byId.values

    fun ids(): Set<String> = byId.keys

    fun register(binding: PageBinding): PageBinding {
        require(binding.id.isNotBlank()) { "page id is required" }
        PathPattern.parse(binding.path)
        if (byId.containsKey(binding.id)) throw DuplicatePageException(binding.id)
        byId[binding.id] = binding
        byPath[binding.path] = binding
        return binding
    }

    inline fun <reified T : Any> page(
        id: String,
        path: String,
        methods: Set<PageMethod> = setOf(PageMethod.GET),
    ): PageBinding = register(PageBinding(id, path, serializer<T>(), methods))

    fun get(id: String): PageBinding =
        byId[id] ?: throw UnknownPageException(id)

    fun findByPath(path: String): PageBinding? = byPath[path]

    fun match(pathname: String): PageBinding? {
        var best: PageBinding? = null
        for (binding in byId.values) {
            if (!binding.pattern.matches(pathname)) continue
            if (best == null || binding.pattern.specificity > best.pattern.specificity) {
                best = binding
            }
        }
        return best
    }

    companion object {
        fun matches(pattern: String, pathname: String): Boolean =
            PathPattern.parse(pattern).matches(pathname)

        fun params(pattern: String, pathname: String): Map<String, String> =
            PathPattern.parse(pattern).params(pathname) ?: emptyMap()
    }
}

fun pages(block: PageRegistry.() -> Unit): PageRegistry = PageRegistry().apply(block)
