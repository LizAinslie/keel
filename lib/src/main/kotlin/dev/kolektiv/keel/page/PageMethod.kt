package dev.kolektiv.keel.page

enum class PageMethod {
    GET,
    POST,
    PUT,
    PATCH,
    DELETE,
    ;

    companion object {
        fun from(method: String): PageMethod? =
            entries.find { it.name.equals(method, ignoreCase = true) }
    }
}
