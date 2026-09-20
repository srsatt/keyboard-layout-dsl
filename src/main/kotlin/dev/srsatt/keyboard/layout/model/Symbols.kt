package dev.srsatt.keyboard.layout.model

import kotlin.time.Duration

data class InputSource(val id: String) {
    init {
        require(id.isNotBlank()) { "Input source ID must not be blank" }
    }
}

sealed interface EncodingFallbackPolicy {
    data object Fail : EncodingFallbackPolicy

    data class Use(
        val host: HostPlatform,
        val inputSource: InputSource,
    ) : EncodingFallbackPolicy
}

class SymbolEncoding internal constructor(
    val host: HostPlatform,
    val inputSource: InputSource,
    val effects: List<ActionEffect>,
)

class SymbolCatalog internal constructor(
    private val encodings: Map<SymbolRef, List<SymbolEncoding>>,
) {
    fun resolveEncoding(
        symbol: SymbolRef,
        host: HostPlatform,
        inputSource: InputSource,
        fallback: EncodingFallbackPolicy,
    ): SymbolEncoding {
        find(symbol, host, inputSource)?.let { return it }
        if (fallback is EncodingFallbackPolicy.Use) {
            find(symbol, fallback.host, fallback.inputSource)?.let { return it }
        }
        throw IllegalArgumentException(
            "Symbol '${symbol.value}' has no encoding for host '${host.id}' and input source '${inputSource.id}'",
        )
    }

    fun validateCoverage(
        symbols: Iterable<SymbolRef>,
        host: HostPlatform,
        inputSource: InputSource,
        fallback: EncodingFallbackPolicy,
    ) {
        symbols.toSet().forEach { resolveEncoding(it, host, inputSource, fallback) }
    }

    private fun find(symbol: SymbolRef, host: HostPlatform, inputSource: InputSource): SymbolEncoding? =
        encodings[symbol].orEmpty().singleOrNull { it.host == host && it.inputSource == inputSource }
}

class SymbolsScope internal constructor() {
    private val encodings = linkedMapOf<SymbolRef, List<SymbolEncoding>>()

    operator fun SymbolRef.invoke(block: SymbolDefinitionScope.() -> Unit) {
        require(this !in encodings) { "Symbol '$value' is defined more than once" }
        encodings[this] = SymbolDefinitionScope(this).apply(block).build()
    }

    internal fun build() = SymbolCatalog(encodings.mapValues { it.value.toList() })
}

class SymbolDefinitionScope internal constructor(private val symbol: SymbolRef) {
    private val encodings = mutableListOf<SymbolEncoding>()

    fun encodingIn(
        host: HostPlatform,
        inputSource: InputSource,
        block: SymbolEncodingScope.() -> Unit,
    ) {
        require(encodings.none { it.host == host && it.inputSource == inputSource }) {
            "Symbol '${symbol.value}' has duplicate encoding for host '${host.id}' and input source '${inputSource.id}'"
        }
        encodings += SymbolEncoding(host, inputSource, SymbolEncodingScope().apply(block).build())
    }

    internal fun build(): List<SymbolEncoding> {
        require(encodings.isNotEmpty()) { "Symbol '${symbol.value}' must declare at least one encoding" }
        return encodings.toList()
    }
}

class SymbolEncodingScope internal constructor() {
    private val effects = mutableListOf<ActionEffect>()
    private val budget = EffectBudget(MAX_ACTION_EFFECTS_PER_IMPLEMENTATION)

    fun tap(stroke: Stroke) = add(RawTapEffect(stroke))

    fun tap(key: Key) = tap(Stroke(key))

    fun delay(duration: Duration) = add(DelayEffect(duration))

    internal fun build(): List<ActionEffect> {
        require(effects.isNotEmpty()) { "A symbol encoding must declare at least one tap" }
        return effects.toList()
    }

    private fun add(effect: ActionEffect) {
        budget.record()
        effects += effect
    }
}

fun symbols(block: SymbolsScope.() -> Unit): SymbolCatalog = SymbolsScope().apply(block).build()
