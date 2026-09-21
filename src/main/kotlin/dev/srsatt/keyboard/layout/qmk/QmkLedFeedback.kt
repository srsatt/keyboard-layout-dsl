package dev.srsatt.keyboard.layout.qmk

import dev.srsatt.keyboard.layout.model.KeyPosition

data class HsvColor(val hue: Int, val saturation: Int, val value: Int) {
    init {
        require(hue in 0..255 && saturation in 0..255 && value in 0..255) {
            "HSV components must be in 0..255"
        }
    }
}

data class StatusLedAddress(val index: Int) {
    init {
        require(index >= 0) { "Status LED index must not be negative" }
    }
}

sealed interface DeviceStatusLedMap {
    data class Available(
        val source: String,
        val addresses: Set<StatusLedAddress>,
    ) : DeviceStatusLedMap {
        init {
            require(source.isNotBlank()) { "A status LED map source must not be blank" }
            require(addresses.isNotEmpty()) { "An available status LED map must not be empty" }
        }
    }

    data class Unavailable(val reason: String) : DeviceStatusLedMap {
        init {
            require(reason.isNotBlank()) { "An unavailable status LED map must explain the missing capability" }
        }
    }
}

data class QmkLedLayerState(
    val id: String,
    val qmkLayer: Int,
    val color: HsvColor,
    val usefulPositions: Set<KeyPosition>,
) {
    init {
        require(id.isNotBlank()) { "LED layer state ID must not be blank" }
        require(qmkLayer in 0..31) { "QMK LED layer '$id' must be in 0..31" }
    }
}

data class QmkLedCombinedState(
    val id: String,
    val qmkLayers: Set<Int>,
    val color: HsvColor,
    val usefulPositions: Set<KeyPosition>,
) {
    init {
        require(id.isNotBlank()) { "Combined LED state ID must not be blank" }
        require(qmkLayers.size >= 2) { "Combined LED state '$id' requires at least two layers" }
    }
}

data class QmkStatusLedState(
    val id: String,
    val address: StatusLedAddress,
) {
    init {
        require(id.isNotBlank()) { "Status LED state ID must not be blank" }
    }
}

data class QmkLedFeedbackPlan(
    val matrixAddresses: Map<KeyPosition, MatrixAddress>,
    val physicalAddresses: Map<KeyPosition, LedAddress>,
    val layers: List<QmkLedLayerState>,
    val combinedStates: List<QmkLedCombinedState>,
    val statusStates: List<QmkStatusLedState>,
)

enum class UnsupportedLedFallback {
    ERROR,
    OMIT_SEMANTIC_FEEDBACK,
}

fun qmkLedFeedbackPlan(
    matrix: DeviceAddressMap<MatrixAddress>,
    physicalLeds: DeviceAddressMap<LedAddress>,
    statusLeds: DeviceStatusLedMap,
    layers: List<QmkLedLayerState>,
    combinedStates: List<QmkLedCombinedState>,
    statusStates: List<QmkStatusLedState>,
    unsupportedFallback: UnsupportedLedFallback = UnsupportedLedFallback.ERROR,
): QmkLedFeedbackPlan? {
    val matrixAddresses = matrix as? DeviceAddressMap.Available
        ?: return unsupported("Physical-key matrix mapping", (matrix as DeviceAddressMap.Unavailable).reason, unsupportedFallback)
    val physical = physicalLeds as? DeviceAddressMap.Available
        ?: return unsupported("Physical-key LED feedback", (physicalLeds as DeviceAddressMap.Unavailable).reason, unsupportedFallback)
    val status = statusLeds as? DeviceStatusLedMap.Available
        ?: return unsupported("Status LED feedback", (statusLeds as DeviceStatusLedMap.Unavailable).reason, unsupportedFallback)

    require(layers.distinctBy(QmkLedLayerState::id).size == layers.size) { "LED layer state IDs must be unique" }
    require(layers.distinctBy(QmkLedLayerState::qmkLayer).size == layers.size) { "QMK LED layer numbers must be unique" }
    require(combinedStates.distinctBy(QmkLedCombinedState::id).size == combinedStates.size) {
        "Combined LED state IDs must be unique"
    }
    require(statusStates.distinctBy(QmkStatusLedState::id).size == statusStates.size) {
        "Status LED state IDs must be unique"
    }
    require(statusStates.distinctBy(QmkStatusLedState::address).size == statusStates.size) {
        "Status LED states must map to distinct device LEDs"
    }

    val knownLayers = layers.map(QmkLedLayerState::qmkLayer).toSet()
    combinedStates.forEach { state ->
        require(state.qmkLayers.all(knownLayers::contains)) {
            "Combined LED state '${state.id}' references unknown QMK layers ${state.qmkLayers - knownLayers}"
        }
    }
    val mappedPositions = physical.addresses.keys
    val usedPositions = layers.flatMap(QmkLedLayerState::usefulPositions) +
        combinedStates.flatMap(QmkLedCombinedState::usefulPositions)
    require(usedPositions.all(mappedPositions::contains)) {
        "LED feedback references positions without device LEDs: " +
            usedPositions.filterNot(mappedPositions::contains).map(KeyPosition::id).distinct().sorted().joinToString()
    }
    require(statusStates.all { it.address in status.addresses }) {
        "Status LED feedback references unsupported device LEDs: " +
            statusStates.filterNot { it.address in status.addresses }.map { it.address.index }.distinct().sorted().joinToString()
    }

    require(matrixAddresses.addresses.keys == physical.addresses.keys) {
        "Matrix and LED maps must cover the same physical positions"
    }

    return QmkLedFeedbackPlan(matrixAddresses.addresses, physical.addresses, layers, combinedStates, statusStates)
}

private fun unsupported(
    capability: String,
    reason: String,
    fallback: UnsupportedLedFallback,
): Nothing? = when (fallback) {
    UnsupportedLedFallback.ERROR -> error("$capability is unsupported: $reason")
    UnsupportedLedFallback.OMIT_SEMANTIC_FEEDBACK -> null
}
