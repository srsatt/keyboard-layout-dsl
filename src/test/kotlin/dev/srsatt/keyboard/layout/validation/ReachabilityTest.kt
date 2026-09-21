package dev.srsatt.keyboard.layout.validation

import dev.srsatt.keyboard.layout.dsl.Nothing
import dev.srsatt.keyboard.layout.dsl.layout
import dev.srsatt.keyboard.layout.model.LayerContext
import dev.srsatt.keyboard.layout.model.command
import dev.srsatt.keyboard.layout.model.keyboardProfile
import dev.srsatt.keyboard.layout.model.layer
import dev.srsatt.keyboard.layout.model.position
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReachabilityTest {
    private val a = position("test.a")
    private val b = position("test.b")
    private val profile = keyboardProfile("test", listOf(a, b))
    private val action = command("test.action")

    @Test
    fun `disabled workflow positions are intentional rather than unused`() {
        val workflow = layer("workflow")
        val declared = layout("workflow") {
            base { a performs action }
            on(workflow) {
                default does Nothing
                a performs action
            }
        }

        val report = diagnoseReachability(declared, profile, emptyList())
        val workflowEmpty = report.diagnostics.filterIsInstance<PositionReachabilityDiagnostic>().single {
            it.position == b && it.outcome == ReachabilityOutcome.INTENTIONAL_EMPTY
        }

        assertTrue(LayerContext.Overlay(workflow) in workflowEmpty.contexts)
        assertTrue(report.modeledReachabilityComplete)
        assertTrue(report.wholeFirmwareReachabilityProven)
    }

    @Test
    fun `unused positions and opaque C prevent stronger proof`() {
        val incomplete = diagnoseReachability(
            layout("unused") { base { a performs action } },
            profile,
            emptyList(),
        )
        assertTrue(incomplete.diagnostics.any {
            it is PositionReachabilityDiagnostic && it.position == b && it.outcome == ReachabilityOutcome.UNUSED
        })
        assertFalse(incomplete.modeledReachabilityComplete)
        assertFalse(incomplete.wholeFirmwareReachabilityProven)

        val opaque = diagnoseReachability(
            layout("opaque") { base { a performs action; b does Nothing } },
            profile,
            listOf("legacy.process-record-user"),
        )
        assertTrue(opaque.modeledReachabilityComplete)
        assertTrue(opaque.diagnostics.any { it is OpaqueLegacyReachabilityDiagnostic })
        assertFalse(opaque.wholeFirmwareReachabilityProven)
    }
}
