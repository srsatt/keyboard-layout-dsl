package dev.srsatt.keyboard.layout.model

import dev.srsatt.keyboard.layout.dsl.layout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class SymbolsTest {
    private val english = InputSource("test.english")
    private val german = InputSource("test.german")

    @Test
    fun `dot encoding is selected by host and input source`() {
        val dot = symbol('.')
        val catalog = symbols {
            dot {
                encodingIn(MacOS, english) { tap(Key.PERIOD) }
                encodingIn(MacOS, german) { tap(Shift + Key.PERIOD) }
            }
        }

        assertEquals(
            listOf(RawTapEffect(Stroke(Key.PERIOD))),
            catalog.resolveEncoding(dot, MacOS, english, EncodingFallbackPolicy.Fail).effects,
        )
        assertEquals(
            listOf(RawTapEffect(Shift + Key.PERIOD)),
            catalog.resolveEncoding(dot, MacOS, german, EncodingFallbackPolicy.Fail).effects,
        )
    }

    @Test
    fun `fallback is explicit and missing coverage fails`() {
        val dot = symbol('.')
        val french = InputSource("test.french")
        val catalog = symbols {
            dot {
                encodingIn(MacOS, english) { tap(Key.PERIOD) }
            }
        }

        assertEquals(
            english,
            catalog.resolveEncoding(
                dot,
                MacOS,
                french,
                EncodingFallbackPolicy.Use(MacOS, english),
            ).inputSource,
        )
        val error = assertFailsWith<IllegalArgumentException> {
            catalog.validateCoverage(listOf(dot, symbol('!')), MacOS, english, EncodingFallbackPolicy.Fail)
        }
        assertTrue(error.message.orEmpty().contains("'!'"))
        assertTrue(error.message.orEmpty().contains("test.english"))
    }

    @Test
    fun `semantic dot intent differs from raw Period usage`() {
        val semantic = TypeIntent(symbol('.'))
        val raw = RawTapEffect(Stroke(Key.PERIOD))

        assertIs<SymbolRef>(semantic.value)
        assertEquals(".", semantic.value.value)
        assertEquals(Key.PERIOD, raw.stroke.key)
        assertFalse(semantic.equals(raw))
    }

    @Test
    fun `German dead key encoding retains tap delay tap order`() {
        val umlaut = symbol('ä')
        val catalog = symbols {
            umlaut {
                encodingIn(MacOS, english) {
                    tap(Alt + Key.U)
                    delay(100.milliseconds)
                    tap(Key.A)
                }
            }
        }

        assertEquals(
            listOf(
                RawTapEffect(Alt + Key.U),
                DelayEffect(100.milliseconds),
                RawTapEffect(Stroke(Key.A)),
            ),
            catalog.resolveEncoding(umlaut, MacOS, english, EncodingFallbackPolicy.Fail).effects,
        )
    }

    @Test
    fun `row strings still count Unicode code points`() {
        val first = position("test.first")
        val second = position("test.second")
        val row = object : PhysicalGroup {
            override val positions = listOf(first, second)
        }
        val declared = layout("unicode-row") {
            language(language("test")) {
                row types "a\uD83D\uDE00"
            }
        }

        assertEquals(
            listOf("a", "\uD83D\uDE00"),
            declared.layers.single().bindings.map { ((it.intent as TypeIntent).value as SymbolRef).value },
        )
    }
}
