package dev.srsatt.keyboard.layout.model

/** A value whose press and release are delivered as a pair. */
interface Sendable

/** A state that remains active while its physical trigger is held. */
interface Holdable

/** A value that can be invoked once after its trigger is recognized. */
interface Performable

data class KeystrokeRef(val id: String) : Sendable, Performable {
    init {
        requireValidId(id, "Keystroke")
    }
}

data class ModifierRef(val id: String) : Holdable {
    init {
        requireValidId(id, "Modifier")
    }
}

data class CommandRef(val id: String) : Performable {
    init {
        requireValidId(id, "Command")
    }
}

data class LayerRef(val id: String) : Holdable {
    init {
        requireValidId(id, "Layer")
    }
}

data class LanguageRef(val id: String) {
    init {
        requireValidId(id, "Language")
    }
}

data class SymbolRef(val value: String) {
    init {
        require(value.codePointCount(0, value.length) == 1) {
            "A symbol must contain exactly one Unicode code point"
        }
    }
}

fun keystroke(id: String) = KeystrokeRef(id)

fun modifier(id: String) = ModifierRef(id)

fun command(id: String) = CommandRef(id)

fun layer(id: String) = LayerRef(id)

fun language(id: String) = LanguageRef(id)

fun symbol(value: Char) = SymbolRef(value.toString())

fun symbol(value: String) = SymbolRef(value)

sealed interface BindingIntent

data class SendIntent(val value: Sendable) : BindingIntent

data class HoldIntent(val value: Holdable) : BindingIntent

data class PerformIntent(val value: Performable) : BindingIntent

data class TypeIntent(val value: SymbolRef) : BindingIntent

data object DisabledIntent : BindingIntent

data object TransparentIntent : BindingIntent

data class DeclaredBinding(
    val position: KeyPosition,
    val intent: BindingIntent,
)

enum class OverlayDefault {
    DISABLED,
    TRANSPARENT,
}

sealed interface LayerContext {
    data object Base : LayerContext
    data class Language(val language: LanguageRef) : LayerContext
    data class Overlay(val layer: LayerRef) : LayerContext
}

data class LayerDefinition(
    val context: LayerContext,
    val default: OverlayDefault?,
    val bindings: List<DeclaredBinding>,
)

data class Layout(
    val id: String,
    val layers: List<LayerDefinition>,
)

private fun requireValidId(id: String, kind: String) {
    require(id.isNotBlank()) { "$kind ID must not be blank" }
}
