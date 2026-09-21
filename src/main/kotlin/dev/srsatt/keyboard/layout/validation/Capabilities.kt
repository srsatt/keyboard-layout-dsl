package dev.srsatt.keyboard.layout.validation

import dev.srsatt.keyboard.layout.model.ActionRef
import dev.srsatt.keyboard.layout.model.ActionReference
import dev.srsatt.keyboard.layout.model.EncodingFallbackPolicy
import dev.srsatt.keyboard.layout.model.HostActionRef
import dev.srsatt.keyboard.layout.model.HostPlatform
import dev.srsatt.keyboard.layout.model.InputSource
import dev.srsatt.keyboard.layout.model.KeyPosition
import dev.srsatt.keyboard.layout.model.KeystrokeActionRef
import dev.srsatt.keyboard.layout.model.Layout
import dev.srsatt.keyboard.layout.model.SymbolCatalog
import dev.srsatt.keyboard.layout.model.SymbolRef
import dev.srsatt.keyboard.layout.model.resolveImplementation
import dev.srsatt.keyboard.layout.model.validateActionCycles

data class TargetCapacity(
    val maxChordMembers: Int,
    val maxChordTokens: Int,
) {
    init {
        require(maxChordMembers > 0 && maxChordTokens >= 0) { "Target capacities must be positive" }
    }
}

data class BackendMapping(
    val requiredPositions: List<KeyPosition>,
    val mappedPositions: List<KeyPosition>,
)

data class ValidationRequirements(
    val layout: Layout,
    val declaredActions: List<ActionReference> = emptyList(),
    val reachableActions: List<ActionReference> = emptyList(),
    val internalActionIds: Set<String> = emptySet(),
    val reachableSymbols: Set<SymbolRef> = emptySet(),
    val symbolCatalog: SymbolCatalog? = null,
    val host: HostPlatform,
    val inputSource: InputSource,
    val fallback: EncodingFallbackPolicy = EncodingFallbackPolicy.Fail,
    val hostSignals: Map<String, String> = emptyMap(),
    val reservedSignals: Set<String> = emptySet(),
    val backendMapping: BackendMapping? = null,
    val capacity: TargetCapacity,
)

/** Validates references and explicit target constraints after the full model is assembled. */
fun validateRequirements(requirements: ValidationRequirements) {
    validateLayout(requirements.layout)
    validateActionIds(requirements)
    validateActions(requirements)
    validateSymbols(requirements)
    validateSignals(requirements)
    requirements.backendMapping?.let(::validateBackendMapping)
    validateCapacity(requirements.layout, requirements.capacity)
}

private fun validateActionIds(requirements: ValidationRequirements) {
    val duplicates = requirements.declaredActions.groupBy(ActionReference::id).filterValues { it.size > 1 }.keys.sorted()
    require(duplicates.isEmpty()) { "Duplicate action IDs: ${duplicates.joinToString()}" }
    val declared = requirements.declaredActions.associateBy(ActionReference::id)
    requirements.reachableActions.forEach { action ->
        require(declared[action.id]?.javaClass == action.javaClass) { "Missing action reference '${action.id}'" }
    }
}

private fun validateActions(requirements: ValidationRequirements) {
    requirements.reachableActions.forEach { action ->
        if (action.id in requirements.internalActionIds) return@forEach
        when (action) {
            is ActionRef -> action.resolveImplementation(requirements.host)
            is KeystrokeActionRef -> action.resolveImplementation(requirements.host)
            is HostActionRef -> require(action.id in requirements.hostSignals) {
                "Host action '${action.id}' has no signal binding"
            }
        }
    }
    val general = requirements.declaredActions.filterIsInstance<ActionRef>()
        .filterNot { it.id in requirements.internalActionIds }
    if (general.isNotEmpty()) validateActionCycles(general, requirements.host)
}

private fun validateSymbols(requirements: ValidationRequirements) {
    if (requirements.reachableSymbols.isEmpty()) return
    requireNotNull(requirements.symbolCatalog) { "Reachable symbols have no symbol catalog" }
        .validateCoverage(requirements.reachableSymbols, requirements.host, requirements.inputSource, requirements.fallback)
}

private fun validateSignals(requirements: ValidationRequirements) {
    requirements.hostSignals.forEach { (action, signal) ->
        require(signal !in requirements.reservedSignals) {
            "Host action '$action' uses reserved signal '$signal'"
        }
    }
}

private fun validateBackendMapping(mapping: BackendMapping) {
    val duplicate = mapping.mappedPositions.groupBy(KeyPosition::id).filterValues { it.size > 1 }.keys.sorted()
    require(duplicate.isEmpty()) { "Backend mapping duplicates positions: ${duplicate.joinToString()}" }
    val missing = mapping.requiredPositions.toSet() - mapping.mappedPositions.toSet()
    val unexpected = mapping.mappedPositions.toSet() - mapping.requiredPositions.toSet()
    require(missing.isEmpty() && unexpected.isEmpty()) {
        "Backend mapping mismatch: missing=${missing.map(KeyPosition::id).sorted()}, unexpected=${unexpected.map(KeyPosition::id).sorted()}"
    }
}

private fun validateCapacity(layout: Layout, capacity: TargetCapacity) {
    val chords = layout.layers.flatMap { it.chords }
    val members = chords.maxOfOrNull { it.keys.positions.size } ?: 0
    require(members <= capacity.maxChordMembers) {
        "Chord member limit ${capacity.maxChordMembers} exceeded by $members"
    }
    require(chords.size <= capacity.maxChordTokens) {
        "Chord token limit ${capacity.maxChordTokens} exceeded by ${chords.size}"
    }
}
