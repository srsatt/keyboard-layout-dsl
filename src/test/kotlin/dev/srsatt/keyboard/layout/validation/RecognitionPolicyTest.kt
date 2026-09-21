package dev.srsatt.keyboard.layout.validation

import dev.srsatt.keyboard.layout.dsl.layout
import dev.srsatt.keyboard.layout.model.TriggerBehavior
import dev.srsatt.keyboard.layout.model.command
import dev.srsatt.keyboard.layout.model.keystroke
import dev.srsatt.keyboard.layout.model.modifier
import dev.srsatt.keyboard.layout.model.position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RecognitionPolicyTest {
    private val backspace = position("thumb.backspace")
    private val enter = position("thumb.enter")
    private val cmd = position("thumb.cmd")
    private val a = position("test.a")
    private val b = position("test.b")
    private val c = position("test.c")
    private val key = keystroke("key.backspace")
    private val action = command("test.action")

    @Test
    fun `deferred nested thumb family passes`() {
        val declared = layout("thumb-family") {
            base {
                backspace sends key
                deferred {
                    backspace and enter performs action
                    backspace and enter and cmd performs action
                }
            }
        }

        validateLayout(declared)
        assertTrue(declared.layers.single().chords.all { it.triggerBehavior == TriggerBehavior.DEFERRED })
    }

    @Test
    fun `partial overlap without a unique winner fails`() {
        val declared = layout("partial-overlap") {
            base {
                a and b performs action
                b and c performs action
            }
        }

        val error = assertFailsWith<IllegalArgumentException> { validateLayout(declared) }

        assertTrue(error.message.orEmpty().contains("ambiguous partial chord overlap"))
        assertTrue(error.message.orEmpty().contains("[test.a, test.b]"))
        assertTrue(error.message.orEmpty().contains("[test.b, test.c]"))
    }

    @Test
    fun `immediate opaque or destructive prefix fails`() {
        val declared = layout("unsafe-prefix") {
            base {
                immediateOpaqueOrDestructive { a performs action }
                a and b performs action
            }
        }

        val error = assertFailsWith<IllegalArgumentException> { validateLayout(declared) }

        assertTrue(error.message.orEmpty().contains("unsafe immediate prefix"))
        assertTrue(error.message.orEmpty().contains("[test.a]"))
        assertTrue(error.message.orEmpty().contains("[test.a, test.b]"))
    }

    @Test
    fun `immediate reversible modifier prefix passes`() {
        val declared = layout("reversible-prefix") {
            base {
                immediateReversible { a holds modifier("shift") }
                a and b performs action
            }
        }

        validateLayout(declared)
        assertEquals(TriggerBehavior.IMMEDIATE_REVERSIBLE, declared.layers.single().bindings.first().triggerBehavior)
    }
}
