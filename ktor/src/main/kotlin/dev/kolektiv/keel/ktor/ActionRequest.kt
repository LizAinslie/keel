package dev.kolektiv.keel.ktor

import dev.kolektiv.keel.KeelJson
import io.ktor.server.application.ApplicationCall
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Context handed to an action handler. Cookie/session state is on [call].
 * Typed input is decoded by the engine and passed as the handler argument;
 * [receiveJson] is the same body as an object, for handlers that want it raw.
 */
class ActionRequest(
    val call: ApplicationCall,
    private val bodyText: String = "",
) {
    fun receiveJson(): JsonObject {
        if (bodyText.isBlank()) return JsonObject(emptyMap())
        return KeelJson.codec.parseToJsonElement(bodyText).jsonObject
    }

    companion object {
        private val current = ThreadLocal<ActionRequest>()

        /**
         * The action currently running on this thread. Annotated `@KeelAction`
         * functions use this for cookies/session without taking [call] as an
         * input field.
         */
        fun current(): ActionRequest =
            current.get() ?: error("not inside a Keel action")

        internal inline fun <T> with(request: ActionRequest, block: () -> T): T {
            val previous = current.get()
            current.set(request)
            return try {
                block()
            } finally {
                if (previous == null) current.remove() else current.set(previous)
            }
        }
    }
}
