package dev.srsatt.keyboard.layout.runtime

import dev.srsatt.keyboard.layout.model.Alt
import dev.srsatt.keyboard.layout.model.Backspace
import dev.srsatt.keyboard.layout.model.Cmd
import dev.srsatt.keyboard.layout.model.Ctrl
import dev.srsatt.keyboard.layout.model.DeleteWord
import dev.srsatt.keyboard.layout.model.Key
import dev.srsatt.keyboard.layout.model.Linux
import dev.srsatt.keyboard.layout.model.MacOS
import dev.srsatt.keyboard.layout.model.Stroke
import dev.srsatt.keyboard.layout.model.keystrokeAction
import dev.srsatt.keyboard.layout.model.language
import dev.srsatt.keyboard.layout.model.layer
import dev.srsatt.keyboard.layout.model.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InvocationRuntimeTest {
    @Test
    fun `release uses the implementation and context captured at press`() {
        val shortcut = keystrokeAction("host.shortcut") {
            implementationIn(MacOS) { Cmd + Key.E }
            implementationIn(Linux) { Ctrl + Key.E }
        }
        val en = language("en")
        val ru = language("ru")
        val red = layer("red")
        val runtime = InvocationRuntime(RuntimeContext(MacOS, en))

        val pressed = runtime.press(owner("held"), shortcut)
        runtime.selectContext(RuntimeContext(Linux, ru, setOf(red)))
        val released = runtime.release(owner("held"))
        val next = runtime.press(owner("next"), shortcut)

        assertEquals(RuntimeContext(MacOS, en), pressed.invocation.context)
        assertEquals(
            listOf(OutputTransition.ModifierPressed(Cmd), OutputTransition.KeyPressed(Key.E)),
            pressed.outputs,
        )
        assertEquals(pressed.invocation, released.invocation)
        assertEquals(
            listOf(OutputTransition.KeyReleased(Key.E), OutputTransition.ModifierReleased(Cmd)),
            released.outputs,
        )
        assertEquals(RuntimeContext(Linux, ru, setOf(red)), next.invocation.context)
        assertEquals(
            listOf(OutputTransition.ModifierPressed(Ctrl), OutputTransition.KeyPressed(Key.E)),
            next.outputs,
        )
    }

    @Test
    fun `DeleteWord preserves Alt owned by an interleaved physical modifier`() {
        val runtime = InvocationRuntime(RuntimeContext(MacOS))

        assertEquals(
            listOf(OutputTransition.ModifierPressed(Alt)),
            runtime.pressModifier(owner("physical-alt"), Alt).outputs,
        )
        assertEquals(
            listOf(OutputTransition.KeyPressed(Backspace)),
            runtime.press(owner("delete-word"), DeleteWord).outputs,
        )
        assertEquals(
            listOf(OutputTransition.KeyReleased(Backspace)),
            runtime.release(owner("delete-word")).outputs,
        )
        assertEquals(setOf(Alt), runtime.activeModifiers)
        assertEquals(
            listOf(OutputTransition.ModifierReleased(Alt)),
            runtime.release(owner("physical-alt")).outputs,
        )
    }

    @Test
    fun `DeleteWord releases its Alt when no other invocation owns it`() {
        val runtime = InvocationRuntime(RuntimeContext(MacOS))

        assertEquals(
            listOf(OutputTransition.ModifierPressed(Alt), OutputTransition.KeyPressed(Backspace)),
            runtime.press(owner("delete-word"), DeleteWord).outputs,
        )
        assertEquals(
            listOf(OutputTransition.KeyReleased(Backspace), OutputTransition.ModifierReleased(Alt)),
            runtime.release(owner("delete-word")).outputs,
        )
        assertEquals(emptySet(), runtime.activeModifiers)
        assertEquals(
            listOf(OutputTransition.KeyPressed(Key.A)),
            runtime.press(owner("ordinary-a"), Stroke(Key.A)).outputs,
        )
    }

    @Test
    fun `later physical Alt survives an earlier DeleteWord release`() {
        val runtime = InvocationRuntime(RuntimeContext(MacOS))

        runtime.press(owner("delete-word"), DeleteWord)
        assertEquals(emptyList(), runtime.pressModifier(owner("physical-alt"), Alt).outputs)
        assertEquals(
            listOf(OutputTransition.KeyReleased(Backspace)),
            runtime.release(owner("delete-word")).outputs,
        )
        assertEquals(setOf(Alt), runtime.activeModifiers)
        assertEquals(
            listOf(OutputTransition.ModifierReleased(Alt)),
            runtime.release(owner("physical-alt")).outputs,
        )
    }

    @Test
    fun `an invocation owner cannot be reused until release`() {
        val runtime = InvocationRuntime(RuntimeContext(MacOS))
        val owner = owner("same")

        runtime.press(owner, Stroke(Key.A))

        assertFailsWith<IllegalArgumentException> { runtime.press(owner, Stroke(Key.B)) }
        runtime.release(owner)
        runtime.press(owner, Stroke(Key.B))
    }

    private fun owner(id: String) = InvocationOwner(id)
}
