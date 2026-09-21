package dev.srsatt.keyboard.layout.model

import kotlin.time.Duration

data class LedColor(val hue: Int, val saturation: Int, val value: Int) {
    init {
        require(hue in 0..255 && saturation in 0..255 && value in 0..255) {
            "LED color components must be in 0..255"
        }
    }
}

sealed interface LedPulseColor

data class FixedLedPulseColor(val color: LedColor) : LedPulseColor

data object CurrentLanguageColor : LedPulseColor

val White = FixedLedPulseColor(LedColor(0, 0, 255))
val Green = FixedLedPulseColor(LedColor(85, 255, 255))

sealed interface LedPulseTarget

data object TriggerKeys : LedPulseTarget

data class FixedLedPulseTarget(val positions: Set<KeyPosition>) : LedPulseTarget {
    init {
        require(positions.isNotEmpty()) { "A fixed LED pulse target must contain at least one position" }
    }
}

sealed interface FeedbackTrigger {
    data object Typing : FeedbackTrigger
    data class Action(val actionId: String) : FeedbackTrigger
}

data class LedPulseRule(
    val trigger: FeedbackTrigger,
    val target: LedPulseTarget,
    val color: LedPulseColor,
    val durationMilliseconds: Int,
)

data class FeedbackConfiguration(val pulses: List<LedPulseRule>)

class FeedbackScope internal constructor() {
    private val pulses = mutableListOf<LedPulseRule>()

    fun onTyping(block: LedPulseScope.() -> Unit) {
        add(FeedbackTrigger.Typing, block)
    }

    fun onAction(action: ActionReference, block: LedPulseScope.() -> Unit) {
        add(FeedbackTrigger.Action(action.id), block)
    }

    private fun add(trigger: FeedbackTrigger, block: LedPulseScope.() -> Unit) {
        require(pulses.none { it.trigger == trigger }) { "Feedback trigger '$trigger' already has an LED pulse" }
        pulses += LedPulseScope(trigger).apply(block).build()
    }

    internal fun build() = FeedbackConfiguration(pulses.toList())
}

class LedPulseScope internal constructor(private val trigger: FeedbackTrigger) {
    private var pulse: LedPulseRule? = null

    fun pulseLED(target: LedPulseTarget, color: LedPulseColor, duration: Duration) {
        require(pulse == null) { "Feedback trigger '$trigger' may declare only one LED pulse" }
        val milliseconds = duration.inWholeMilliseconds
        require(milliseconds in 1..Int.MAX_VALUE.toLong()) {
            "LED pulse duration must be between 1 ms and ${Int.MAX_VALUE} ms"
        }
        pulse = LedPulseRule(trigger, target, color, milliseconds.toInt())
    }

    fun pulseLED(target: PhysicalGroup, color: LedPulseColor, duration: Duration) =
        pulseLED(FixedLedPulseTarget(target.positions.toSet()), color, duration)

    internal fun build() = requireNotNull(pulse) { "Feedback trigger '$trigger' must declare an LED pulse" }
}

fun feedback(block: FeedbackScope.() -> Unit): FeedbackConfiguration = FeedbackScope().apply(block).build()
