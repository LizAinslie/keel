package dev.kolektiv.keel.page

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/**
 * A page the host knows how to load. [id] is the contract key themes implement.
 * [path] is a Ktor-style pattern (`/p/{slug}`), owned by the server.
 */
data class PageBinding(
    val id: String,
    val path: String,
    val serializer: KSerializer<*>,
)

class DuplicatePageException(id: String) : IllegalStateException("page '$id' is already registered")

class UnknownPageException(id: String) : NoSuchElementException("unknown page '$id'")

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
        require(binding.path.startsWith("/")) { "path must be absolute, got '${binding.path}'" }
        if (byId.containsKey(binding.id)) throw DuplicatePageException(binding.id)
        byId[binding.id] = binding
        byPath[binding.path] = binding
        return binding
    }

    inline fun <reified T : Any> page(id: String, path: String): PageBinding =
        register(PageBinding(id, path, serializer<T>()))

    fun get(id: String): PageBinding =
        byId[id] ?: throw UnknownPageException(id)

    fun findByPath(path: String): PageBinding? = byPath[path]

    fun match(pathname: String): PageBinding? {
        byPath[pathname]?.let { return it }
        return byId.values.firstOrNull { matches(it.path, pathname) }
    }

    companion object {
        fun matches(pattern: String, pathname: String): Boolean {
            val patternParts = pattern.trimEnd('/').split('/')
            val pathParts = pathname.trimEnd('/').split('/')
            if (patternParts.size != pathParts.size) return false
            return patternParts.indices.all { i ->
                val expected = patternParts[i]
                expected.startsWith("{") && expected.endsWith("}") || expected == pathParts[i]
            }
        }

        fun params(pattern: String, pathname: String): Map<String, String> {
            val patternParts = pattern.trimEnd('/').split('/')
            val pathParts = pathname.trimEnd('/').split('/')
            if (patternParts.size != pathParts.size) return emptyMap()
            val out = linkedMapOf<String, String>()
            for (i in patternParts.indices) {
                val expected = patternParts[i]
                if (expected.startsWith("{") && expected.endsWith("}")) {
                    out[expected.removePrefix("{").removeSuffix("}")] = pathParts[i]
                } else if (expected != pathParts[i]) {
                    return emptyMap()
                }
            }
            return out
        }
    }
}

fun pages(block: PageRegistry.() -> Unit): PageRegistry = PageRegistry().apply(block)
