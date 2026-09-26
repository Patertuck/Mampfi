package ch.mampfi.app

import java.net.URI

private val webUrlPattern = Regex("""(?i)\b(?:https?://|www\.)[^\s<>\"']+""")
private val repeatedWhitespace = Regex("""\s+""")

internal fun extractFirstWebUrl(value: String): String? = webUrlPattern.find(value)?.value
    ?.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}')
    ?.takeIf { it.isNotBlank() }

internal fun normalizedWebUrlOrNull(value: String): String? {
    val candidate = value.trim().let {
        when {
            it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) -> it
            it.startsWith("www.", ignoreCase = true) -> "https://$it"
            else -> return null
        }
    }
    val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
    return candidate.takeIf { uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true) }
        ?.takeIf { !uri.host.isNullOrBlank() }
}

internal fun normalizePastedMealName(value: String): String = value.trim().replace(repeatedWhitespace, " ")
