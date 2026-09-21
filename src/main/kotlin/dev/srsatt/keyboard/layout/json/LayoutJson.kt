package dev.srsatt.keyboard.layout.json

import dev.srsatt.keyboard.layout.model.KeyPosition

data class LayoutJsonVariant(
    val id: String,
    val bindings: List<LayoutJsonBinding>,
)

data class LayoutJsonBinding(
    val position: KeyPosition,
    val value: String,
)

data class LayoutJsonChord(
    val id: String,
    val members: List<String>,
    val value: String,
    val source: String,
)

data class LayoutJsonCoverage(
    val id: String,
    val kind: String,
    val source: String,
)

data class LayoutJsonDocument(
    val layoutId: String,
    val groups: Map<String, List<KeyPosition>>,
    val variants: List<LayoutJsonVariant>,
    val chords: List<LayoutJsonChord>,
    val coverage: List<LayoutJsonCoverage>,
    val feedback: Map<String, String>,
    val schemaVersion: Int = 1,
)

/** Deterministic, machine-readable export with no machine-local metadata. */
fun LayoutJsonDocument.render(): String = buildString {
    appendLine("{")
    field("schemaVersion", schemaVersion.toString(), quoted = false, trailing = true)
    field("layoutId", layoutId, trailing = true)
    append("  \"groups\": {")
    groups.toSortedMap().entries.forEachIndexed { index, (id, positions) ->
        appendLine(if (index == 0) "" else ",")
        append("    \"").append(escape(id)).append("\": [")
        append(positions.joinToString(", ") { "\"${escape(it.id)}\"" })
        append(']')
    }
    appendLine()
    appendLine("  },")
    array("variants", variants.sortedBy(LayoutJsonVariant::id)) { variant ->
        """{"id":"${escape(variant.id)}","bindings":[${variant.bindings.joinToString(",") { binding -> "{\"position\":\"${escape(binding.position.id)}\",\"value\":\"${escape(binding.value)}\"}" }}]}"""
    }
    appendLine(",")
    array("chords", chords.sortedBy(LayoutJsonChord::id)) { chord ->
        """{"id":"${escape(chord.id)}","members":[${chord.members.sorted().joinToString(",") { "\"${escape(it)}\"" }}],"value":"${escape(chord.value)}","source":"${escape(chord.source)}"}"""
    }
    appendLine(",")
    array("coverage", coverage.sortedBy(LayoutJsonCoverage::id)) { entry ->
        """{"id":"${escape(entry.id)}","kind":"${escape(entry.kind)}","source":"${escape(entry.source)}"}"""
    }
    appendLine(",")
    appendLine("  \"feedback\": {")
    feedback.toSortedMap().entries.forEachIndexed { index, (key, value) ->
        append("    \"").append(escape(key)).append("\": \"").append(escape(value)).append('"')
        if (index != feedback.size - 1) append(',')
        appendLine()
    }
    appendLine("  }")
    appendLine("}")
}

private fun StringBuilder.field(name: String, value: String, quoted: Boolean = true, trailing: Boolean = false) {
    append("  \"").append(name).append("\": ")
    if (quoted) append('"').append(escape(value)).append('"') else append(value)
    if (trailing) append(',')
    appendLine()
}

private fun <T> StringBuilder.array(name: String, values: List<T>, render: (T) -> String) {
    appendLine("  \"$name\": [")
    values.forEachIndexed { index, value ->
        append("    ").append(render(value))
        if (index != values.lastIndex) append(',')
        appendLine()
    }
    append("  ]")
}

private fun escape(value: String) = buildString {
    value.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(character)
        }
    }
}
