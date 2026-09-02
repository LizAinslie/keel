package dev.kolektiv.keel

import kotlinx.serialization.json.Json

object KeelJson {
    val codec: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = false
        explicitNulls = false
    }
}
