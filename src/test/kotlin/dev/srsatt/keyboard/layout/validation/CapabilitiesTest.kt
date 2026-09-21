package dev.srsatt.keyboard.layout.validation

import dev.srsatt.keyboard.layout.dsl.layout
import dev.srsatt.keyboard.layout.model.HostPlatform
import dev.srsatt.keyboard.layout.model.InputSource
import dev.srsatt.keyboard.layout.model.MacOS
import dev.srsatt.keyboard.layout.model.command
import dev.srsatt.keyboard.layout.model.hostAction
import dev.srsatt.keyboard.layout.model.position
import dev.srsatt.keyboard.layout.model.symbol
import dev.srsatt.keyboard.layout.model.symbols
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CapabilitiesTest {
    private val a = position("test.a")
    private val b = position("test.b")
    private val c = position("test.c")
    private val source = InputSource("test.source")
    private val empty = layout("empty") { base { a does dev.srsatt.keyboard.layout.dsl.Nothing } }
    private fun requirements(block: ValidationRequirements.() -> ValidationRequirements = { this }) =
        ValidationRequirements(empty, host = MacOS, inputSource = source, capacity = TargetCapacity(2, 1)).block()

    @Test fun `missing action reference identifies its ID`() {
        val missing = hostAction("missing.action")
        val error = assertFailsWith<IllegalArgumentException> { validateRequirements(requirements { copy(reachableActions = listOf(missing)) }) }
        assertTrue(error.message.orEmpty().contains("missing.action"))
    }

    @Test fun `symbol coverage and reserved signals identify missing value`() {
        val voice = hostAction("voice.test")
        val missingSymbol = assertFailsWith<IllegalArgumentException> {
            validateRequirements(requirements { copy(reachableSymbols = setOf(symbol('!')), symbolCatalog = symbols { (symbol('.')) { encodingIn(MacOS, source) { tap(dev.srsatt.keyboard.layout.model.Key.PERIOD) } } }) })
        }
        val reservedSignal = assertFailsWith<IllegalArgumentException> {
            validateRequirements(requirements { copy(declaredActions = listOf(voice), reachableActions = listOf(voice), hostSignals = mapOf(voice.id to "F13"), reservedSignals = setOf("F13")) })
        }
        assertTrue(missingSymbol.message.orEmpty().contains("'!'"))
        assertTrue(reservedSignal.message.orEmpty().contains("F13"))
    }

    @Test fun `backend and capacity limits identify exact overflow`() {
        val mapping = assertFailsWith<IllegalArgumentException> {
            validateRequirements(requirements { copy(backendMapping = BackendMapping(listOf(a, b), listOf(a, a))) })
        }
        val chords = layout("capacity") { base { a and b and c performs command("x") } }
        val capacity = assertFailsWith<IllegalArgumentException> {
            validateRequirements(requirements { copy(layout = chords, capacity = TargetCapacity(2, 0)) })
        }
        assertTrue(mapping.message.orEmpty().contains("duplicates positions"))
        assertTrue(capacity.message.orEmpty().contains("member limit 2"))
    }

    @Test fun `action implementations and cycles use declared validation`() {
        val missing = dev.srsatt.keyboard.layout.model.action("missing.impl") { }
        val error = assertFailsWith<IllegalArgumentException> {
            validateRequirements(requirements { copy(declaredActions = listOf(missing), reachableActions = listOf(missing)) })
        }
        assertTrue(error.message.orEmpty().contains("no implementation"))
    }
}
