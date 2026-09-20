package dev.srsatt.keyboard.layout.dsl

import dev.srsatt.keyboard.layout.model.BindingIntent
import dev.srsatt.keyboard.layout.model.ChordKeys
import dev.srsatt.keyboard.layout.model.DeclaredBinding
import dev.srsatt.keyboard.layout.model.DeclaredChord
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
import dev.srsatt.keyboard.layout.model.SourceProvenance
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
    private val chords = mutableListOf<DeclaredChord>()
    private val groupStack = ArrayDeque<Set<KeyPosition>>()
    private val sharedChordKeys = ArrayDeque<List<KeyPosition>>()
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

    infix fun KeyPosition.and(other: KeyPosition) = ChordKeys.from(listOf(this, other))

    infix fun ChordKeys.and(other: KeyPosition) = ChordKeys.from(positions + other)

    fun chordsWith(vararg groups: PhysicalGroup, block: LayerScope.() -> Unit) {
        val added = groups.flatMap(PhysicalGroup::positions)
        require(added.isNotEmpty()) { "chordsWith requires at least one shared physical position" }
        validateUniqueChordPositions(sharedChordKeys.flatten() + added)
        sharedChordKeys.addLast(added)
        try {
            block()
        } finally {
            sharedChordKeys.removeLast()
        }
    }

    infix fun KeyPosition.sends(value: Sendable) = bind(this, SendIntent(value))

    infix fun KeyPosition.holds(value: Holdable) = bind(this, HoldIntent(value))

    infix fun KeyPosition.performs(value: Performable) = bind(this, PerformIntent(value))

    infix fun KeyPosition.types(value: SymbolRef) = bind(this, TypeIntent(value))

    infix fun KeyPosition.types(value: TextRef) = bind(this, TypeIntent(value))

    infix fun ChordKeys.sends(value: Sendable) = bindChord(this, SendIntent(value))

    infix fun ChordKeys.holds(value: Holdable) = bindChord(this, HoldIntent(value))

    infix fun ChordKeys.performs(value: Performable) = bindChord(this, PerformIntent(value))

    infix fun ChordKeys.types(value: SymbolRef) = bindChord(this, TypeIntent(value))

    infix fun ChordKeys.types(value: TextRef) = bindChord(this, TypeIntent(value))

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
        return LayerDefinition(context, overlayDefault, bindings.toList(), chords.toList())
    }

    private fun bind(position: KeyPosition, intent: BindingIntent) {
        val group = groupStack.lastOrNull()
        require(group == null || position in group) {
            "Position '${position.id}' is outside the current physical group"
        }
        if (sharedChordKeys.isEmpty()) {
            bindings += DeclaredBinding(position, intent)
        } else {
            recordChord(ChordKeys.from(sharedChordKeys.flatten() + position), intent)
        }
    }

    private fun bindChord(keys: ChordKeys, intent: BindingIntent) {
        val group = groupStack.lastOrNull()
        require(group == null || group.containsAll(keys.positions)) {
            "Chord contains positions outside the current physical group"
        }
        recordChord(ChordKeys.from(sharedChordKeys.flatten() + keys.positions), intent)
    }

    private fun recordChord(keys: ChordKeys, intent: BindingIntent) {
        chords += DeclaredChord(keys, intent, sourceProvenance())
    }

    private fun setDefault(value: OverlayDefault) {
        require(requiresDefault) { "Only overlay layers can declare a default" }
        require(overlayDefault == null) { "Overlay ${context.describe()} declares its default more than once" }
        overlayDefault = value
    }
}

private fun validateUniqueChordPositions(positions: List<KeyPosition>) {
    val duplicateIds = positions.groupBy(KeyPosition::id).filterValues { it.size > 1 }.keys.sorted()
    require(duplicateIds.isEmpty()) {
        "Chord repeats physical position aliases: ${duplicateIds.joinToString()}"
    }
}

private fun sourceProvenance(): SourceProvenance {
    val frame = Thread.currentThread().stackTrace.firstOrNull {
        it.fileName?.endsWith(".kt") == true &&
            !it.className.startsWith("dev.srsatt.keyboard.layout.dsl.LayerScope") &&
            !it.className.startsWith("dev.srsatt.keyboard.layout.dsl.LayoutDslKt")
    }
    val packagePath = frame?.className?.substringBeforeLast('.', "")?.replace('.', '/')
    val file = listOfNotNull(packagePath?.takeIf(String::isNotEmpty), frame?.fileName).joinToString("/")
    return SourceProvenance(file.ifEmpty { "<unknown>" }, frame?.lineNumber?.takeIf { it > 0 } ?: 1)
}

data object DefaultTarget

private fun LayerContext.describe() = when (this) {
    LayerContext.Base -> "Base"
    is LayerContext.Language -> "language '${language.id}'"
    LayerContext.Shift -> "Shift"
    is LayerContext.ShiftedLanguage -> "shifted language '${language.id}'"
    is LayerContext.Overlay -> "'${layer.id}'"
}
