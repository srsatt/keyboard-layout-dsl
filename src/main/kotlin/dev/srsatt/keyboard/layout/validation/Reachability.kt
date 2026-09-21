package dev.srsatt.keyboard.layout.validation

import dev.srsatt.keyboard.layout.model.DisabledIntent
import dev.srsatt.keyboard.layout.model.KeyboardProfile
import dev.srsatt.keyboard.layout.model.KeyPosition
import dev.srsatt.keyboard.layout.model.LayerContext
import dev.srsatt.keyboard.layout.model.Layout
import dev.srsatt.keyboard.layout.model.OverlayDefault
import dev.srsatt.keyboard.layout.model.TransparentIntent

enum class ReachabilityOutcome {
    REACHED,
    UNUSED,
    INTENTIONAL_EMPTY,
    OPAQUE_LEGACY_UNKNOWN,
}

sealed interface ReachabilityDiagnostic {
    val outcome: ReachabilityOutcome
}

data class PositionReachabilityDiagnostic(
    val position: KeyPosition,
    override val outcome: ReachabilityOutcome,
    val contexts: List<LayerContext>,
) : ReachabilityDiagnostic {
    init {
        require(outcome != ReachabilityOutcome.OPAQUE_LEGACY_UNKNOWN) {
            "Opaque legacy diagnostics do not identify a modeled position"
        }
    }
}

data class OpaqueLegacyReachabilityDiagnostic(
    val effectId: String,
) : ReachabilityDiagnostic {
    init {
        require(effectId.isNotBlank()) { "Opaque legacy effect ID must not be blank" }
    }

    override val outcome = ReachabilityOutcome.OPAQUE_LEGACY_UNKNOWN
}

data class ReachabilityReport(
    val diagnostics: List<ReachabilityDiagnostic>,
) {
    val modeledReachabilityComplete: Boolean
        get() = diagnostics.none { it.outcome == ReachabilityOutcome.UNUSED }

    val wholeFirmwareReachabilityProven: Boolean
        get() = modeledReachabilityComplete &&
            diagnostics.none { it.outcome == ReachabilityOutcome.OPAQUE_LEGACY_UNKNOWN }
}

/** Reports only what the declarative model can prove; opaque C remains explicitly unknown. */
fun diagnoseReachability(
    layout: Layout,
    profile: KeyboardProfile,
    opaqueLegacyEffects: Collection<String>,
): ReachabilityReport {
    val profilePositions = profile.positions.toSet()
    val declaredPositions = layout.layers.flatMap { layer ->
        layer.bindings.map { it.position } + layer.chords.flatMap { it.keys.positions }
    }.toSet()
    val outsideProfile = declaredPositions - profilePositions
    require(outsideProfile.isEmpty()) {
        "Layout '${layout.id}' references positions outside profile '${profile.id}': " +
            outsideProfile.map(KeyPosition::id).sorted().joinToString()
    }

    val reached = profile.positions.associateWith { mutableListOf<LayerContext>() }
    val intentionallyEmpty = profile.positions.associateWith { mutableListOf<LayerContext>() }

    layout.layers.forEach { layer ->
        layer.bindings.forEach { binding ->
            when (binding.intent) {
                DisabledIntent -> intentionallyEmpty.getValue(binding.position).addDistinct(layer.context)
                TransparentIntent -> Unit
                else -> reached.getValue(binding.position).addDistinct(layer.context)
            }
        }
        layer.chords.flatMap { it.keys.positions }.forEach { position ->
            reached.getValue(position).addDistinct(layer.context)
        }
        if (layer.default == OverlayDefault.DISABLED) {
            val explicit = layer.bindings.mapTo(mutableSetOf()) { it.position }
            profile.positions.filterNot(explicit::contains).forEach { position ->
                intentionallyEmpty.getValue(position).addDistinct(layer.context)
            }
        }
    }

    val diagnostics = buildList {
        profile.positions.forEach { position ->
            val reachedContexts = reached.getValue(position)
            val emptyContexts = intentionallyEmpty.getValue(position)
            if (reachedContexts.isNotEmpty()) {
                add(PositionReachabilityDiagnostic(position, ReachabilityOutcome.REACHED, reachedContexts))
            }
            if (emptyContexts.isNotEmpty()) {
                add(PositionReachabilityDiagnostic(position, ReachabilityOutcome.INTENTIONAL_EMPTY, emptyContexts))
            }
            if (reachedContexts.isEmpty() && emptyContexts.isEmpty()) {
                add(PositionReachabilityDiagnostic(position, ReachabilityOutcome.UNUSED, emptyList()))
            }
        }
        opaqueLegacyEffects.distinct().sorted().forEach { add(OpaqueLegacyReachabilityDiagnostic(it)) }
    }
    return ReachabilityReport(diagnostics)
}

private fun MutableList<LayerContext>.addDistinct(context: LayerContext) {
    if (context !in this) add(context)
}
