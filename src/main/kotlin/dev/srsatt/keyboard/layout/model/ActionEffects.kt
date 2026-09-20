package dev.srsatt.keyboard.layout.model

import kotlin.time.Duration

const val MAX_ACTION_EFFECTS_PER_IMPLEMENTATION = 256

sealed interface ActionEffect

data class RawTapEffect(val stroke: Stroke) : ActionEffect

data class TypeTextEffect(val text: String) : ActionEffect {
    init {
        require(text.isNotEmpty()) { "Effect text must not be empty" }
    }
}

data class InvokeActionEffect(val action: ActionReference) : ActionEffect

data class ScopedModifierEffect(
    val modifier: Modifier,
    val effects: List<ActionEffect>,
) : ActionEffect

data class DelayEffect(val duration: Duration) : ActionEffect {
    init {
        require(duration > Duration.ZERO) { "Effect delay must be positive" }
    }
}

class TapScope internal constructor(
    private val budget: EffectBudget = EffectBudget(MAX_ACTION_EFFECTS_PER_IMPLEMENTATION),
) {
    private val effects = mutableListOf<ActionEffect>()

    /** Records a raw keyboard tap, without invoking semantic action behavior. */
    fun tap(stroke: Stroke) = add(RawTapEffect(stroke))

    fun typeText(text: String) = add(TypeTextEffect(text))

    /** Records a semantic invocation, preserving the referenced action identity. */
    fun perform(action: ActionReference) = add(InvokeActionEffect(action))

    fun withModifier(modifier: Modifier, block: TapScope.() -> Unit) {
        val nested = TapScope(budget).apply(block).buildEffects()
        require(nested.isNotEmpty()) { "A scoped modifier must contain at least one effect" }
        add(ScopedModifierEffect(modifier, nested))
    }

    fun delay(duration: Duration) = add(DelayEffect(duration))

    internal fun build(): GeneralActionImplementation {
        val built = buildEffects()
        require(built.isNotEmpty()) { "A general action implementation must declare at least one effect" }
        return GeneralActionImplementation(built)
    }

    private fun add(effect: ActionEffect) {
        budget.record()
        effects += effect
    }

    private fun buildEffects(): List<ActionEffect> = effects.toList()
}

internal class EffectBudget(private val limit: Int) {
    private var count = 0

    fun record() {
        require(++count <= limit) {
            "An action implementation may contain at most $limit effects"
        }
    }
}

/** Rejects cycles among the selected general-action implementations. */
fun validateActionCycles(
    actions: Iterable<ActionRef>,
    host: HostPlatform,
    overrides: ActionOverrides = ActionOverrides.Empty,
) {
    val declarations = actions.toList()
    val duplicates = declarations.groupBy(ActionRef::id).filterValues { it.size > 1 }.keys.sorted()
    require(duplicates.isEmpty()) { "Duplicate action IDs: ${duplicates.joinToString()}" }

    val graph = declarations.associate { action ->
        action.id to action.resolveImplementation(host, overrides).effects.invokedGeneralActionIds()
    }
    val complete = mutableSetOf<String>()
    val active = linkedSetOf<String>()

    fun visit(id: String) {
        if (id in complete || id !in graph) return
        if (!active.add(id)) {
            val cycle = active.dropWhile { it != id } + id
            throw IllegalArgumentException("Action reference cycle: ${cycle.joinToString(" -> ")}")
        }
        graph.getValue(id).forEach(::visit)
        active.remove(id)
        complete += id
    }

    graph.keys.forEach(::visit)
}

private fun List<ActionEffect>.invokedGeneralActionIds(): Set<String> = buildSet {
    this@invokedGeneralActionIds.forEach { effect ->
        when (effect) {
            is InvokeActionEffect -> (effect.action as? ActionRef)?.id?.let(::add)
            is ScopedModifierEffect -> addAll(effect.effects.invokedGeneralActionIds())
            else -> Unit
        }
    }
}
