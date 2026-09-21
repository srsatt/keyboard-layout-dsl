package dev.srsatt.keyboard.layout.runtime

import dev.srsatt.keyboard.layout.model.language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TemporaryLanguageRuntimeTest {
    private val en = language("en")
    private val ru = language("ru")

    @Test
    fun `English identifier inside Russian restores after committed space`() {
        val runtime = runtime()

        assertEquals(LanguageSwitch(ru, en), runtime.enter())
        assertTrue(runtime.isActive)
        "some_variable-2".forEach { character ->
            assertNull(runtime.afterCommitted(CommittedOutput.Text(character.toString())))
            assertEquals(en, runtime.currentLanguage)
        }

        assertEquals(LanguageSwitch(en, ru), runtime.afterCommitted(CommittedOutput.Text(" ")))
        assertEquals(ru, runtime.currentLanguage)
        assertFalse(runtime.isActive)
    }

    @Test
    fun `punctuation brackets Enter and Tab restore only after they are committed`() {
        val separators = listOf(
            CommittedOutput.Text("."),
            CommittedOutput.Text(","),
            CommittedOutput.Text("("),
            CommittedOutput.Text(")"),
            CommittedOutput.Text("["),
            CommittedOutput.Text("]"),
            CommittedOutput.Enter,
            CommittedOutput.Tab,
        )

        separators.forEach { separator ->
            val runtime = runtime()
            runtime.enter()
            assertEquals(en, runtime.currentLanguage)

            assertEquals(LanguageSwitch(en, ru), runtime.afterCommitted(separator), separator.toString())
            assertEquals(ru, runtime.currentLanguage, separator.toString())
            assertFalse(runtime.isActive, separator.toString())
        }
    }

    @Test
    fun `letters digits hyphen and underscore continue the temporary word`() {
        val runtime = runtime()
        runtime.enter()

        listOf("E", "ж", "0", "9", "-", "_").forEach { character ->
            assertNull(runtime.afterCommitted(CommittedOutput.Text(character)), character)
        }

        assertEquals(en, runtime.currentLanguage)
        assertTrue(runtime.isActive)
    }

    @Test
    fun `restores at the punctuation character boundary`() {
        val runtime = runtime()
        runtime.enter()

        assertFailsWith<IllegalArgumentException> { CommittedOutput.Text("x.y") }
        assertNull(runtime.afterCommitted(CommittedOutput.Text("x")))
        assertNull(runtime.afterCommitted(CommittedOutput.Text("_")))
        assertEquals(en, runtime.currentLanguage)

        assertEquals(LanguageSwitch(en, ru), runtime.afterCommitted(CommittedOutput.Text(".")))
        assertEquals(ru, runtime.currentLanguage)
        assertFalse(runtime.isActive)
    }

    private fun runtime() = TemporaryLanguageRuntime(en, ru, ru)
}
