package dev.srsatt.keyboard.layout.runtime

import dev.srsatt.keyboard.layout.model.ActionReference
import dev.srsatt.keyboard.layout.model.HostPlatform
import dev.srsatt.keyboard.layout.model.LanguageRef
import dev.srsatt.keyboard.layout.model.SwitchLanguage
import dev.srsatt.keyboard.layout.model.SyncLanguage

data class HostSwitchMechanism(val id: String) {
    init {
        require(id.isNotBlank()) { "Host switch mechanism ID must not be blank" }
    }
}

data class RuntimeHostProfile(
    val id: String,
    val platform: HostPlatform,
    val languageSwitch: HostSwitchMechanism,
) {
    init {
        require(id.isNotBlank()) { "Runtime host profile ID must not be blank" }
    }
}

data class RawRuntimeEvent(val id: String) {
    init {
        require(id.isNotBlank()) { "Raw runtime event ID must not be blank" }
    }
}

sealed interface RuntimeOperation {
    val id: String

    data class Diagnostic(override val id: String) : RuntimeOperation {
        init {
            require(id.isNotBlank()) { "Diagnostic operation ID must not be blank" }
        }
    }

    data class Device(override val id: String) : RuntimeOperation {
        init {
            require(id.isNotBlank()) { "Device operation ID must not be blank" }
        }
    }
}

enum class RuntimeInvocationPath {
    RAW_EVENT,
    SEMANTIC,
}

data class HostRuntimeState(
    val profile: RuntimeHostProfile,
    val language: LanguageRef,
)

sealed interface HostRuntimeEvent {
    data class HostProfileSelected(
        val previous: RuntimeHostProfile,
        val selected: RuntimeHostProfile,
    ) : HostRuntimeEvent

    data class LanguageIntentChanged(
        val previous: LanguageRef,
        val selected: LanguageRef,
    ) : HostRuntimeEvent

    data class HostSwitchRequested(val mechanism: HostSwitchMechanism) : HostRuntimeEvent
    data class RawEventForwarded(val event: RawRuntimeEvent) : HostRuntimeEvent
    data class DiagnosticReported(
        val operation: RuntimeOperation.Diagnostic,
        val state: HostRuntimeState,
    ) : HostRuntimeEvent

    data class DeviceOperationRequested(val operation: RuntimeOperation.Device) : HostRuntimeEvent
}

data class HostRuntimeTransition(
    val path: RuntimeInvocationPath,
    val events: List<HostRuntimeEvent>,
)

/** Runtime-owned host and language intent; emitted switch requests do not imply host confirmation. */
class HostRuntime(
    profiles: Collection<RuntimeHostProfile>,
    languages: List<LanguageRef>,
    initialProfile: RuntimeHostProfile,
    initialLanguage: LanguageRef,
    private val supportedOperations: Set<RuntimeOperation> = emptySet(),
) {
    private val profilesById = profiles.associateBy(RuntimeHostProfile::id)
    private val languages = languages.toList()

    var state = HostRuntimeState(initialProfile, initialLanguage)
        private set

    init {
        require(profilesById.size == profiles.size) { "Runtime host profile IDs must be unique" }
        require(profilesById[initialProfile.id] == initialProfile) {
            "Initial runtime host profile '${initialProfile.id}' is not configured"
        }
        require(this.languages.size >= 2 && this.languages.distinct().size == this.languages.size) {
            "Runtime languages must contain at least two unique languages"
        }
        require(initialLanguage in this.languages) {
            "Initial runtime language '${initialLanguage.id}' is not configured"
        }
    }

    fun selectHost(profileId: String): HostRuntimeTransition {
        val selected = requireNotNull(profilesById[profileId]) {
            "Runtime host profile '$profileId' is not configured"
        }
        val previous = state.profile
        state = state.copy(profile = selected)
        return semantic(HostRuntimeEvent.HostProfileSelected(previous, selected))
    }

    fun invoke(action: ActionReference): HostRuntimeTransition = when (action.id) {
        SwitchLanguage.id -> switchLanguage()
        SyncLanguage.id -> syncLanguage()
        else -> throw IllegalArgumentException("Action '${action.id}' is not handled by HostRuntime")
    }

    fun invoke(operation: RuntimeOperation): HostRuntimeTransition {
        require(operation in supportedOperations) { "Runtime operation '${operation.id}' is not supported" }
        return when (operation) {
            is RuntimeOperation.Diagnostic -> semantic(HostRuntimeEvent.DiagnosticReported(operation, state))
            is RuntimeOperation.Device -> semantic(HostRuntimeEvent.DeviceOperationRequested(operation))
        }
    }

    fun dispatchRaw(event: RawRuntimeEvent) = HostRuntimeTransition(
        RuntimeInvocationPath.RAW_EVENT,
        listOf(HostRuntimeEvent.RawEventForwarded(event)),
    )

    private fun switchLanguage(): HostRuntimeTransition {
        val previous = state.language
        val selected = languages[(languages.indexOf(previous) + 1) % languages.size]
        state = state.copy(language = selected)
        return semantic(
            HostRuntimeEvent.LanguageIntentChanged(previous, selected),
            HostRuntimeEvent.HostSwitchRequested(state.profile.languageSwitch),
        )
    }

    private fun syncLanguage() = semantic(HostRuntimeEvent.HostSwitchRequested(state.profile.languageSwitch))

    private fun semantic(vararg events: HostRuntimeEvent) =
        HostRuntimeTransition(RuntimeInvocationPath.SEMANTIC, events.toList())
}
