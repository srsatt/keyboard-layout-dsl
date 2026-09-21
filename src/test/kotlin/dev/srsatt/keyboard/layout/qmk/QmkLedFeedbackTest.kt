package dev.srsatt.keyboard.layout.qmk

import dev.srsatt.keyboard.layout.model.keyboardProfile
import dev.srsatt.keyboard.layout.model.position
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QmkLedFeedbackTest {
    private val first = position("sample.first")
    private val second = position("sample.second")
    private val profile = keyboardProfile("sample", listOf(first, second))
    private val matrix = deviceAddressMap(profile, "sample.h:layout", listOf(first to MatrixAddress(0, 0), second to MatrixAddress(0, 1)))
    private val physical = deviceAddressMap(profile, "sample.c:g_led_config", listOf(first to LedAddress(3), second to LedAddress(7)))
    private val statuses = DeviceStatusLedMap.Available("sample.h:status", setOf(StatusLedAddress(1)))
    private val layers = listOf(
        QmkLedLayerState("RED", 4, HsvColor(0, 255, 200), setOf(first)),
        QmkLedLayerState("NAV", 5, HsvColor(128, 255, 200), setOf(second)),
    )

    @Test
    fun `unsupported physical and status targets fail unless omission is explicit`() {
        val physicalError = assertFailsWith<IllegalStateException> {
            qmkLedFeedbackPlan(
                matrix, DeviceAddressMap.Unavailable("no per-key LEDs"), statuses, layers, emptyList(), emptyList(),
            )
        }
        assertTrue(physicalError.message.orEmpty().contains("no per-key LEDs"))

        val statusError = assertFailsWith<IllegalArgumentException> {
            qmkLedFeedbackPlan(
                matrix, physical,
                statuses,
                layers,
                emptyList(),
                listOf(QmkStatusLedState("modifier.shift", StatusLedAddress(2))),
            )
        }
        assertTrue(statusError.message.orEmpty().contains("unsupported device LEDs: 2"))

        val unavailableStatus = assertFailsWith<IllegalStateException> {
            qmkLedFeedbackPlan(
                matrix, physical,
                DeviceStatusLedMap.Unavailable("no dedicated status LEDs"),
                layers,
                emptyList(),
                listOf(QmkStatusLedState("modifier.shift", StatusLedAddress(1))),
            )
        }
        assertTrue(unavailableStatus.message.orEmpty().contains("no dedicated status LEDs"))

        assertNull(
            qmkLedFeedbackPlan(
                DeviceAddressMap.Unavailable("no matrix map"),
                DeviceAddressMap.Unavailable("no per-key LEDs"),
                DeviceStatusLedMap.Unavailable("no status LEDs"),
                layers,
                emptyList(),
                emptyList(),
                UnsupportedLedFallback.OMIT_SEMANTIC_FEEDBACK,
            ),
        )
    }

    @Test
    fun `combined states require known constituent layers and mapped positions`() {
        val unknownLayer = assertFailsWith<IllegalArgumentException> {
            qmkLedFeedbackPlan(
                matrix, physical,
                statuses,
                layers,
                listOf(QmkLedCombinedState("RED_NAV", setOf(4, 13), HsvColor(220, 255, 200), setOf(first))),
                emptyList(),
            )
        }
        assertTrue(unknownLayer.message.orEmpty().contains("unknown QMK layers [13]"))

        val missingPosition = assertFailsWith<IllegalArgumentException> {
            qmkLedFeedbackPlan(
                matrix, physical,
                statuses,
                layers + QmkLedLayerState("EXTRA", 6, HsvColor(1, 2, 3), setOf(position("outside"))),
                emptyList(),
                emptyList(),
            )
        }
        assertTrue(missingPosition.message.orEmpty().contains("outside"))
    }
}
