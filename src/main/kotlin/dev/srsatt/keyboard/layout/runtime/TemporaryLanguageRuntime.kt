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
        check(!isActive) { "Temporary language is already active" }
        val original = currentLanguage
        val temporary = if (original == firstLanguage) secondLanguage else firstLanguage
        savedLanguage = original
        currentLanguage = temporary
        return LanguageSwitch(original, temporary)
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
}
