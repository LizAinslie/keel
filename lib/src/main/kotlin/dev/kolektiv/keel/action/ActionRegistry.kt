package dev.kolektiv.keel.action

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/**
 * A server action the host knows how to run. [id] is the contract key packs
 * call; it is not a URL. [input] / [output] are the JSON payload serializers.
 */
data class ActionBinding(
    val id: String,
    val input: KSerializer<*>,
    val output: KSerializer<*>,
)

class DuplicateActionException(id: String) : IllegalStateException("action '$id' is already registered")

class UnknownActionException(
    id: String,
    message: String = "unknown action '$id'",
) : NoSuchElementException(message) {
    val actionId: String = id
}

/**
 * Source of truth for action ids and in/out serializers.
 *
 * Ktor walks this registry to bind `POST /__keel/action/{id}`. Typegen walks
 * it to emit `.d.ts`. This module does not depend on Ktor.
 */
class ActionRegistry {
    private val byId = linkedMapOf<String, ActionBinding>()

    val actions: Collection<ActionBinding> get() = byId.values

    fun ids(): Set<String> = byId.keys

    fun register(binding: ActionBinding): ActionBinding {
        require(binding.id.isNotBlank()) { "action id is required" }
        if (byId.containsKey(binding.id)) throw DuplicateActionException(binding.id)
        byId[binding.id] = binding
        return binding
    }

    inline fun <reified I : Any, reified O : Any> action(id: String): ActionBinding =
        register(ActionBinding(id, serializer<I>(), serializer<O>()))

    fun get(id: String): ActionBinding =
        byId[id] ?: throw UnknownActionException(id)
}

fun actions(block: ActionRegistry.() -> Unit): ActionRegistry = ActionRegistry().apply(block)
