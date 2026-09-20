package dev.srsatt.keyboard.layout.dsl

import dev.srsatt.keyboard.layout.model.BindingIntent
import dev.srsatt.keyboard.layout.model.DeclaredBinding
import dev.srsatt.keyboard.layout.model.DisabledIntent
import dev.srsatt.keyboard.layout.model.HoldIntent
import dev.srsatt.keyboard.layout.model.Holdable
import dev.srsatt.keyboard.layout.model.KeyPosition
import dev.srsatt.keyboard.layout.model.LanguageRef
import dev.srsatt.keyboard.layout.model.LayerContext
import dev.srsatt.keyboard.layout.model.LayerDefinition
import dev.srsatt.keyboard.layout.model.LayerRef
import dev.srsatt.keyboard.layout.model.Layout
import dev.srsatt.keyboard.layout.model.OverlayDefault
import dev.srsatt.keyboard.layout.model.PerformIntent
import dev.srsatt.keyboard.layout.model.Performable
import dev.srsatt.keyboard.layout.model.PhysicalGroup
import dev.srsatt.keyboard.layout.model.SendIntent
import dev.srsatt.keyboard.layout.model.Sendable
import dev.srsatt.keyboard.layout.model.SymbolRef
import dev.srsatt.keyboard.layout.model.TextRef
import dev.srsatt.keyboard.layout.model.TransparentIntent
import dev.srsatt.keyboard.layout.model.TypeIntent

@DslMarker
annotation class LayoutDsl

data object Nothing

data object Below

fun layout(id: String, block: LayoutScope.() -> Unit): Layout {
    require(id.isNotBlank()) { "A layout ID must not be blank" }
    return LayoutScope(id).apply(block).build()
}

@LayoutDsl
class LayoutScope internal constructor(private val id: String) {
    private val layers = mutableListOf<LayerDefinition>()

    fun base(block: LayerScope.() -> Unit) {
        layers += LayerScope(LayerContext.Base, requiresDefault = false).apply(block).build()
    }

    fun language(language: LanguageRef, block: LayerScope.() -> Unit) {
        layers += LayerScope(LayerContext.Language(language), requiresDefault = false).apply(block).build()
    }

    fun shifted(block: LayerScope.() -> Unit) {
        layers += LayerScope(LayerContext.Shift, requiresDefault = false).apply(block).build()
    }

    fun shifted(language: LanguageRef, block: LayerScope.() -> Unit) {
        layers += LayerScope(LayerContext.ShiftedLanguage(language), requiresDefault = false).apply(block).build()
    }

    fun on(layer: LayerRef, block: LayerScope.() -> Unit) {
        layers += LayerScope(LayerContext.Overlay(layer), requiresDefault = true).apply(block).build()
    }

    internal fun build() = Layout(id, layers.toList())
}

@LayoutDsl
class LayerScope internal constructor(
    private val context: LayerContext,
    private val requiresDefault: Boolean,
) {
    private val bindings = mutableListOf<DeclaredBinding>()
    private val groupStack = ArrayDeque<Set<KeyPosition>>()
    private var overlayDefault: OverlayDefault? = null

    val default: DefaultTarget
        get() = DefaultTarget

    operator fun <G : PhysicalGroup> G.invoke(block: G.() -> Unit) {
        val positions = positions.toSet()
        val parent = groupStack.lastOrNull()
        require(parent == null || parent.containsAll(positions)) {
            "Nested physical group contains positions outside its parent group"
        }

        groupStack.addLast(positions)
        try {
            this.block()
        } finally {
            groupStack.removeLast()
        }
    }

    infix fun KeyPosition.sends(value: Sendable) = bind(this, SendIntent(value))

    infix fun KeyPosition.holds(value: Holdable) = bind(this, HoldIntent(value))

    infix fun KeyPosition.performs(value: Performable) = bind(this, PerformIntent(value))

    infix fun KeyPosition.types(value: SymbolRef) = bind(this, TypeIntent(value))

    infix fun KeyPosition.types(value: TextRef) = bind(this, TypeIntent(value))

    infix fun PhysicalGroup.types(value: String) {
        val codePoints = value.codePoints().toArray()
        require(codePoints.size == positions.size) {
            "Row has ${positions.size} positions but received ${codePoints.size} characters"
        }
        positions.zip(codePoints.asIterable()).forEach { (position, codePoint) ->
            bind(position, TypeIntent(SymbolRef(String(Character.toChars(codePoint)))))
        }
    }

    infix fun KeyPosition.does(@Suppress("UNUSED_PARAMETER") value: Nothing) = bind(this, DisabledIntent)

    infix fun KeyPosition.inherits(@Suppress("UNUSED_PARAMETER") value: Below) = bind(this, TransparentIntent)

    infix fun DefaultTarget.does(@Suppress("UNUSED_PARAMETER") value: Nothing) {
        setDefault(OverlayDefault.DISABLED)
    }

    infix fun DefaultTarget.inherits(@Suppress("UNUSED_PARAMETER") value: Below) {
        setDefault(OverlayDefault.TRANSPARENT)
    }

    internal fun build(): LayerDefinition {
        require(!requiresDefault || overlayDefault != null) {
            "Overlay ${context.describe()} must declare `default does Nothing` or `default inherits Below`"
        }
        return LayerDefinition(context, overlayDefault, bindings.toList())
    }

    private fun bind(position: KeyPosition, intent: BindingIntent) {
        val group = groupStack.lastOrNull()
        require(group == null || position in group) {
            "Position '${position.id}' is outside the current physical group"
        }
        bindings += DeclaredBinding(position, intent)
    }

    private fun setDefault(value: OverlayDefault) {
        require(requiresDefault) { "Only overlay layers can declare a default" }
        require(overlayDefault == null) { "Overlay ${context.describe()} declares its default more than once" }
        overlayDefault = value
    }
}

data object DefaultTarget

private fun LayerContext.describe() = when (this) {
    LayerContext.Base -> "Base"
    is LayerContext.Language -> "language '${language.id}'"
    LayerContext.Shift -> "Shift"
    is LayerContext.ShiftedLanguage -> "shifted language '${language.id}'"
    is LayerContext.Overlay -> "'${layer.id}'"
}
