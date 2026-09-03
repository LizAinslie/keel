package dev.kolektiv.keel.samples.harbor

import kotlinx.serialization.Serializable

@Serializable
data class PostSummary(
    val slug: String,
    val title: String,
    val excerpt: String,
    val published: String,
)

@Serializable
data class HomePage(
    val kicker: String,
    val title: String,
    val lede: String,
    val posts: List<PostSummary>,
)

@Serializable
data class ListPage(
    val title: String,
    val query: String,
    val posts: List<PostSummary>,
)

@Serializable
data class PostPage(
    val slug: String,
    val title: String,
    val body: String,
    val published: String,
)

@Serializable
data class AboutPage(
    val title: String,
    val copy: String,
    val pack: String,
)

@Serializable
data class NotFoundPage(
    val path: String,
    val title: String = "Not on this chart",
)

fun Entry.summary(): PostSummary = PostSummary(slug, title, excerpt, published)
