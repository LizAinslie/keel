package dev.kolektiv.keel.typegen

import dev.kolektiv.keel.KeelAction
import dev.kolektiv.keel.KeelType
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@KeelType("demo.home")
@Serializable
data class DemoHome(val title: String)

@KeelType
@Serializable
data class DemoRenameIn(val name: String)

@KeelType
@Serializable
data class DemoRenameOut(val ok: Boolean)

object DemoActions {
    @KeelAction("demo.rename")
    fun rename(input: DemoRenameIn): DemoRenameOut = DemoRenameOut(true)
}

class TypegenAnnotationTest {

    @Test
    fun `emits pages and actions from annotations`() {
        val contract = ClasspathContract.scan(listOf("dev.kolektiv.keel.typegen"))
        val ts = Typegen.emit(contract, pagesName = "DemoPages")
        assertTrue(ts.contains("export interface DemoHome {"), ts)
        assertTrue(ts.contains("\"demo.home\": DemoHome"), ts)
        assertTrue(ts.contains("\"demo.rename\": { in: DemoRenameIn; out: DemoRenameOut }"), ts)
        assertTrue(ts.contains("export type DemoActionId = keyof DemoActions"), ts)
        assertTrue(ts.contains("export interface DemoPages"), ts)
    }
}
