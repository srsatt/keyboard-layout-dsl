package dev.srsatt.keyboard.layout.dsl

import dev.srsatt.keyboard.layout.model.LayerContext
import dev.srsatt.keyboard.layout.model.PhysicalGroup
import dev.srsatt.keyboard.layout.model.command
import dev.srsatt.keyboard.layout.model.language
import dev.srsatt.keyboard.layout.model.position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ChordDslTest {
    private val a = position("test.a")
    private val b = position("test.b")
    private val c = position("test.c")
    private val d = position("test.d")
    private val aliasA = a
    private val pair = group(b, c)
    private val action = command("test.action")

    @Test
    fun `inline chords are order independent and retain declaration source`() {
        val first = declaredChord { a and b performs action }
        val reversed = declaredChord { b and a performs action }

        assertEquals(first.keys, reversed.keys)
        assertEquals(listOf("test.a", "test.b"), first.keys.positions.map { it.id })
        assertTrue(first.source.file.endsWith("ChordDslTest.kt"))
        assertTrue(first.source.line > 0)
    }

    @Test
    fun `nested shared groups expand as one union`() {
        val chord = declaredChord {
            chordsWith(a) {
                chordsWith(pair) {
                    d performs action
                }
            }
        }

        assertEquals(listOf("test.a", "test.b", "test.c", "test.d"), chord.keys.positions.map { it.id })
    }

    @Test
    fun `shared and inline syntax produce identical chord sets`() {
        val shared = declaredChord { chordsWith(a) { b performs action } }
        val inline = declaredChord { a and b performs action }

        assertEquals(inline.keys, shared.keys)
    }

    @Test
    fun `aliases may not repeat a physical position`() {
        val inlineError = assertFailsWith<IllegalArgumentException> {
            declaredChord { a and aliasA performs action }
        }
        val sharedError = assertFailsWith<IllegalArgumentException> {
            declaredChord { chordsWith(a) { aliasA performs action } }
        }

        assertTrue(inlineError.message.orEmpty().contains("test.a"))
        assertTrue(sharedError.message.orEmpty().contains("test.a"))
    }

    @Test
    fun `chords inherit enclosing language condition`() {
        val english = language("en")
        val declared = layout("conditions") {
            language(english) {
                chordsWith(a) { b performs action }
            }
        }

        assertEquals(LayerContext.Language(english), declared.layers.single().context)
        assertEquals(1, declared.layers.single().chords.size)
    }

    private fun declaredChord(block: LayerScope.() -> Unit) =
        layout("chord") { base(block) }.layers.single().chords.single()
}

private fun group(vararg positions: dev.srsatt.keyboard.layout.model.KeyPosition): PhysicalGroup =
    object : PhysicalGroup {
        override val positions = positions.toList()
    }
