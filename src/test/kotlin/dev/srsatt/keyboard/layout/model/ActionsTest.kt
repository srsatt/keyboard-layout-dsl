package dev.srsatt.keyboard.layout.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ActionsTest {
    @Test
    fun `semantic built-ins have stable IDs and performable typing`() {
        assertEquals(
            listOf(
                "language.switch",
                "language.sync",
                "language.temporary",
                "sound.mute",
                "sound.unmute",
                "sound.toggle",
            ),
            listOf(SwitchLanguage, SyncLanguage, TemporaryLanguage, MuteSounds, UnmuteSounds, ToggleSounds)
                .map(ActionReference::id),
        )
        assertTrue(Performable::class.java.isAssignableFrom(ActionRef::class.java))
        assertFalse(Sendable::class.java.isAssignableFrom(ActionRef::class.java))
        assertFalse(Holdable::class.java.isAssignableFrom(ActionRef::class.java))
    }

    @Test
    fun `action kinds retain stable IDs and lifecycle typing`() {
        val workmux = action("workmux.agents") {
            implementationIn(MacOS) { tap(Cmd + Key.E) }
        }
        val paste = keystrokeAction("terminal.paste") {
            implementationIn(MacOS) { Ctrl + Key.V }
        }
        val voice = hostAction("voice.openwhispr")

        assertEquals("workmux.agents", workmux.id)
        assertEquals("terminal.paste", paste.id)
        assertEquals("voice.openwhispr", voice.id)
        assertFalse(Sendable::class.java.isAssignableFrom(ActionRef::class.java))
        assertFalse(Holdable::class.java.isAssignableFrom(ActionRef::class.java))
        assertIs<Sendable>(paste)
        assertFalse(Holdable::class.java.isAssignableFrom(KeystrokeActionRef::class.java))
        assertIs<Performable>(voice)
        assertEquals(ActionDelivery.ONCE, workmux.resolveImplementation(MacOS).delivery)
    }

    @Test
    fun `DeleteWord is a paired keystroke action`() {
        val implementation = DeleteWord.resolveImplementation(MacOS)

        assertEquals("edit.delete-word", DeleteWord.id)
        assertEquals(ActionDelivery.PAIRED, implementation.delivery)
        assertEquals(Stroke(Backspace, setOf(Alt)), implementation.stroke)
    }

    @Test
    fun `consumer implementation overrides action default`() {
        val paste = shortcutAction("terminal.paste", Ctrl + Key.V)
        val overrides = actions {
            paste {
                implementationIn(MacOS) { Cmd + Key.V }
            }
        }

        assertEquals(Stroke(Key.V, setOf(Ctrl)), paste.resolveImplementation(Linux).stroke)
        assertEquals(Stroke(Key.V, setOf(Cmd)), paste.resolveImplementation(MacOS, overrides).stroke)
    }

    @Test
    fun `resolution rejects duplicate selected and absent implementations`() {
        val paste = shortcutAction("terminal.paste", Ctrl + Key.V)
        val duplicates = actions {
            paste {
                implementationIn(MacOS) { Cmd + Key.V }
                implementationIn(MacOS) { Ctrl + Key.V }
            }
        }
        val missing = action("missing") {
            implementationIn(MacOS) { tap(Key.E.asStroke()) }
        }

        val duplicateError = assertFailsWith<IllegalArgumentException> {
            paste.resolveImplementation(MacOS, duplicates)
        }
        val missingError = assertFailsWith<IllegalArgumentException> {
            missing.resolveImplementation(Linux)
        }

        assertTrue(duplicateError.message.orEmpty().contains("duplicate selected"))
        assertTrue(missingError.message.orEmpty().contains("no implementation"))
    }
}

private fun Key.asStroke() = Stroke(this)
