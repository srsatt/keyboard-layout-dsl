package dev.srsatt.keyboard.layout.model

data class HostPlatform(val id: String) {
    init {
        require(id.isNotBlank()) { "Host platform ID must not be blank" }
    }
}

val MacOS = HostPlatform("macos")
val Linux = HostPlatform("linux")
val Windows = HostPlatform("windows")

enum class Key {
    A, B, C, D, E, F, G, H, I, J, K, L, M,
    N, O, P, Q, R, S, T, U, V, W, X, Y, Z,
    BACKSPACE, ENTER, SPACE, TAB,
}

enum class Modifier {
    ALT, CMD, CTRL, SHIFT,
}

val Alt = Modifier.ALT
val Cmd = Modifier.CMD
val Ctrl = Modifier.CTRL
val Shift = Modifier.SHIFT
val Backspace = Key.BACKSPACE

data class Stroke(
    val key: Key,
    val modifiers: Set<Modifier> = emptySet(),
)

operator fun Modifier.plus(key: Key) = Stroke(key, setOf(this))

operator fun Modifier.plus(other: Modifier) = ModifierSet(setOf(this, other))

data class ModifierSet(val modifiers: Set<Modifier>)

operator fun ModifierSet.plus(modifier: Modifier) = ModifierSet(modifiers + modifier)

operator fun ModifierSet.plus(key: Key) = Stroke(key, modifiers)

enum class ActionDelivery {
    ONCE,
    PAIRED,
    HOST_EVENT,
}

sealed interface ActionImplementation {
    val delivery: ActionDelivery
}

data class GeneralActionImplementation(val effects: List<ActionEffect>) : ActionImplementation {
    override val delivery = ActionDelivery.ONCE
}

data class KeystrokeActionImplementation(val stroke: Stroke) : ActionImplementation {
    override val delivery = ActionDelivery.PAIRED
}

data class HostActionImplementation(val eventId: String) : ActionImplementation {
    override val delivery = ActionDelivery.HOST_EVENT
}

sealed interface ActionReference {
    val id: String
}

class ActionRef internal constructor(
    override val id: String,
    val description: String?,
    internal val implementations: List<ImplementationChoice<GeneralActionImplementation>>,
) : ActionReference, Performable {
    override fun equals(other: Any?) = other is ActionRef && id == other.id
    override fun hashCode() = id.hashCode()
    override fun toString() = "ActionRef(id=$id)"
}

class KeystrokeActionRef internal constructor(
    override val id: String,
    val description: String?,
    internal val implementations: List<ImplementationChoice<KeystrokeActionImplementation>>,
) : ActionReference, Sendable, Performable {
    override fun equals(other: Any?) = other is KeystrokeActionRef && id == other.id
    override fun hashCode() = id.hashCode()
    override fun toString() = "KeystrokeActionRef(id=$id)"
}

class HostActionRef internal constructor(
    override val id: String,
) : ActionReference, Performable {
    override fun equals(other: Any?) = other is HostActionRef && id == other.id
    override fun hashCode() = id.hashCode()
    override fun toString() = "HostActionRef(id=$id)"
}

internal data class ImplementationChoice<T>(
    val host: HostPlatform?,
    val implementation: T,
)

fun action(id: String, block: ActionBuilder.() -> Unit): ActionRef {
    requireActionId(id)
    return ActionBuilder().apply(block).build(id)
}

fun keystrokeAction(id: String, block: KeystrokeActionBuilder.() -> Unit): KeystrokeActionRef {
    requireActionId(id)
    return KeystrokeActionBuilder().apply(block).build(id)
}

fun shortcutAction(id: String, stroke: Stroke): KeystrokeActionRef =
    keystrokeAction(id) { implementation { stroke } }

fun hostAction(id: String): HostActionRef {
    requireActionId(id)
    return HostActionRef(id)
}

class ActionBuilder internal constructor() {
    private var description: String? = null
    private val implementations = mutableListOf<ImplementationChoice<GeneralActionImplementation>>()

    fun description(value: String) {
        require(value.isNotBlank()) { "Action description must not be blank" }
        description = value
    }

    fun implementation(block: TapScope.() -> Unit) {
        implementations += ImplementationChoice(null, TapScope().apply(block).build())
    }

    fun implementationIn(host: HostPlatform, block: TapScope.() -> Unit) {
        implementations += ImplementationChoice(host, TapScope().apply(block).build())
    }

    internal fun build(id: String) = ActionRef(id, description, implementations.toList())
}

class KeystrokeActionBuilder internal constructor() {
    private var description: String? = null
    private val implementations = mutableListOf<ImplementationChoice<KeystrokeActionImplementation>>()

    fun description(value: String) {
        require(value.isNotBlank()) { "Action description must not be blank" }
        description = value
    }

    fun implementation(block: () -> Stroke) {
        implementations += ImplementationChoice(null, KeystrokeActionImplementation(block()))
    }

    fun implementationIn(host: HostPlatform, block: () -> Stroke) {
        implementations += ImplementationChoice(host, KeystrokeActionImplementation(block()))
    }

    internal fun build(id: String) = KeystrokeActionRef(id, description, implementations.toList())
}

class ActionOverrides internal constructor(
    internal val general: Map<String, List<ImplementationChoice<GeneralActionImplementation>>>,
    internal val keystrokes: Map<String, List<ImplementationChoice<KeystrokeActionImplementation>>>,
) {
    internal companion object {
        val Empty = ActionOverrides(emptyMap(), emptyMap())
    }
}

class ActionsScope internal constructor() {
    private val general = mutableMapOf<String, MutableList<ImplementationChoice<GeneralActionImplementation>>>()
    private val keystrokes = mutableMapOf<String, MutableList<ImplementationChoice<KeystrokeActionImplementation>>>()

    operator fun ActionRef.invoke(block: ActionOverrideBuilder.() -> Unit) {
        general.getOrPut(id, ::mutableListOf) += ActionOverrideBuilder().apply(block).implementations
    }

    operator fun KeystrokeActionRef.invoke(block: KeystrokeActionOverrideBuilder.() -> Unit) {
        keystrokes.getOrPut(id, ::mutableListOf) += KeystrokeActionOverrideBuilder().apply(block).implementations
    }

    internal fun build() = ActionOverrides(
        general.mapValues { it.value.toList() },
        keystrokes.mapValues { it.value.toList() },
    )
}

class ActionOverrideBuilder internal constructor() {
    internal val implementations = mutableListOf<ImplementationChoice<GeneralActionImplementation>>()

    fun implementation(block: TapScope.() -> Unit) {
        implementations += ImplementationChoice(null, TapScope().apply(block).build())
    }

    fun implementationIn(host: HostPlatform, block: TapScope.() -> Unit) {
        implementations += ImplementationChoice(host, TapScope().apply(block).build())
    }
}

class KeystrokeActionOverrideBuilder internal constructor() {
    internal val implementations = mutableListOf<ImplementationChoice<KeystrokeActionImplementation>>()

    fun implementation(block: () -> Stroke) {
        implementations += ImplementationChoice(null, KeystrokeActionImplementation(block()))
    }

    fun implementationIn(host: HostPlatform, block: () -> Stroke) {
        implementations += ImplementationChoice(host, KeystrokeActionImplementation(block()))
    }
}

fun actions(block: ActionsScope.() -> Unit): ActionOverrides = ActionsScope().apply(block).build()

fun ActionRef.resolveImplementation(
    host: HostPlatform,
    overrides: ActionOverrides = ActionOverrides.Empty,
): GeneralActionImplementation = selectImplementation(
    id,
    host,
    overrides.general[id].orEmpty(),
    implementations,
)

fun KeystrokeActionRef.resolveImplementation(
    host: HostPlatform,
    overrides: ActionOverrides = ActionOverrides.Empty,
): KeystrokeActionImplementation = selectImplementation(
    id,
    host,
    overrides.keystrokes[id].orEmpty(),
    implementations,
)

fun HostActionRef.resolveImplementation() = HostActionImplementation(id)

private fun <T> selectImplementation(
    actionId: String,
    host: HostPlatform,
    overrides: List<ImplementationChoice<T>>,
    defaults: List<ImplementationChoice<T>>,
): T {
    selected(actionId, host, "consumer overrides", overrides)?.let { return it }
    selected(actionId, host, "action declaration", defaults)?.let { return it }
    throw IllegalArgumentException("Action '$actionId' has no implementation for host '${host.id}'")
}

private fun <T> selected(
    actionId: String,
    host: HostPlatform,
    source: String,
    choices: List<ImplementationChoice<T>>,
): T? {
    val selected = choices.filter { it.host == host }.ifEmpty { choices.filter { it.host == null } }
    require(selected.size <= 1) {
        "Action '$actionId' has duplicate selected implementations for host '${host.id}' in $source"
    }
    return selected.singleOrNull()?.implementation
}

val DeleteWord = keystrokeAction("edit.delete-word") {
    implementationIn(MacOS) { Alt + Backspace }
}

val SwitchLanguage = action("language.switch") { }
val SyncLanguage = action("language.sync") { }
val TemporaryLanguage = action("language.temporary") { }
val MuteSounds = action("sound.mute") { }
val UnmuteSounds = action("sound.unmute") { }
val ToggleSounds = action("sound.toggle") { }

private fun requireActionId(id: String) {
    require(id.isNotBlank()) { "Action ID must not be blank" }
}
