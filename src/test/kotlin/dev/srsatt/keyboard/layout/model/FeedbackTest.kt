package dev.srsatt.keyboard.layout.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class FeedbackTest {
    @Test
    fun `typing and action pulses retain semantic targets colors and bounded durations`() {
        val key = position("sample.key")
        val action = hostAction("sample.action")
        val configured = feedback {
            onTyping { pulseLED(TriggerKeys, White, 80.milliseconds) }
            onAction(action) { pulseLED(key, CurrentLanguageColor, 120.milliseconds) }
        }

        assertEquals(FeedbackTrigger.Typing, configured.pulses[0].trigger)
        assertEquals(TriggerKeys, configured.pulses[0].target)
        assertEquals(FixedLedPulseTarget(setOf(key)), configured.pulses[1].target)
        assertEquals(CurrentLanguageColor, configured.pulses[1].color)
        assertEquals(120, configured.pulses[1].durationMilliseconds)

        assertFailsWith<IllegalArgumentException> {
            feedback { onTyping { pulseLED(TriggerKeys, White, Duration.ZERO) } }
        }
        assertFailsWith<IllegalArgumentException> {
            feedback {
                onTyping { pulseLED(TriggerKeys, White, 1.milliseconds) }
                onTyping { pulseLED(TriggerKeys, Green, 1.milliseconds) }
            }
        }
    }
}
