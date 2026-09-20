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

sealed interface TextIntentValue {
    val value: String
}

data class SymbolRef(override val value: String) : TextIntentValue {
    init {
        require(value.codePointCount(0, value.length) == 1) {
            "A symbol must contain exactly one Unicode code point"
        }
    }
}

data class TextRef(override val value: String) : TextIntentValue {
    init {
        require(value.isNotEmpty()) { "Text must not be empty" }
    }
}

fun keystroke(id: String) = KeystrokeRef(id)

fun modifier(id: String) = ModifierRef(id)

fun command(id: String) = CommandRef(id)

fun layer(id: String) = LayerRef(id)

fun language(id: String) = LanguageRef(id)

fun symbol(value: Char) = SymbolRef(value.toString())

fun symbol(value: String) = SymbolRef(value)

fun text(value: String) = TextRef(value)

sealed interface BindingIntent

data class SendIntent(val value: Sendable) : BindingIntent

data class HoldIntent(val value: Holdable) : BindingIntent

data class PerformIntent(val value: Performable) : BindingIntent

data class TypeIntent(val value: TextIntentValue) : BindingIntent

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
    data object Shift : LayerContext
    data class ShiftedLanguage(val language: LanguageRef) : LayerContext
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

enum class BindingOrigin {
    BASE,
    LANGUAGE,
    DERIVED_SHIFT,
    SHIFT_OVERRIDE,
    LANGUAGE_SHIFT_OVERRIDE,
}

data class EffectiveBinding(
    val position: KeyPosition,
    val intent: BindingIntent,
    val origin: BindingOrigin,
)

/** Resolves the effective Base typing state for one language and Shift state. */
fun Layout.resolveBindings(
    profile: KeyboardProfile,
    language: LanguageRef? = null,
    shifted: Boolean = false,
): List<EffectiveBinding> {
    val applicable = buildList {
        add(LayerContext.Base to BindingOrigin.BASE)
        language?.let { add(LayerContext.Language(it) to BindingOrigin.LANGUAGE) }
    }
    val resolved = linkedMapOf<KeyPosition, EffectiveBinding>()

    applicable.forEach { (context, origin) -> applyContext(context, origin, resolved) }

    if (shifted) {
        resolved.replaceAll { position, binding ->
            deriveShift(binding)?.let { EffectiveBinding(position, it, BindingOrigin.DERIVED_SHIFT) } ?: binding
        }
        applyContext(LayerContext.Shift, BindingOrigin.SHIFT_OVERRIDE, resolved)
        language?.let {
            applyContext(LayerContext.ShiftedLanguage(it), BindingOrigin.LANGUAGE_SHIFT_OVERRIDE, resolved)
        }
    }

    val profilePositions = profile.positions.toSet()
    val outsideProfile = resolved.keys - profilePositions
    require(outsideProfile.isEmpty()) {
        "Layout '$id' binds positions outside profile '${profile.id}': " +
            outsideProfile.map(KeyPosition::id).sorted().joinToString()
    }

    val missing = profile.positions.filterNot(resolved::containsKey)
    require(missing.isEmpty()) {
        val variant = buildString {
            append(language?.id ?: "common")
            if (shifted) append(" + Shift")
        }
        "Layout '$id' has undeclared effective Base positions for $variant: " +
            missing.joinToString { it.id }
    }

    return profile.positions.map(resolved::getValue)
}

private fun Layout.applyContext(
    context: LayerContext,
    origin: BindingOrigin,
    resolved: MutableMap<KeyPosition, EffectiveBinding>,
) {
    val declarations = layers.filter { it.context == context }.flatMap(LayerDefinition::bindings)
    val duplicates = declarations.groupBy(DeclaredBinding::position).filterValues { it.size > 1 }.keys
    require(duplicates.isEmpty()) {
        "Layout '$id' declares positions more than once in ${context.description()}: " +
            duplicates.map(KeyPosition::id).sorted().joinToString()
    }
    declarations.forEach { binding ->
        if (binding.intent !is TransparentIntent) {
            resolved[binding.position] = EffectiveBinding(binding.position, binding.intent, origin)
        }
    }
}

private fun deriveShift(binding: EffectiveBinding): BindingIntent? {
    val symbol = (binding.intent as? TypeIntent)?.value as? SymbolRef ?: return null
    val codePoint = symbol.value.codePointAt(0)
    if (!Character.isLetter(codePoint)) return null
    val uppercase = symbol.value.uppercase()
    if (uppercase.codePointCount(0, uppercase.length) != 1 || uppercase == symbol.value) return null
    return TypeIntent(SymbolRef(uppercase))
}

private fun LayerContext.description() = when (this) {
    LayerContext.Base -> "Base"
    is LayerContext.Language -> "language '${language.id}'"
    LayerContext.Shift -> "Shift"
    is LayerContext.ShiftedLanguage -> "shifted language '${language.id}'"
    is LayerContext.Overlay -> "layer '${layer.id}'"
}

private fun requireValidId(id: String, kind: String) {
    require(id.isNotBlank()) { "$kind ID must not be blank" }
}
