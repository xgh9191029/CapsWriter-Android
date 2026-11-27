package com.capswriter.offline.util

/**
 * Client-side hotwords.
 *
 * Accepted syntax, one item per line:
 *
 *   CapsWriter                 -> only sent to the server as ASR context
 *   错词=>正确词                -> sent as context and replaced literally in the result
 *   re:错[词字]=>正确词         -> sent as context and replaced with a regular expression
 *   # comment                  -> ignored
 *
 * The context part is useful for Fun-ASR-Nano, which accepts a prompt in AudioMessage.context.
 * The replacement rules guarantee that exact corrections also work with engines that ignore
 * that prompt.
 */
data class HotwordRule(
    val source: String,
    val replacement: String,
    val isRegex: Boolean,
)

data class HotwordConfig(
    val context: String,
    val rules: List<HotwordRule>,
)

object HotwordProcessor {

    private const val MAX_CONTEXT_CHARS = 800

    fun parse(raw: String): HotwordConfig {
        val terms = LinkedHashSet<String>()
        val rules = ArrayList<HotwordRule>()

        raw.lineSequence().forEach { originalLine ->
            val line = originalLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach

            val regex = line.startsWith("re:")
            val body = if (regex) line.removePrefix("re:").trim() else line
            if (body.isEmpty()) return@forEach

            val split = splitRule(body)
            if (split == null) {
                terms.add(body)
                return@forEach
            }

            val source = split.first.trim()
            val replacement = split.second.trim()
            if (source.isEmpty()) return@forEach

            if (regex) {
                val valid = runCatching { Regex(source) }.isSuccess
                if (!valid) return@forEach
            }
            rules.add(HotwordRule(source, replacement, regex))
            if (replacement.isNotEmpty()) terms.add(replacement)
        }

        val context = terms
            .asSequence()
            .filter { it.isNotBlank() }
            .joinToString("、")
            .take(MAX_CONTEXT_CHARS)

        return HotwordConfig(context = context, rules = rules)
    }

    fun apply(text: String, raw: String): String = apply(text, parse(raw))

    fun apply(text: String, config: HotwordConfig): String {
        if (text.isEmpty() || config.rules.isEmpty()) return text
        var result = text

        // Longer literal sources first so "语音输入法" wins over "输入法".
        config.rules
            .filterNot { it.isRegex }
            .sortedByDescending { it.source.length }
            .forEach { rule ->
                if (rule.source.isNotEmpty()) {
                    result = result.replace(rule.source, rule.replacement)
                }
            }

        config.rules.filter { it.isRegex }.forEach { rule ->
            result = runCatching {
                Regex(rule.source).replace(result, rule.replacement)
            }.getOrDefault(result)
        }
        return result
    }

    private fun splitRule(body: String): Pair<String, String>? {
        val arrow = body.indexOf("=>")
        if (arrow >= 0) {
            return body.substring(0, arrow) to body.substring(arrow + 2)
        }
        val equals = body.indexOf('=')
        if (equals >= 0) {
            return body.substring(0, equals) to body.substring(equals + 1)
        }
        return null
    }
}
