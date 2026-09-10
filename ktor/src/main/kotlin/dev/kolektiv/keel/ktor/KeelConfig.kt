package dev.kolektiv.keel.ktor

import dev.kolektiv.keel.Keel
import dev.kolektiv.keel.action.ActionDiscovery
import dev.kolektiv.keel.action.ActionRegistry
import dev.kolektiv.keel.bundle.FrontendBundle
import dev.kolektiv.keel.page.PageRegistry
import dev.kolektiv.keel.theme.ThemeResolver
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import kotlinx.serialization.json.JsonObject

fun interface SharedProvider {
    fun load(call: ApplicationCall, path: String, params: Map<String, String>, query: Parameters): JsonObject?
}

class PagesDsl @PublishedApi internal constructor(
    @PublishedApi internal val registry: PageRegistry,
    @PublishedApi internal val handlers: MutableMap<String, suspend PageRequest.() -> Any>,
) {
    inline fun <reified T : Any> page(
        id: String,
        path: String,
        noinline load: suspend PageRequest.() -> T,
    ) {
        registry.page<T>(id, path)
        handlers[id] = load
    }
}

class ActionsDsl @PublishedApi internal constructor(
    @PublishedApi internal val registry: ActionRegistry,
    @PublishedApi internal val handlers: MutableMap<String, suspend ActionRequest.(Any) -> Any>,
) {
    inline fun <reified I : Any, reified O : Any> action(
        id: String,
        noinline handler: suspend ActionRequest.(I) -> O,
    ) {
        registry.action<I, O>(id)
        @Suppress("UNCHECKED_CAST")
        handlers[id] = handler as suspend ActionRequest.(Any) -> Any
    }
}

class KeelConfig {
    var bundle: FrontendBundle? = null
    var bundles: List<FrontendBundle> = emptyList()
    var packUrlPrefix: String = Keel.PACK_URL_PREFIX
    var defaultThemeId: String? = null
    var themeResolver: ThemeResolver? = null
    var bootstrap: String = "bootstrap.js"
    var title: String = "Keel"
    var shared: SharedProvider? = null
    var notFoundPageId: String? = null

    @PublishedApi
    internal val registry: PageRegistry = PageRegistry()
    @PublishedApi
    internal val handlers: MutableMap<String, suspend PageRequest.() -> Any> = linkedMapOf()
    @PublishedApi
    internal val actionRegistry: ActionRegistry = ActionRegistry()
    @PublishedApi
    internal val actionHandlers: MutableMap<String, suspend ActionRequest.(Any) -> Any> = linkedMapOf()

    fun pages(block: PagesDsl.() -> Unit) {
        PagesDsl(registry, handlers).block()
    }

    fun actions(block: ActionsDsl.() -> Unit) {
        ActionsDsl(actionRegistry, actionHandlers).block()
    }

    /**
     * Register `@KeelAction` functions on [hosts] (objects or classes). Each
     * function is `(In) -> Out`; JSON in/out is the contract. An
     * `ApplicationCall.(In) -> Out` extension gets [ActionRequest.call] as
     * `this`.
     */
    fun actions(vararg hosts: Any) {
        for (host in hosts) {
            for (discovered in ActionDiscovery.discover(host)) {
                actionRegistry.register(discovered.binding)
                actionHandlers[discovered.id] = { input ->
                    ActionRequest.with(this) { discovered.invoke(input, extension = call) }
                }
            }
        }
    }

    internal fun configuredBundles(): List<FrontendBundle> =
        buildList {
            bundle?.let { add(it) }
            addAll(bundles)
        }.distinctBy { it.id }
}
