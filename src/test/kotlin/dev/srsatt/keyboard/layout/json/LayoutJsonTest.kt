package dev.srsatt.keyboard.layout.json

import dev.srsatt.keyboard.layout.model.position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LayoutJsonTest {
    private val a = position("test.a")
    private val b = position("test.b")

    @Test
    fun `JSON export is stable ordered and has no machine-local churn`() {
        val document = LayoutJsonDocument(
            layoutId = "test",
            groups = linkedMapOf("right" to listOf(b), "left" to listOf(a)),
            variants = listOf(LayoutJsonVariant("RU", listOf(LayoutJsonBinding(a, "RU_A"))), LayoutJsonVariant("EN", listOf(LayoutJsonBinding(a, "EN_A")))),
            chords = listOf(LayoutJsonChord("z", listOf("b", "a"), "ACTION", "relative.kt:1")),
            coverage = listOf(LayoutJsonCoverage("legacy", "opaque", "legacy.c"), LayoutJsonCoverage("base", "native", "layout.kt:1")),
            feedback = mapOf("action" to "pending", "typing" to "off"),
        )

        val first = document.render()
        assertEquals(first, document.render())
        assertTrue("\"schemaVersion\": 1" in first)
        assertTrue(first.indexOf("\"EN\"") < first.indexOf("\"RU\""))
        assertTrue("\"kind\":\"native\"" in first)
        assertTrue("\"kind\":\"opaque\"" in first)
        assertFalse("/Users/" in first)
    }
}
