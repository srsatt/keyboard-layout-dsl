package dev.srsatt.keyboard.layout.dsl

import dev.srsatt.keyboard.layout.model.CommandRef
import dev.srsatt.keyboard.layout.model.DisabledIntent
import dev.srsatt.keyboard.layout.model.Holdable
import dev.srsatt.keyboard.layout.model.LayerContext
import dev.srsatt.keyboard.layout.model.OverlayDefault
import dev.srsatt.keyboard.layout.model.PerformIntent
import dev.srsatt.keyboard.layout.model.PhysicalGroup
import dev.srsatt.keyboard.layout.model.SendIntent
import dev.srsatt.keyboard.layout.model.TransparentIntent
import dev.srsatt.keyboard.layout.model.TypeIntent
import dev.srsatt.keyboard.layout.model.command
import dev.srsatt.keyboard.layout.model.keystroke
import dev.srsatt.keyboard.layout.model.language
import dev.srsatt.keyboard.layout.model.layer
import dev.srsatt.keyboard.layout.model.modifier
import dev.srsatt.keyboard.layout.model.position
import dev.srsatt.keyboard.layout.model.symbol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LayoutDslTest {
    private val board = TestBoard()

    @Test
    fun `grouped declarations retain lifecycle and character intent`() = with(board) {
        val red = layer("red")
        val english = language("en")
        val shift = modifier("shift")
        val backspace = keystroke("backspace")
        val workmux = command("workmux.agents")

        val declared = layout("example") {
            base {
                left {
                    thumbs {
                        outer holds shift
                        inner sends backspace
                    }
                    symbols {
                        first types symbol('.')
                    }
                }
            }
            language(english) {
                left {
                    letters types "a\uD83D\uDE00"
                }
            }
            on(red) {
                default inherits Below
                left {
                    thumbs {
                        outer performs workmux
                        inner does Nothing
                    }
                    symbols {
                        first inherits Below
                    }
                }
            }
        }

        assertEquals(3, declared.layers.size)
        assertEquals(LayerContext.Base, declared.layers[0].context)
        assertIs<SendIntent>(declared.layers[0].bindings[1].intent)
        assertEquals(listOf("a", "\uD83D\uDE00"), declared.layers[1].bindings.map { (it.intent as TypeIntent).value.value })
        assertEquals(OverlayDefault.TRANSPARENT, declared.layers[2].default)
        assertIs<PerformIntent>(declared.layers[2].bindings[0].intent)
        assertIs<DisabledIntent>(declared.layers[2].bindings[1].intent)
        assertIs<TransparentIntent>(declared.layers[2].bindings[2].intent)
    }

    @Test
    fun `row strings must match physical cardinality by Unicode code point`() = with(board) {
        val error = assertFailsWith<IllegalArgumentException> {
            layout("invalid-row") {
                base {
                    letters types "abc"
                }
            }
        }

        assertTrue(error.message.orEmpty().contains("2 positions"))
    }

    @Test
    fun `overlay declarations require an explicit default`() {
        val error = assertFailsWith<IllegalArgumentException> {
            layout("missing-default") {
                on(layer("red")) { }
            }
        }

        assertTrue(error.message.orEmpty().contains("must declare"))
    }

    @Test
    fun `general commands cannot cross the hold lifecycle boundary`() {
        assertFalse(Holdable::class.java.isAssignableFrom(CommandRef::class.java))

        val holdMethods = LayerScope::class.java.declaredMethods.filter { it.name == "holds" }
        assertEquals(1, holdMethods.size)
        assertEquals(Holdable::class.java, holdMethods.single().parameterTypes.last())
    }

    private class TestBoard {
        val letterA = position("test.left.letter.a")
        val letterEmoji = position("test.left.letter.emoji")
        val thumbOuter = position("test.left.thumb.outer")
        val thumbInner = position("test.left.thumb.inner")
        val symbolFirst = position("test.left.symbol.first")

        val letters = physicalGroup(letterA, letterEmoji)
        val thumbs = Thumbs(thumbOuter, thumbInner)
        val symbols = Symbols(symbolFirst)
        val left = physicalGroup(letters, thumbs, symbols)
    }

    private data class Thumbs(
        val outer: dev.srsatt.keyboard.layout.model.KeyPosition,
        val inner: dev.srsatt.keyboard.layout.model.KeyPosition,
    ) : PhysicalGroup {
        override val positions = listOf(outer, inner)
    }

    private data class Symbols(
        val first: dev.srsatt.keyboard.layout.model.KeyPosition,
    ) : PhysicalGroup {
        override val positions = listOf(first)
    }

}

private fun physicalGroup(vararg groups: PhysicalGroup): PhysicalGroup = object : PhysicalGroup {
    override val positions = groups.flatMap(PhysicalGroup::positions)
}
