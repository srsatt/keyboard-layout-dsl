package dev.srsatt.keyboard.layout.model

/** An unordered chord trigger, stored in stable physical-ID order. */
class ChordKeys private constructor(
    val positions: List<KeyPosition>,
) {
    override fun equals(other: Any?) = other is ChordKeys && positions == other.positions

    override fun hashCode() = positions.hashCode()

    override fun toString() = positions.joinToString(prefix = "[", postfix = "]", transform = KeyPosition::id)

    internal companion object {
        fun from(positions: List<KeyPosition>): ChordKeys {
            val duplicateIds = positions.groupBy(KeyPosition::id).filterValues { it.size > 1 }.keys.sorted()
            require(duplicateIds.isEmpty()) {
                "Chord repeats physical position aliases: ${duplicateIds.joinToString()}"
            }
            require(positions.size >= 2) { "A chord must contain at least two physical positions" }
            return ChordKeys(positions.sortedBy(KeyPosition::id))
        }
    }
}

data class SourceProvenance(
    val file: String,
    val line: Int,
) {
    init {
        require(file.isNotBlank()) { "Source file must not be blank" }
        require(line > 0) { "Source line must be positive" }
    }
}

data class DeclaredChord(
    val keys: ChordKeys,
    val intent: BindingIntent,
    val source: SourceProvenance,
)
