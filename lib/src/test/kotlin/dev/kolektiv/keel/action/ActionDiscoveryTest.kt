package dev.kolektiv.keel.action

import dev.kolektiv.keel.KeelAction
import dev.kolektiv.keel.KeelType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ActionDiscoveryTest {

    @KeelType
    @Serializable
    data class EchoIn(val message: String)

    @KeelType
    @Serializable
    data class EchoOut(val message: String)

    object Host {
        @KeelAction("demo.echo")
        fun echo(input: EchoIn): EchoOut = EchoOut(input.message.uppercase())
    }

    @Test
    fun `discovers a one-in one-out action on an object`() {
        val discovered = ActionDiscovery.discover(Host)
        assertEquals(1, discovered.size)
        assertEquals("demo.echo", discovered.single().id)
        val out = runBlocking { discovered.single().invoke(EchoIn("hi")) as EchoOut }
        assertEquals("HI", out.message)
    }

    @Test
    fun `registry uses the function name when the id is omitted`() {
        val registry = ActionDiscovery.registry(UnnamedHost)
        assertEquals(setOf("shout"), registry.ids())
    }

    object UnnamedHost {
        @KeelAction
        fun shout(input: EchoIn): EchoOut = EchoOut(input.message)
    }

    class EchoCtx(val prefix: String)

    object ExtensionHost {
        @KeelAction("demo.ctx")
        fun EchoCtx.echo(input: EchoIn): EchoOut = EchoOut(prefix + input.message)
    }

    object Boom {
        @KeelAction("demo.boom")
        fun boom(input: EchoIn): EchoOut = throw IllegalStateException("nope")
    }

    @Test
    fun `extension receiver is passed at invoke`() {
        val discovered = ActionDiscovery.discover(ExtensionHost).single()
        val out = runBlocking {
            discovered.invoke(EchoIn("hi"), extension = EchoCtx("X")) as EchoOut
        }
        assertEquals("Xhi", out.message)
    }

    @Test
    fun `invoke rethrows the function exception`() {
        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { ActionDiscovery.discover(Boom).single().invoke(EchoIn("x")) }
        }
        assertEquals("nope", error.message)
    }
}
