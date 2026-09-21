package dev.srsatt.keyboard.layout.validation

import dev.srsatt.keyboard.layout.model.DeclaredBinding
import dev.srsatt.keyboard.layout.model.DeclaredChord
import dev.srsatt.keyboard.layout.model.LanguageRef
import dev.srsatt.keyboard.layout.model.LayerContext
import dev.srsatt.keyboard.layout.model.Layout
import dev.srsatt.keyboard.layout.model.SourceProvenance
import dev.srsatt.keyboard.layout.model.TransparentIntent
import dev.srsatt.keyboard.layout.model.TriggerBehavior

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
        addAll(recognitionConflicts(declarations, chords))
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

private data class TriggerCandidate(
    val keys: Set<String>,
    val behavior: TriggerBehavior,
    val source: SourceProvenance,
    val context: LayerContext,
)

private fun recognitionConflicts(
    bindings: List<BindingDeclaration>,
    chords: List<ChordDeclaration>,
): List<String> {
    val candidates = buildList {
        bindings.filterNot { it.binding.intent is TransparentIntent }.forEach {
            add(
                TriggerCandidate(
                    keys = setOf(it.binding.position.id),
                    behavior = it.binding.triggerBehavior,
                    source = it.binding.source,
                    context = it.context,
                ),
            )
        }
        chords.forEach {
            add(
                TriggerCandidate(
                    keys = it.chord.keys.positions.mapTo(linkedSetOf()) { position -> position.id },
                    behavior = it.chord.triggerBehavior,
                    source = it.chord.source,
                    context = it.context,
                ),
            )
        }
    }

    return pairs(candidates).mapNotNull { (first, second) ->
        if (!first.context.condition().overlaps(second.context.condition())) return@mapNotNull null
        val shared = first.keys intersect second.keys
        if (shared.isEmpty() || first.keys == second.keys) return@mapNotNull null

        val smaller = when {
            second.keys.containsAll(first.keys) -> first
            first.keys.containsAll(second.keys) -> second
            else -> return@mapNotNull "ambiguous partial chord overlap ${first.keys.describe()} and " +
                "${second.keys.describe()} at ${first.source.location()} and ${second.source.location()}"
        }
        if (smaller.behavior == TriggerBehavior.IMMEDIATE_OPAQUE_OR_DESTRUCTIVE) {
            "unsafe immediate prefix ${smaller.keys.describe()} for ${
                (if (smaller === first) second else first).keys.describe()
            } at ${smaller.source.location()} and ${
                (if (smaller === first) second else first).source.location()
            }"
        } else {
            null
        }
    }.toList()
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

private fun Set<String>.describe() = sorted().joinToString(prefix = "[", postfix = "]")

private fun <T> pairs(values: List<T>) = sequence {
    for (first in values.indices) {
        for (second in first + 1 until values.size) {
            yield(values[first] to values[second])
        }
    }
}
