package dev.srsatt.keyboard.layout.runtime

import dev.srsatt.keyboard.layout.model.ActionOverrides
import dev.srsatt.keyboard.layout.model.HostPlatform
import dev.srsatt.keyboard.layout.model.Key
import dev.srsatt.keyboard.layout.model.KeystrokeActionRef
import dev.srsatt.keyboard.layout.model.LanguageRef
import dev.srsatt.keyboard.layout.model.LayerRef
import dev.srsatt.keyboard.layout.model.Modifier
import dev.srsatt.keyboard.layout.model.Stroke
import dev.srsatt.keyboard.layout.model.resolveImplementation

data class InvocationOwner(val id: String) {
    init {
        require(id.isNotBlank()) { "Invocation owner ID must not be blank" }
    }
}

data class RuntimeContext(
    val host: HostPlatform,
    val language: LanguageRef? = null,
    val activeLayers: Set<LayerRef> = emptySet(),
) {
    internal fun snapshot() = copy(activeLayers = activeLayers.toSet())
}

data class OwnedOutput(
    val key: Key? = null,
    val modifiers: Set<Modifier> = emptySet(),
) {
    init {
        require(key != null || modifiers.isNotEmpty()) { "Owned output must contain a key or modifier" }
    }
}

data class ResolvedInvocation(
    val owner: InvocationOwner,
    val context: RuntimeContext,
    val actionId: String?,
    val output: OwnedOutput,
)

sealed interface OutputTransition {
    data class KeyPressed(val key: Key) : OutputTransition
    data class KeyReleased(val key: Key) : OutputTransition
    data class ModifierPressed(val modifier: Modifier) : OutputTransition
    data class ModifierReleased(val modifier: Modifier) : OutputTransition
}

data class InvocationTransition(
    val invocation: ResolvedInvocation,
    val outputs: List<OutputTransition>,
)

/**
 * Owns paired keyboard output by invocation. Context and action resolution are captured at press;
 * release removes only that invocation's contributions.
 */
class InvocationRuntime(
    initialContext: RuntimeContext,
    private val overrides: ActionOverrides = ActionOverrides.Empty,
) {
    var context: RuntimeContext = initialContext.snapshot()
        private set

    private val invocations = linkedMapOf<InvocationOwner, ResolvedInvocation>()
    private val keyOwners = mutableMapOf<Key, MutableSet<InvocationOwner>>()
    private val modifierOwners = mutableMapOf<Modifier, MutableSet<InvocationOwner>>()

    val activeKeys: Set<Key>
        get() = keyOwners.keys.toSet()

    val activeModifiers: Set<Modifier>
        get() = modifierOwners.keys.toSet()

    fun selectContext(next: RuntimeContext) {
        context = next.snapshot()
    }

    fun press(owner: InvocationOwner, action: KeystrokeActionRef): InvocationTransition {
        requireAvailable(owner)
        val implementation = action.resolveImplementation(context.host, overrides)
        return acquire(owner, action.id, OwnedOutput(implementation.stroke.key, implementation.stroke.modifiers))
    }

    fun press(owner: InvocationOwner, stroke: Stroke): InvocationTransition {
        requireAvailable(owner)
        return acquire(owner, null, OwnedOutput(stroke.key, stroke.modifiers))
    }

    fun pressModifier(owner: InvocationOwner, modifier: Modifier): InvocationTransition {
        requireAvailable(owner)
        return acquire(owner, null, OwnedOutput(modifiers = setOf(modifier)))
    }

    fun release(owner: InvocationOwner): InvocationTransition {
        val invocation = requireNotNull(invocations.remove(owner)) {
            "Invocation '${owner.id}' is not active"
        }
        val transitions = buildList {
            invocation.output.key?.let { key ->
                if (release(keyOwners, key, owner)) add(OutputTransition.KeyReleased(key))
            }
            invocation.output.modifiers.sortedByDescending(Modifier::ordinal).forEach { modifier ->
                if (release(modifierOwners, modifier, owner)) add(OutputTransition.ModifierReleased(modifier))
            }
        }
        return InvocationTransition(invocation, transitions)
    }

    private fun acquire(owner: InvocationOwner, actionId: String?, output: OwnedOutput): InvocationTransition {
        val invocation = ResolvedInvocation(owner, context.snapshot(), actionId, output)
        invocations[owner] = invocation
        val transitions = buildList {
            output.modifiers.sortedBy(Modifier::ordinal).forEach { modifier ->
                if (acquire(modifierOwners, modifier, owner)) add(OutputTransition.ModifierPressed(modifier))
            }
            output.key?.let { key ->
                if (acquire(keyOwners, key, owner)) add(OutputTransition.KeyPressed(key))
            }
        }
        return InvocationTransition(invocation, transitions)
    }

    private fun requireAvailable(owner: InvocationOwner) {
        require(owner !in invocations) { "Invocation '${owner.id}' is already active" }
    }

    private fun <T> acquire(
        ownersByResource: MutableMap<T, MutableSet<InvocationOwner>>,
        resource: T,
        owner: InvocationOwner,
    ): Boolean {
        val owners = ownersByResource.getOrPut(resource, ::linkedSetOf)
        val wasInactive = owners.isEmpty()
        check(owners.add(owner)) { "Invocation '${owner.id}' already owns $resource" }
        return wasInactive
    }

    private fun <T> release(
        ownersByResource: MutableMap<T, MutableSet<InvocationOwner>>,
        resource: T,
        owner: InvocationOwner,
    ): Boolean {
        val owners = checkNotNull(ownersByResource[resource]) { "No owners recorded for $resource" }
        check(owners.remove(owner)) { "Invocation '${owner.id}' does not own $resource" }
        if (owners.isNotEmpty()) return false
        ownersByResource.remove(resource)
        return true
    }
}
