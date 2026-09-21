package dev.srsatt.keyboard.layout.validation

import dev.srsatt.keyboard.layout.dsl.LayoutScope
import dev.srsatt.keyboard.layout.dsl.LayerScope
import dev.srsatt.keyboard.layout.dsl.Below
import dev.srsatt.keyboard.layout.dsl.layout
import dev.srsatt.keyboard.layout.model.CommandRef
import dev.srsatt.keyboard.layout.model.command
import dev.srsatt.keyboard.layout.model.language
import dev.srsatt.keyboard.layout.model.layer
import dev.srsatt.keyboard.layout.model.position
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ValidationTest {
    private val a = position("test.a")
    private val b = position("test.b")
    private val action = command("test.action")
    private val otherAction = command("test.other")
    private val english = language("en")
    private val russian = language("ru")

    @Test
    fun `same binding contributed by separate fragments reports both declarations`() {
        val declared = layout("binding-fragments") {
            firstBindingFragment(a, action)
            secondBindingFragment(a, action)
        }

        val error = assertFailsWith<IllegalArgumentException> { validateLayout(declared) }

        assertTrue(error.message.orEmpty().contains("binding 'test.a'"))
        assertTwoSourceLocations(error.message.orEmpty())
    }

    @Test
    fun `reversed same-action chord reports both declarations`() {
        val declared = layout("reversed") {
            base {
                a and b performs action
                b and a performs action
            }
        }

        val error = assertFailsWith<IllegalArgumentException> { validateLayout(declared) }

        assertTrue(error.message.orEmpty().contains("chord [test.a, test.b]"))
        assertTwoSourceLocations(error.message.orEmpty())
    }

    @Test
    fun `inline chord conflicts with shared-key expansion at original declarations`() {
        val declared = layout("shorthand") {
            base {
                a and b performs action
                chordsWith(a) { b performs otherAction }
            }
        }

        val error = assertFailsWith<IllegalArgumentException> { validateLayout(declared) }

        assertTrue(error.message.orEmpty().contains("chord [test.a, test.b]"))
        assertTwoSourceLocations(error.message.orEmpty())
    }

    @Test
    fun `same chord is valid in disjoint languages`() {
        val declared = layout("languages") {
            language(english) { a and b performs action }
            language(russian) { b and a performs otherAction }
        }

        validateLayout(declared)
    }

    @Test
    fun `different overlay layers and transparent declarations are not conflicts`() {
        val red = layer("red")
        val green = layer("green")
        val declared = layout("overlays") {
            on(red) {
                default inherits Below
                a performs action
            }
            on(green) {
                default inherits Below
                a performs otherAction
            }
            base { a inherits Below }
        }

        validateLayout(declared)
    }

    private fun assertTwoSourceLocations(message: String) {
        val locations = Regex("ValidationTest\\.kt:\\d+").findAll(message).map { it.value }.toList()
        assertTrue(locations.size >= 2, message)
        assertTrue(locations[0] != locations[1], message)
    }
}

private fun LayoutScope.firstBindingFragment(position: dev.srsatt.keyboard.layout.model.KeyPosition, action: CommandRef) {
    base { position performs action }
}

private fun LayoutScope.secondBindingFragment(position: dev.srsatt.keyboard.layout.model.KeyPosition, action: CommandRef) {
    base { position performs action }
}
