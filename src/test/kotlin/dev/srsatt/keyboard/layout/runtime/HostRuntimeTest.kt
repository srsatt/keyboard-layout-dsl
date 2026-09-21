package dev.srsatt.keyboard.layout.runtime

import dev.srsatt.keyboard.layout.model.Linux
import dev.srsatt.keyboard.layout.model.MacOS
import dev.srsatt.keyboard.layout.model.SwitchLanguage
import dev.srsatt.keyboard.layout.model.SyncLanguage
import dev.srsatt.keyboard.layout.model.language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HostRuntimeTest {
    private val en = language("en")
    private val ru = language("ru")
    private val mac = RuntimeHostProfile("mac", MacOS, HostSwitchMechanism("test.mac.switch-language"))
    private val linux = RuntimeHostProfile("linux", Linux, HostSwitchMechanism("test.linux.switch-language"))

    @Test
    fun `selected host profile supplies the next normal language switch request`() {
        val runtime = runtime()

        runtime.selectHost(linux.id)
        val transition = runtime.invoke(SwitchLanguage)

        assertEquals(HostRuntimeState(linux, ru), runtime.state)
        assertEquals(RuntimeInvocationPath.SEMANTIC, transition.path)
        assertEquals(
            listOf(
                HostRuntimeEvent.LanguageIntentChanged(en, ru),
                HostRuntimeEvent.HostSwitchRequested(linux.languageSwitch),
            ),
            transition.events,
        )
        assertEquals(1, transition.events.filterIsInstance<HostRuntimeEvent.HostSwitchRequested>().size)
    }

    @Test
    fun `manual language synchronization requests one host toggle without changing intent`() {
        val runtime = runtime()

        val transition = runtime.invoke(SyncLanguage)

        assertEquals(HostRuntimeState(mac, en), runtime.state)
        assertEquals(
            listOf(HostRuntimeEvent.HostSwitchRequested(mac.languageSwitch)),
            transition.events,
        )
    }

    @Test
    fun `raw events and supported semantic operations use distinct paths`() {
        val diagnostic = RuntimeOperation.Diagnostic("test.report-mode")
        val reset = RuntimeOperation.Device("test.reset")
        val runtime = runtime(setOf(diagnostic, reset))

        val raw = runtime.dispatchRaw(RawRuntimeEvent("test.legacy-keycode"))
        val report = runtime.invoke(diagnostic)
        val device = runtime.invoke(reset)

        assertEquals(RuntimeInvocationPath.RAW_EVENT, raw.path)
        assertEquals(
            listOf(HostRuntimeEvent.RawEventForwarded(RawRuntimeEvent("test.legacy-keycode"))),
            raw.events,
        )
        assertEquals(RuntimeInvocationPath.SEMANTIC, report.path)
        assertEquals(
            listOf(HostRuntimeEvent.DiagnosticReported(diagnostic, HostRuntimeState(mac, en))),
            report.events,
        )
        assertEquals(RuntimeInvocationPath.SEMANTIC, device.path)
        assertEquals(listOf(HostRuntimeEvent.DeviceOperationRequested(reset)), device.events)
        assertFailsWith<IllegalArgumentException> {
            runtime.invoke(RuntimeOperation.Device("test.unsupported"))
        }
    }

    private fun runtime(supported: Set<RuntimeOperation> = emptySet()) = HostRuntime(
        profiles = listOf(mac, linux),
        languages = listOf(en, ru),
        initialProfile = mac,
        initialLanguage = en,
        supportedOperations = supported,
    )
}
