package dev.kolektiv.keel.samples.harbor

import dev.kolektiv.keel.KeelAction
import dev.kolektiv.keel.ktor.PageValidationException
import io.ktor.server.application.ApplicationCall
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

data class User(
    val id: String,
    val displayName: String,
)

data class Message(
    val id: String,
    val userId: String,
    val displayName: String,
    val body: String,
    val at: String,
)

object Board {
    const val COOKIE: String = "harbor_uid"

    private val users = ConcurrentHashMap<String, User>()
    private val messages = CopyOnWriteArrayList<Message>()

    fun clear() {
        users.clear()
        messages.clear()
    }

    fun viewer(call: ApplicationCall): User? {
        val id = call.request.cookies[COOKIE] ?: return null
        return users[id]
    }

    fun user(id: String): User? = users[id]

    fun feed(): List<FeedItem> = messages.map { it.toFeedItem() }

    fun messagesFor(userId: String): List<FeedItem> =
        messages.filter { it.userId == userId }.map { it.toFeedItem() }

    @KeelAction("harbor.setName")
    fun ApplicationCall.setName(input: SetNameIn): SetNameOut {
        val trimmed = input.displayName.trim()
        if (trimmed.length < 2 || trimmed.length > 40) {
            throw PageValidationException(
                mapOf("displayName" to listOf("Display name must be 2 to 40 characters.")),
                Unit,
            )
        }
        val existing = viewer(this)
        val user = if (existing != null) {
            val updated = existing.copy(displayName = trimmed)
            users[existing.id] = updated
            messages.replaceAll { message ->
                if (message.userId == existing.id) message.copy(displayName = trimmed) else message
            }
            updated
        } else {
            val created = User(id = newId(), displayName = trimmed)
            users[created.id] = created
            response.cookies.append(COOKIE, created.id, path = "/", httpOnly = true)
            created
        }
        return SetNameOut(user.toRef())
    }

    @KeelAction("harbor.postMessage")
    fun ApplicationCall.postMessage(input: PostMessageIn): PostMessageOut {
        val author = viewer(this)
        if (author == null) {
            throw PageValidationException(
                mapOf("body" to listOf("Set a display name first.")),
                Unit,
            )
        }
        val trimmed = input.body.trim()
        if (trimmed.isEmpty() || trimmed.length > 2000) {
            throw PageValidationException(
                mapOf("body" to listOf("Message must be 1 to 2000 characters.")),
                Unit,
            )
        }
        val message = Message(
            id = newId(),
            userId = author.id,
            displayName = author.displayName,
            body = trimmed,
            at = timestamp(),
        )
        messages.add(0, message)
        return PostMessageOut(message.toFeedItem())
    }

    private fun newId(): String = UUID.randomUUID().toString()

    private fun timestamp(): String = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()
}

fun User.toRef(): UserRef = UserRef(id = id, displayName = displayName)

fun Message.toFeedItem(): FeedItem = FeedItem(
    id = id,
    userId = userId,
    displayName = displayName,
    body = body,
    at = at,
)
