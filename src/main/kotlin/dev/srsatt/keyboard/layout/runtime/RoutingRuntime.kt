package dev.srsatt.keyboard.layout.runtime

import dev.srsatt.keyboard.layout.model.InputSource
import dev.srsatt.keyboard.layout.model.Key
import dev.srsatt.keyboard.layout.model.LanguageRef
import dev.srsatt.keyboard.layout.model.Modifier
import dev.srsatt.keyboard.layout.model.Shift
import dev.srsatt.keyboard.layout.model.Stroke
import kotlin.time.Duration

data class RoutingOwner(val id: String) {
    init {
        require(id.isNotBlank()) { "Routing owner ID must not be blank" }
    }
}

enum class OneShotEligibility {
    ELIGIBLE,
    INELIGIBLE,
}

sealed interface SourceRoute {
    val language: LanguageRef

    data class Logical(override val language: LanguageRef) : SourceRoute

    data class Host(
        override val language: LanguageRef,
        val inputSource: InputSource,
    ) : SourceRoute
}

data class RoutingScope(
    val sourceRoute: SourceRoute? = null,
    val contributedModifiers: Set<Modifier> = emptySet(),
    val suppressedModifiers: Set<Modifier> = emptySet(),
    val restorationDelay: Duration = Duration.ZERO,
) {
    init {
        require(restorationDelay >= Duration.ZERO) { "Restoration delay must not be negative" }
        require((contributedModifiers intersect suppressedModifiers).isEmpty()) {
            "A routing scope cannot contribute and suppress the same modifier"
        }
    }
}

sealed interface RoutingEvent {
    data class Tap(val stroke: Stroke) : RoutingEvent
    data class Output(val transition: OutputTransition) : RoutingEvent
    data class OneShotArmed(val modifier: Modifier, val expiresAt: Duration) : RoutingEvent
    data class OneShotConsumed(val modifier: Modifier) : RoutingEvent
    data class OneShotExpired(val modifier: Modifier) : RoutingEvent
    data class LogicalLanguageChanged(val from: LanguageRef?, val to: LanguageRef?) : RoutingEvent
    data class HostSourceSwitchRequested(val from: InputSource, val to: InputSource) : RoutingEvent
}

data class RoutingStep(val events: List<RoutingEvent>)

/**
 * Runtime routing state above [InvocationRuntime]. Callers supply a monotonic timestamp so firmware
 * backends can use their native clock without making this model platform-specific.
 */
class RoutingRuntime(
    initialContext: RuntimeContext,
    initialHostSource: InputSource,
) {
    private data class OneShotState(val modifier: Modifier, val expiresAt: Duration)

    private data class ActiveScope(
        val owner: RoutingOwner,
        val declaration: RoutingScope,
        val previousContext: RuntimeContext,
        val previousHostSource: InputSource,
        val modifierOwners: Map<Modifier, InvocationOwner>,
        var restoreAt: Duration? = null,
    )

    private val invocations = InvocationRuntime(initialContext)
    private var oneShot: OneShotState? = null
    private var scope: ActiveScope? = null
    private var lastTimestamp = Duration.ZERO
    private var internalOwnerSequence = 0

    var assumedHostSource: InputSource = initialHostSource
        private set

    val context: RuntimeContext
        get() = invocations.context

    val activeKeys: Set<Key>
        get() = invocations.activeKeys

    val activeModifiers: Set<Modifier>
        get() = invocations.activeModifiers - suppressedModifiers()

    fun sentenceEnd(now: Duration, oneShotTimeout: Duration): RoutingStep {
        val events = advance(now).toMutableList()
        events += RoutingEvent.Tap(Stroke(Key.PERIOD))
        events += RoutingEvent.Tap(Stroke(Key.SPACE))
        events += armOneShot(Shift, oneShotTimeout, now)
        return RoutingStep(events)
    }

    fun armOneShot(modifier: Modifier, timeout: Duration, now: Duration): RoutingEvent.OneShotArmed {
        checkTimestamp(now)
        require(timeout > Duration.ZERO) { "One-shot timeout must be positive" }
        val state = OneShotState(modifier, now + timeout)
        oneShot = state
        return RoutingEvent.OneShotArmed(state.modifier, state.expiresAt)
    }

    fun press(
        owner: InvocationOwner,
        stroke: Stroke,
        eligibility: OneShotEligibility,
        now: Duration,
    ): RoutingStep {
        val events = advance(now).toMutableList()
        val consumed = oneShot.takeIf { eligibility == OneShotEligibility.ELIGIBLE }
        if (consumed != null) {
            oneShot = null
            events += RoutingEvent.OneShotConsumed(consumed.modifier)
        }
        val routedStroke = consumed?.let { stroke.copy(modifiers = stroke.modifiers + it.modifier) } ?: stroke
        events += visible(invocations.press(owner, routedStroke).outputs)
        return RoutingStep(events)
    }

    fun pressModifier(owner: InvocationOwner, modifier: Modifier, now: Duration): RoutingStep {
        val events = advance(now).toMutableList()
        events += visible(invocations.pressModifier(owner, modifier).outputs)
        return RoutingStep(events)
    }

    fun release(owner: InvocationOwner, now: Duration): RoutingStep {
        val events = advance(now).toMutableList()
        events += visible(invocations.release(owner).outputs)
        return RoutingStep(events)
    }

    fun beginScope(owner: RoutingOwner, declaration: RoutingScope, now: Duration): RoutingStep {
        val events = advance(now).toMutableList()
        require(scope == null) { "Routing scope '${scope?.owner?.id}' is already active" }

        declaration.suppressedModifiers.sortedBy(Modifier::ordinal).forEach { modifier ->
            if (modifier in invocations.activeModifiers) {
                events += RoutingEvent.Output(OutputTransition.ModifierReleased(modifier))
            }
        }

        val previousContext = context
        val previousHostSource = assumedHostSource
        routeTo(declaration.sourceRoute, events)

        val modifierOwners = declaration.contributedModifiers.associateWith { modifier ->
            InvocationOwner("routing-scope-${internalOwnerSequence++}-${modifier.name.lowercase()}")
        }
        scope = ActiveScope(owner, declaration, previousContext, previousHostSource, modifierOwners)
        modifierOwners.forEach { (modifier, modifierOwner) ->
            events += visible(invocations.pressModifier(modifierOwner, modifier).outputs)
        }
        return RoutingStep(events)
    }

    fun endScope(owner: RoutingOwner, now: Duration): RoutingStep {
        val events = advance(now).toMutableList()
        val active = requireNotNull(scope) { "No routing scope is active" }
        require(active.owner == owner) { "Routing scope '${owner.id}' is not active" }
        require(active.restoreAt == null) { "Routing scope '${owner.id}' is already ending" }
        active.restoreAt = now + active.declaration.restorationDelay
        if (active.restoreAt == now) restore(active, events)
        return RoutingStep(events)
    }

    fun advanceTo(now: Duration): RoutingStep = RoutingStep(advance(now))

    private fun advance(now: Duration): List<RoutingEvent> {
        checkTimestamp(now)
        return buildList {
            oneShot?.takeIf { now >= it.expiresAt }?.let { expired ->
                oneShot = null
                add(RoutingEvent.OneShotExpired(expired.modifier))
            }
            scope?.takeIf { it.restoreAt?.let { deadline -> now >= deadline } == true }?.let { active ->
                restore(active, this)
            }
        }
    }

    private fun restore(active: ActiveScope, events: MutableList<RoutingEvent>) {
        active.modifierOwners.values.forEach { owner ->
            events += visible(invocations.release(owner).outputs)
        }

        val currentLanguage = context.language
        invocations.selectContext(active.previousContext)
        if (currentLanguage != active.previousContext.language) {
            events += RoutingEvent.LogicalLanguageChanged(currentLanguage, active.previousContext.language)
        }
        if (assumedHostSource != active.previousHostSource) {
            events += RoutingEvent.HostSourceSwitchRequested(assumedHostSource, active.previousHostSource)
            assumedHostSource = active.previousHostSource
        }

        val suppressed = active.declaration.suppressedModifiers
        scope = null
        suppressed.sortedBy(Modifier::ordinal).forEach { modifier ->
            if (modifier in invocations.activeModifiers) {
                events += RoutingEvent.Output(OutputTransition.ModifierPressed(modifier))
            }
        }
    }

    private fun routeTo(route: SourceRoute?, events: MutableList<RoutingEvent>) {
        if (route == null) return
        val previousLanguage = context.language
        invocations.selectContext(context.copy(language = route.language))
        if (previousLanguage != route.language) {
            events += RoutingEvent.LogicalLanguageChanged(previousLanguage, route.language)
        }
        if (route is SourceRoute.Host && assumedHostSource != route.inputSource) {
            events += RoutingEvent.HostSourceSwitchRequested(assumedHostSource, route.inputSource)
            assumedHostSource = route.inputSource
        }
    }

    private fun visible(outputs: List<OutputTransition>): List<RoutingEvent.Output> =
        outputs.mapNotNull { transition ->
            val modifier = when (transition) {
                is OutputTransition.ModifierPressed -> transition.modifier
                is OutputTransition.ModifierReleased -> transition.modifier
                else -> null
            }
            transition.takeIf { modifier !in suppressedModifiers() }?.let { RoutingEvent.Output(it) }
        }

    private fun suppressedModifiers(): Set<Modifier> = scope?.declaration?.suppressedModifiers.orEmpty()

    private fun checkTimestamp(now: Duration) {
        require(now >= lastTimestamp) { "Runtime timestamps must be monotonic" }
        lastTimestamp = now
    }
}
