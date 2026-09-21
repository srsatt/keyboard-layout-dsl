package dev.srsatt.keyboard.layout.qmk

import dev.srsatt.keyboard.layout.model.PhysicalGroup
import dev.srsatt.keyboard.layout.model.keyboardProfile
import dev.srsatt.keyboard.layout.model.position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class QmkDeviceBindingTest {
    private val first = position("sample.first")
    private val second = position("sample.second")
    private val third = position("sample.third")
    private val profile = keyboardProfile("sample", listOf(first, second, third))

    @Test
    fun `ordered groups flatten into the declared macro signature`() {
        val pair = object : PhysicalGroup {
            override val positions = listOf(first, second)
        }

        val binding = qmkLayoutMacroBinding(
            profile = profile,
            header = "sample.h",
            macro = "LAYOUT_sample",
            signature = listOf("k00", "k01", "k10"),
            arguments = listOf(pair, third),
        )

        assertEquals(
            listOf("k00" to "sample.first", "k01" to "sample.second", "k10" to "sample.third"),
            binding.arguments.map { it.parameter to it.position.id },
        )
    }

    @Test
    fun `missing duplicated and arity-mismatched mappings fail`() {
        val duplicate = assertFailsWith<IllegalArgumentException> {
            qmkLayoutMacroBinding(profile, "sample.h", "LAYOUT_sample", listOf("a", "b", "c"), listOf(first, first, third))
        }
        assertTrue(duplicate.message.orEmpty().contains("repeats physical positions"))

        val missing = assertFailsWith<IllegalArgumentException> {
            qmkLayoutMacroBinding(profile, "sample.h", "LAYOUT_sample", listOf("a", "b", "c"), listOf(first, second, position("other")))
        }
        assertTrue(missing.message.orEmpty().contains("missing: sample.third"))
        assertTrue(missing.message.orEmpty().contains("unknown: other"))

        val arity = assertFailsWith<IllegalArgumentException> {
            qmkLayoutMacroBinding(profile, "sample.h", "LAYOUT_sample", listOf("a", "b"), listOf(first, second, third))
        }
        assertTrue(arity.message.orEmpty().contains("2 parameters but 3 positions"))
    }
}
