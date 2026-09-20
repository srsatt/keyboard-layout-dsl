package dev.srsatt.keyboard.layout.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class PhysicalTest {
    @Test
    fun `profiles can use non-handed ordered groups and aliases preserve identity`() {
        val main = listOf(position("desk.main.a"), position("desk.main.b"))
        val functionRow = listOf(position("desk.function.f1"), position("desk.function.f2"))
        val numpad = listOf(position("desk.numpad.1"), position("desk.numpad.2"))
        val profile = keyboardProfile("desk", main + functionRow + numpad)
        val enter = numpad.last()

        assertEquals(
            listOf("desk.main.a", "desk.main.b", "desk.function.f1", "desk.function.f2", "desk.numpad.1", "desk.numpad.2"),
            profile.positions.map(KeyPosition::id),
        )
        assertSame(numpad.last(), enter)
    }

    @Test
    fun `a profile rejects duplicate physical IDs`() {
        assertFailsWith<IllegalArgumentException> {
            keyboardProfile("invalid", listOf(position("same"), position("same")))
        }
    }
}
