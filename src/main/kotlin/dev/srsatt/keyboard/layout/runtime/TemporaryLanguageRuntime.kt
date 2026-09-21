package dev.srsatt.keyboard.layout.runtime

import dev.srsatt.keyboard.layout.model.LanguageRef

sealed interface CommittedOutput {
    data class Text(val value: String) : CommittedOutput {
        init {
            require(value.codePointCount(0, value.length) == 1) {
                "Committed text must contain exactly one Unicode code point"
            }
        }
    }

    data object Enter : CommittedOutput
    data object Tab : CommittedOutput
    data object Backspace : CommittedOutput
    data object Navigation : CommittedOutput
    data object Modifier : CommittedOutput
}

data class LanguageSwitch(val from: LanguageRef, val to: LanguageRef)

/**
 * Tracks a one-word language switch independently of any firmware backend.
 * [afterCommitted] must be called after the corresponding output lifecycle completes.
 */
class TemporaryLanguageRuntime(
    private val firstLanguage: LanguageRef,
    private val secondLanguage: LanguageRef,
    initialLanguage: LanguageRef,
) {
    private var savedLanguage: LanguageRef? = null

    var currentLanguage: LanguageRef = initialLanguage
        private set

    val isActive: Boolean
        get() = savedLanguage != null

    init {
        require(firstLanguage != secondLanguage) { "Temporary languages must be distinct" }
        require(initialLanguage == firstLanguage || initialLanguage == secondLanguage) {
            "Initial language '${initialLanguage.id}' is not configured"
        }
    }

    fun enter(): LanguageSwitch {
        savedLanguage?.let { original ->
            val temporary = currentLanguage
            savedLanguage = null
            currentLanguage = original
            return LanguageSwitch(temporary, original)
        }

        val original = currentLanguage
        val temporary = if (original == firstLanguage) secondLanguage else firstLanguage
        savedLanguage = original
        currentLanguage = temporary
        return LanguageSwitch(original, temporary)
    }

    /** Clears temporary state, then performs exactly one ordinary toggle from the active language. */
    fun switchLanguage(): List<LanguageSwitch> = buildList {
        savedLanguage = null
        val previous = currentLanguage
        val selected = if (previous == firstLanguage) secondLanguage else firstLanguage
        currentLanguage = selected
        add(LanguageSwitch(previous, selected))
    }

    fun afterCommitted(output: CommittedOutput): LanguageSwitch? {
        val original = savedLanguage ?: return null
        if (output.continuesWord()) return null

        val temporary = currentLanguage
        currentLanguage = original
        savedLanguage = null
        return LanguageSwitch(temporary, original)
    }
}

private fun CommittedOutput.continuesWord(): Boolean = when (this) {
    is CommittedOutput.Text -> value.codePoints().allMatch { codePoint ->
        Character.isLetterOrDigit(codePoint) || codePoint == '-'.code || codePoint == '_'.code
    }

    CommittedOutput.Enter,
    CommittedOutput.Tab,
    -> false

    CommittedOutput.Backspace,
    CommittedOutput.Navigation,
    CommittedOutput.Modifier,
    -> true
}
