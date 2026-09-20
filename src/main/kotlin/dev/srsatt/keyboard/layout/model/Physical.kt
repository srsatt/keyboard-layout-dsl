package dev.srsatt.keyboard.layout.model

data class KeyPosition(val id: String) : PhysicalGroup {
    init {
        require(id.isNotBlank()) { "A physical position ID must not be blank" }
    }

    override val positions: List<KeyPosition> = listOf(this)
}

interface PhysicalGroup {
    val positions: List<KeyPosition>
}

interface KeyboardProfile : PhysicalGroup {
    val id: String
}

fun position(id: String) = KeyPosition(id)

fun keyboardProfile(id: String, positions: List<KeyPosition>): KeyboardProfile {
    require(id.isNotBlank()) { "A keyboard profile ID must not be blank" }
    require(positions.isNotEmpty()) { "A keyboard profile must contain at least one position" }
    val duplicateIds = positions.groupBy(KeyPosition::id).filterValues { it.size > 1 }.keys
    require(duplicateIds.isEmpty()) {
        "Profile '$id' repeats physical position IDs: ${duplicateIds.sorted().joinToString()}"
    }
    return object : KeyboardProfile {
        override val id = id
        override val positions = positions.toList()
    }
}
