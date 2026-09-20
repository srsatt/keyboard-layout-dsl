package dev.srsatt.keyboard.layout.model

import dev.srsatt.keyboard.layout.dsl.LayerScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class ActionEffectsTest {
    @Test
    fun `effects retain exact order and distinguish raw taps from semantic calls`() {
        val voice = hostAction("voice.openwhispr")
        val sequence = action("workflow.sequence") {
            implementation {
                tap(Cmd + Key.E)
                typeText("ready")
                perform(voice)
                withModifier(Shift) {
                    tap(Stroke(Key.A))
                    delay(25.milliseconds)
                }
                delay(100.milliseconds)
            }
        }

        val effects = sequence.resolveImplementation(Linux).effects

        assertEquals(
            listOf(
                RawTapEffect(Cmd + Key.E),
                TypeTextEffect("ready"),
                InvokeActionEffect(voice),
                ScopedModifierEffect(
                    Shift,
                    listOf(RawTapEffect(Stroke(Key.A)), DelayEffect(25.milliseconds)),
                ),
                DelayEffect(100.milliseconds),
            ),
            effects,
        )
        assertIs<RawTapEffect>(effects[0])
        assertIs<InvokeActionEffect>(effects[2])
    }

    @Test
    fun `bounded Kotlin loops record effects and reject overflow`() {
        val repeated = action("typing.repeat") {
            implementation {
                repeat(3) { tap(Stroke(Key.A)) }
            }
        }

        assertEquals(List(3) { RawTapEffect(Stroke(Key.A)) }, repeated.resolveImplementation(MacOS).effects)

        val error = assertFailsWith<IllegalArgumentException> {
            action("typing.too-many") {
                implementation {
                    repeat(MAX_ACTION_EFFECTS_PER_IMPLEMENTATION + 1) { tap(Stroke(Key.A)) }
                }
            }
        }
        assertTrue(error.message.orEmpty().contains("at most $MAX_ACTION_EFFECTS_PER_IMPLEMENTATION"))
    }

    @Test
    fun `action reference cycles fail validation`() {
        val firstReference = action("cycle.first") { }
        val secondReference = action("cycle.second") { }
        val first = action("cycle.first") {
            implementation { perform(secondReference) }
        }
        val second = action("cycle.second") {
            implementation { perform(firstReference) }
        }

        val error = assertFailsWith<IllegalArgumentException> {
            validateActionCycles(listOf(first, second), MacOS)
        }

        assertTrue(error.message.orEmpty().contains("cycle.first -> cycle.second -> cycle.first"))
    }

    @Test
    fun `general sequences cannot cross the hold lifecycle boundary`() {
        assertFalse(Holdable::class.java.isAssignableFrom(ActionRef::class.java))
        assertFalse(Sendable::class.java.isAssignableFrom(ActionRef::class.java))
        assertTrue(
            LayerScope::class.java.declaredMethods
                .filter { it.name == "holds" }
                .all { it.parameterTypes.last() == Holdable::class.java },
        )
    }
}
