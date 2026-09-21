package dev.srsatt.keyboard.layout.runtime

import dev.srsatt.keyboard.layout.model.Alt
import dev.srsatt.keyboard.layout.model.InputSource
import dev.srsatt.keyboard.layout.model.Key
import dev.srsatt.keyboard.layout.model.MacOS
import dev.srsatt.keyboard.layout.model.Shift
import dev.srsatt.keyboard.layout.model.Stroke
import dev.srsatt.keyboard.layout.model.language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class RoutingRuntimeTest {
    private val en = language("en")
    private val ru = language("ru")
    private val enSource = InputSource("test.en")
    private val ruSource = InputSource("test.ru")

    @Test
    fun `dot and space arm Shift for exactly one eligible invocation`() {
        val runtime = RoutingRuntime(RuntimeContext(MacOS, en), enSource)

        assertEquals(
            listOf(
                RoutingEvent.Tap(Stroke(Key.PERIOD)),
                RoutingEvent.Tap(Stroke(Key.SPACE)),
                RoutingEvent.OneShotArmed(Shift, 1.seconds),
            ),
            runtime.sentenceEnd(0.seconds, 1.seconds).events,
        )
        assertEquals(
            listOf(
                RoutingEvent.OneShotConsumed(Shift),
                output(OutputTransition.ModifierPressed(Shift)),
                output(OutputTransition.KeyPressed(Key.A)),
            ),
            runtime.press(invocation("a"), Stroke(Key.A), OneShotEligibility.ELIGIBLE, 500.milliseconds).events,
        )
        assertEquals(
            listOf(
                output(OutputTransition.KeyReleased(Key.A)),
                output(OutputTransition.ModifierReleased(Shift)),
            ),
            runtime.release(invocation("a"), 600.milliseconds).events,
        )
        assertEquals(
            listOf(output(OutputTransition.KeyPressed(Key.B))),
            runtime.press(invocation("b"), Stroke(Key.B), OneShotEligibility.ELIGIBLE, 700.milliseconds).events,
        )
    }

    @Test
    fun `one-shot expires without changing the next key`() {
        val runtime = RoutingRuntime(RuntimeContext(MacOS, en), enSource)
        runtime.armOneShot(Shift, 1.seconds, 0.seconds)

        assertEquals(
            listOf(RoutingEvent.OneShotExpired(Shift)),
            runtime.advanceTo(1.seconds).events,
        )
        assertEquals(
            listOf(output(OutputTransition.KeyPressed(Key.A))),
            runtime.press(invocation("a"), Stroke(Key.A), OneShotEligibility.ELIGIBLE, 1.seconds).events,
        )
    }

    @Test
    fun `logical English scope restores after delay without switching host source`() {
        val runtime = RoutingRuntime(RuntimeContext(MacOS, ru), ruSource)
        val scope = routing("english-shortcut")

        val entered = runtime.beginScope(
            scope,
            RoutingScope(
                sourceRoute = SourceRoute.Logical(en),
                restorationDelay = 100.milliseconds,
            ),
            0.seconds,
        )
        assertEquals(listOf(RoutingEvent.LogicalLanguageChanged(ru, en)), entered.events)
        assertEquals(en, runtime.context.language)
        assertEquals(ruSource, runtime.assumedHostSource)
        assertFalse(entered.events.any { it is RoutingEvent.HostSourceSwitchRequested })

        assertEquals(
            listOf(output(OutputTransition.KeyPressed(Key.A))),
            runtime.press(invocation("shortcut"), Stroke(Key.A), OneShotEligibility.INELIGIBLE, 10.milliseconds).events,
        )
        assertEquals(emptyList(), runtime.endScope(scope, 20.milliseconds).events)
        assertEquals(emptyList(), runtime.advanceTo(119.milliseconds).events)
        assertEquals(
            listOf(RoutingEvent.LogicalLanguageChanged(en, ru)),
            runtime.advanceTo(120.milliseconds).events,
        )
        assertEquals(ru, runtime.context.language)
        assertEquals(ruSource, runtime.assumedHostSource)
        assertEquals(
            listOf(output(OutputTransition.KeyReleased(Key.A))),
            runtime.release(invocation("shortcut"), 130.milliseconds).events,
        )
    }

    @Test
    fun `suppressed physical modifier is restored only after the declared delay`() {
        val runtime = RoutingRuntime(RuntimeContext(MacOS, en), enSource)
        val alt = invocation("physical-alt")
        val scope = routing("without-alt")
        runtime.pressModifier(alt, Alt, 0.seconds)

        assertEquals(
            listOf(output(OutputTransition.ModifierReleased(Alt))),
            runtime.beginScope(
                scope,
                RoutingScope(
                    suppressedModifiers = setOf(Alt),
                    restorationDelay = 100.milliseconds,
                ),
                10.milliseconds,
            ).events,
        )
        assertEquals(emptySet(), runtime.activeModifiers)
        assertEquals(emptyList(), runtime.endScope(scope, 20.milliseconds).events)
        assertEquals(emptyList(), runtime.advanceTo(119.milliseconds).events)
        assertEquals(
            listOf(output(OutputTransition.ModifierPressed(Alt))),
            runtime.advanceTo(120.milliseconds).events,
        )
        assertEquals(setOf(Alt), runtime.activeModifiers)
        assertEquals(
            listOf(output(OutputTransition.ModifierReleased(Alt))),
            runtime.release(alt, 130.milliseconds).events,
        )
    }

    private fun invocation(id: String) = InvocationOwner(id)
    private fun routing(id: String) = RoutingOwner(id)
    private fun output(transition: OutputTransition) = RoutingEvent.Output(transition)
}
