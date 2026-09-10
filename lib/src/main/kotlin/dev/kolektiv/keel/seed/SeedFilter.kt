package dev.kolektiv.keel.seed

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Inertia-style partial reload: keep or drop **top-level** keys of a
 * [JsonObject]. Other JSON shapes are returned unchanged.
 */
object SeedFilter {
    fun filterData(data: JsonElement, only: Set<String>, except: Set<String>): JsonElement {
        val obj = data as? JsonObject ?: return data
        if (only.isEmpty() && except.isEmpty()) return obj
        val keep = if (only.isEmpty()) obj.keys else only
        return JsonObject(
            obj.filterKeys { key -> key in keep && key !in except },
        )
    }
}
