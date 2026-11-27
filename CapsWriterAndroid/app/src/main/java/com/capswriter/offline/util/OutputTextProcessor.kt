package com.capswriter.offline.util

/**
 * Mirrors the desktop client's trash_punc behavior.
 *
 * The desktop counts semantic units: one CJK character = 1, one Latin word = 1,
 * one number = 1. When the count is **not greater** than trash_punc_thresh it
 * removes the last trailing punctuation; longer sentences keep their punctuation.
 * `force` is used for apps such as WeChat (desktop trash_punc_apps).
 */
object OutputTextProcessor {

    private val TRAILING_PUNCTUATION = setOf('，', '。', ',', '.')
    private val SEMANTIC_UNIT = Regex(
        "[\\u4E00-\\u9FFF\\u3400-\\u4DBF\\uF900-\\uFAFF]|[a-zA-Z]+|\\d+"
    )

    fun countSemanticUnits(text: String): Int = SEMANTIC_UNIT.findAll(text).count()

    @JvmOverloads
    fun trimTrailingPunctuation(
        text: String,
        threshold: Int = 0,
        force: Boolean = false,
    ): String {
        if (text.length <= 1) return text
        if (!force && threshold > 0 && countSemanticUnits(text) > threshold) {
            return text
        }

        var end = text.length
        while (end > 0 && text[end - 1].isWhitespace()) end--
        if (end <= 1) return text
        if (text[end - 1] in TRAILING_PUNCTUATION) end--
        return text.substring(0, end)
    }
}
