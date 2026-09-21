package dev.srsatt.keyboard.layout.qmk

import dev.srsatt.keyboard.layout.model.KeyboardProfile
import dev.srsatt.keyboard.layout.model.KeyPosition
import dev.srsatt.keyboard.layout.model.PhysicalGroup

data class QmkLayoutArgument(
    val parameter: String,
    val position: KeyPosition,
)

data class QmkLayoutMacroBinding(
    val profileId: String,
    val header: String,
    val macro: String,
    val arguments: List<QmkLayoutArgument>,
)

fun qmkLayoutMacroBinding(
    profile: KeyboardProfile,
    header: String,
    macro: String,
    signature: List<String>,
    arguments: List<PhysicalGroup>,
): QmkLayoutMacroBinding {
    require(header.isNotBlank()) { "A QMK layout header must not be blank" }
    require(macro.isNotBlank()) { "A QMK layout macro must not be blank" }
    require(signature.none(String::isBlank)) { "QMK layout parameter names must not be blank" }

    val duplicateParameters = signature.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
    require(duplicateParameters.isEmpty()) {
        "QMK layout macro '$macro' repeats parameters: ${duplicateParameters.sorted().joinToString()}"
    }

    val positions = arguments.flatMap(PhysicalGroup::positions)
    require(signature.size == positions.size) {
        "QMK layout macro '$macro' has ${signature.size} parameters but ${positions.size} positions were supplied"
    }

    val duplicatePositions = positions.groupingBy(KeyPosition::id).eachCount().filterValues { it > 1 }.keys
    require(duplicatePositions.isEmpty()) {
        "QMK layout macro '$macro' repeats physical positions: ${duplicatePositions.sorted().joinToString()}"
    }

    val expected = profile.positions.map(KeyPosition::id).toSet()
    val actual = positions.map(KeyPosition::id).toSet()
    val missing = expected - actual
    val unknown = actual - expected
    require(missing.isEmpty() && unknown.isEmpty()) {
        buildString {
            append("QMK layout macro '").append(macro).append("' does not cover profile '").append(profile.id).append("'")
            if (missing.isNotEmpty()) append("; missing: ").append(missing.sorted().joinToString())
            if (unknown.isNotEmpty()) append("; unknown: ").append(unknown.sorted().joinToString())
        }
    }

    return QmkLayoutMacroBinding(
        profileId = profile.id,
        header = header,
        macro = macro,
        arguments = signature.zip(positions) { parameter, position -> QmkLayoutArgument(parameter, position) },
    )
}

data class MatrixAddress(val row: Int, val column: Int)

data class LedAddress(val index: Int)

sealed interface DeviceAddressMap<out Address> {
    data class Available<Address>(
        val source: String,
        val addresses: Map<KeyPosition, Address>,
    ) : DeviceAddressMap<Address>

    data class Unavailable(val reason: String) : DeviceAddressMap<Nothing> {
        init {
            require(reason.isNotBlank()) { "An unavailable address map must explain the missing evidence" }
        }
    }
}
