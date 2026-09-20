package dev.srsatt.keyboard.layout.model

import dev.srsatt.keyboard.layout.dsl.layout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class VariantsTest {
    private val letter = position("test.letter")
    private val punctuation = position("test.punctuation")
    private val profile = keyboardProfile("test", listOf(letter, punctuation))
    private val english = language("en")
    private val russian = language("ru")

    @Test
    fun `ASCII letters derive uppercase while nonletters remain unchanged`() {
        val declared = layout("derived") {
            language(english) {
                letter types symbol('a')
                punctuation types symbol('.')
            }
        }

        val resolved = declared.resolveBindings(profile, english, shifted = true)

        assertEquals("A", resolved.typeValue(letter))
        assertEquals(BindingOrigin.DERIVED_SHIFT, resolved.bindingAt(letter).origin)
        assertEquals(".", resolved.typeValue(punctuation))
        assertEquals(BindingOrigin.LANGUAGE, resolved.bindingAt(punctuation).origin)
    }

    @Test
    fun `German expansion and Russian shifted exception use explicit overrides`() {
        val declared = layout("overrides") {
            base {
                punctuation types symbol('.')
            }
            language(english) {
                letter types symbol('ß')
            }
            language(russian) {
                letter types symbol('ж')
            }
            shifted(english) {
                letter types text("SS")
            }
            shifted(russian) {
                letter types symbol('Ё')
            }
        }

        val german = declared.resolveBindings(profile, english, shifted = true).bindingAt(letter)
        val russianException = declared.resolveBindings(profile, russian, shifted = true).bindingAt(letter)

        assertEquals("SS", (assertIs<TypeIntent>(german.intent).value).value)
        assertIs<TextRef>((german.intent as TypeIntent).value)
        assertEquals(BindingOrigin.LANGUAGE_SHIFT_OVERRIDE, german.origin)
        assertEquals("Ё", (assertIs<TypeIntent>(russianException.intent).value).value)
        assertEquals(BindingOrigin.LANGUAGE_SHIFT_OVERRIDE, russianException.origin)
    }

    @Test
    fun `common Shift override applies before language-specific Shift override`() {
        val declared = layout("precedence") {
            base {
                punctuation types symbol('.')
            }
            language(english) {
                letter types symbol('a')
            }
            shifted {
                punctuation types symbol('+')
            }
            shifted(english) {
                punctuation types symbol('!')
            }
        }

        val resolved = declared.resolveBindings(profile, english, shifted = true)

        assertEquals("!", resolved.typeValue(punctuation))
        assertEquals(BindingOrigin.LANGUAGE_SHIFT_OVERRIDE, resolved.bindingAt(punctuation).origin)
    }

    @Test
    fun `undeclared effective Base position fails resolution`() {
        val declared = layout("incomplete") {
            language(english) {
                letter types symbol('a')
            }
        }

        val error = assertFailsWith<IllegalArgumentException> {
            declared.resolveBindings(profile, english)
        }

        assertTrue(error.message.orEmpty().contains("test.punctuation"))
        assertTrue(error.message.orEmpty().contains("undeclared effective Base"))
    }

    private fun List<EffectiveBinding>.bindingAt(position: KeyPosition) = single { it.position == position }

    private fun List<EffectiveBinding>.typeValue(position: KeyPosition): String =
        (assertIs<TypeIntent>(bindingAt(position).intent).value).value
}
