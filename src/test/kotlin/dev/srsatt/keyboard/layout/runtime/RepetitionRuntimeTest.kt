package dev.srsatt.keyboard.layout.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RepetitionRuntimeTest {
    private val red = LegacyRepeatedReleaseTrigger("red", "red.momentary", "red.toggle")

    @Test
    fun `third qualifying release follows its normal release with the alternate action`() {
        val runtime = LegacyRepeatedReleaseRuntime()

        repeat(2) {
            assertEquals(listOf(normal(pressed = true)), runtime.onTrigger(red, pressed = true))
            assertEquals(listOf(normal(pressed = false)), runtime.onTrigger(red, pressed = false))
        }

        // The policy accepts no clock, so elapsed time cannot expire the two completed releases.
        assertEquals(listOf(normal(pressed = true)), runtime.onTrigger(red, pressed = true))
        assertEquals(
            listOf(normal(pressed = false), LegacyRepeatedReleaseDelivery.Alternate("red.toggle")),
            runtime.onTrigger(red, pressed = false),
        )
    }

    @Test
    fun `intervening cancelling event resets the release count`() {
        val runtime = LegacyRepeatedReleaseRuntime()

        repeat(2) {
            runtime.onTrigger(red, pressed = true)
            runtime.onTrigger(red, pressed = false)
        }
        runtime.cancel()

        repeat(2) {
            runtime.onTrigger(red, pressed = true)
            assertEquals(listOf(normal(pressed = false)), runtime.onTrigger(red, pressed = false))
        }
        runtime.onTrigger(red, pressed = true)
        assertEquals(
            listOf(normal(pressed = false), LegacyRepeatedReleaseDelivery.Alternate("red.toggle")),
            runtime.onTrigger(red, pressed = false),
        )
    }

    @Test
    fun `firmware repeat is immediate then follows delay and interval until release`() {
        val runtime = FirmwareTimedRepeatRuntime()

        assertEquals(FirmwareRepeatDelivery("held", "cursor.left", 0), runtime.press("held", "cursor.left", 0))
        assertEquals(emptyList(), runtime.onTimer(299))
        assertEquals(listOf(FirmwareRepeatDelivery("held", "cursor.left", 300)), runtime.onTimer(300))
        assertEquals(emptyList(), runtime.onTimer(304))
        assertEquals(listOf(FirmwareRepeatDelivery("held", "cursor.left", 305)), runtime.onTimer(305))

        runtime.release("held")
        assertEquals(emptyList(), runtime.onTimer(1_000))
    }

    @Test
    fun `firmware repeat reports capacity errors`() {
        val runtime = FirmwareTimedRepeatRuntime(capacity = 2)
        runtime.press("first", "one", 0)
        runtime.press("second", "two", 0)

        val error = assertFailsWith<FirmwareRepeatCapacityException> {
            runtime.press("third", "three", 0)
        }

        assertTrue(error.message.orEmpty().contains("capacity 2"))
        assertEquals(emptyList(), runtime.onTimer(299))
        assertEquals(
            listOf(
                FirmwareRepeatDelivery("first", "one", 300),
                FirmwareRepeatDelivery("second", "two", 300),
            ),
            runtime.onTimer(300),
        )
    }

    private fun normal(pressed: Boolean) =
        LegacyRepeatedReleaseDelivery.Normal("red.momentary", pressed)
}
