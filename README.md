# keyboard-layout-dsl

Typed, firmware-agnostic Kotlin DSL and compiler for keyboard layouts.

The public project owns portable layout semantics and validation. Keyboard
profiles, personal actions, host configuration, and handwritten firmware
extensions remain in consumer repositories.

## Model a layout

Physical positions have stable IDs. Profiles expose those positions through
typed, ordered groups; layers attach behavior to them:

```kotlin
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
        left { letters types "a😀" }
    }
    on(red) {
        default inherits Below
        left {
            thumbs {
                outer performs workmux
                inner does Nothing
            }
        }
    }
}
```

This is an excerpt from the executable
[`LayoutDslTest`](src/test/kotlin/dev/srsatt/keyboard/layout/dsl/LayoutDslTest.kt).
It also tests Unicode row cardinality, explicit overlay defaults, and lifecycle
typing: `sends` is paired, `holds` accepts only holdable state, `performs` is a
single invocation, and `types` records character intent.

Chords read in physical terms and are unordered:

```kotlin
a and b performs action

chordsWith(a) {
    chordsWith(pair) {
        d performs action
    }
}
```

The expansion and alias checks are exercised by
[`ChordDslTest`](src/test/kotlin/dev/srsatt/keyboard/layout/dsl/ChordDslTest.kt).

Ordinary Kotlin functions and packages are the composition mechanism. A
consumer can keep profile, actions, symbols, layer fragments, and feedback in
separate files, then call their `LayoutScope` extension functions from one
`layout(...)` declaration. No file-local registry or loader is involved; the
resulting model is validated as a whole.

## Define semantic actions and symbols

Actions own their host-specific meaning:

```kotlin
val workmuxAgents = action("workmux.agents") {
    description("Open the Workmux agent picker")
    implementationIn(MacOS) { tap(Cmd + Key.E) }
}

val terminalPaste = keystrokeAction("terminal.paste") {
    implementationIn(MacOS) { Ctrl + Key.V }
}

val openWhispr = hostAction("voice.openwhispr")
```

`action` runs once, `keystrokeAction` owns a paired press/release stroke, and
`hostAction` requires an explicit target signal. These declarations and
missing/duplicate implementation failures are exercised by
[`ActionsTest`](src/test/kotlin/dev/srsatt/keyboard/layout/model/ActionsTest.kt).

Symbols describe desired text, not a universal keycode:

```kotlin
val dot = symbol('.')
val catalog = symbols {
    dot {
        encodingIn(MacOS, english) { tap(Key.PERIOD) }
        encodingIn(MacOS, german) { tap(Shift + Key.PERIOD) }
    }
}
```

Host/source selection, explicit fallback, missing coverage, and dead-key
sequences are exercised by
[`SymbolsTest`](src/test/kotlin/dev/srsatt/keyboard/layout/model/SymbolsTest.kt).

## Bind a firmware target

Portable positions remain separate from firmware placement. A backend supplies
the layout-macro order and independent matrix/LED address maps:

```kotlin
val binding = qmkLayoutMacroBinding(
    profile = profile,
    header = "sample.h",
    macro = "LAYOUT_sample",
    signature = listOf("k00", "k01", "k10"),
    arguments = listOf(pair, third),
)
```

Complete one-to-one coverage and mapping failures are exercised by
[`QmkDeviceBindingTest`](src/test/kotlin/dev/srsatt/keyboard/layout/qmk/QmkDeviceBindingTest.kt).

## Compilation errors

Validation happens after all files contribute. The following duplicate is an
error even though the key order is reversed:

```kotlin
val invalid = layout("reversed") {
    base {
        a and b performs action
        b and a performs action
    }
}
validateLayout(invalid)
```

[`ValidationTest`](src/test/kotlin/dev/srsatt/keyboard/layout/validation/ValidationTest.kt)
asserts that the exception names the physical chord and both source locations.
Other compile-time checks cover duplicate bindings/IDs, missing action or
symbol implementations, reserved host signals, unsupported target capabilities,
mapping gaps, action cycles, and target capacity.

## Check the compiler

```bash
./gradlew check
```

The repository uses Kotlin/JVM 2.4.20 on Java 21. The Moonlander consumer is a
separate reference integration; firmware-specific C and personal host settings
do not belong in this library.
