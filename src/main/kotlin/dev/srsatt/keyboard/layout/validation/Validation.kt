package dev.srsatt.keyboard.layout.validation

import dev.srsatt.keyboard.layout.model.DeclaredBinding
import dev.srsatt.keyboard.layout.model.DeclaredChord
import dev.srsatt.keyboard.layout.model.LanguageRef
import dev.srsatt.keyboard.layout.model.LayerContext
import dev.srsatt.keyboard.layout.model.Layout
import dev.srsatt.keyboard.layout.model.SourceProvenance
import dev.srsatt.keyboard.layout.model.TransparentIntent

/** Validates conflicts only after all ordinary Kotlin layout fragments have contributed. */
fun validateLayout(layout: Layout) {
    val declarations = layout.layers.flatMap { layer ->
        layer.bindings.map { BindingDeclaration(it, layer.context) }
    }
    val chords = layout.layers.flatMap { layer ->
        layer.chords.map { ChordDeclaration(it, layer.context) }
    }
    val conflicts = buildList {
        addAll(bindingConflicts(declarations))
        addAll(chordConflicts(chords))
    }.sorted()

    require(conflicts.isEmpty()) {
        "Layout '${layout.id}' has conflicting declarations:\n${conflicts.joinToString("\n") { "- $it" }}"
    }
}

private data class BindingDeclaration(
    val binding: DeclaredBinding,
    val context: LayerContext,
)

private data class ChordDeclaration(
    val chord: DeclaredChord,
    val context: LayerContext,
)

private data class EffectiveCondition(
    val language: LanguageRef? = null,
    val shifted: Boolean? = null,
    val activeLayer: String? = null,
) {
    fun overlaps(other: EffectiveCondition) =
        (language == null || other.language == null || language == other.language) &&
            (shifted == null || other.shifted == null || shifted == other.shifted) &&
            (activeLayer == null || other.activeLayer == null || activeLayer == other.activeLayer)
}

private fun bindingConflicts(declarations: List<BindingDeclaration>) = pairs(declarations).mapNotNull { (first, second) ->
    if (
        first.binding.intent is TransparentIntent ||
        second.binding.intent is TransparentIntent ||
        first.binding.position != second.binding.position ||
        !first.context.condition().overlaps(second.context.condition()) ||
        first.context.bindingPrecedence() != second.context.bindingPrecedence()
    ) {
        null
    } else {
        "binding '${first.binding.position.id}' overlaps in ${first.context.describe()} and " +
            "${second.context.describe()} at ${first.binding.source.location()} and ${second.binding.source.location()}"
    }
}

private fun chordConflicts(declarations: List<ChordDeclaration>) = pairs(declarations).mapNotNull { (first, second) ->
    if (
        first.chord.keys != second.chord.keys ||
        !first.context.condition().overlaps(second.context.condition())
    ) {
        null
    } else {
        "chord ${first.chord.keys} overlaps in ${first.context.describe()} and ${second.context.describe()} " +
            "at ${first.chord.source.location()} and ${second.chord.source.location()}"
    }
}

private fun LayerContext.condition() = when (this) {
    LayerContext.Base -> EffectiveCondition()
    is LayerContext.Language -> EffectiveCondition(language = language)
    LayerContext.Shift -> EffectiveCondition(shifted = true)
    is LayerContext.ShiftedLanguage -> EffectiveCondition(language = language, shifted = true)
    is LayerContext.Overlay -> EffectiveCondition(activeLayer = layer.id)
}

/** Different precedence is an explicit Base/language/Shift/layer override, not a conflict. */
private fun LayerContext.bindingPrecedence() = when (this) {
    LayerContext.Base -> 0
    is LayerContext.Language -> 1
    LayerContext.Shift -> 2
    is LayerContext.ShiftedLanguage -> 3
    is LayerContext.Overlay -> 4
}

private fun LayerContext.describe() = when (this) {
    LayerContext.Base -> "Base"
    is LayerContext.Language -> "language '${language.id}'"
    LayerContext.Shift -> "Shift"
    is LayerContext.ShiftedLanguage -> "shifted language '${language.id}'"
    is LayerContext.Overlay -> "layer '${layer.id}'"
}

private fun SourceProvenance.location() = "$file:$line"

private fun <T> pairs(values: List<T>) = sequence {
    for (first in values.indices) {
        for (second in first + 1 until values.size) {
            yield(values[first] to values[second])
        }
    }
}
