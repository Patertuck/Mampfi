package ch.mampfi.app

private val webUrlPattern = Regex("""(?i)\b(?:https?://|www\.)[^\s<>\"']+""")
private val repeatedWhitespace = Regex("""\s+""")

internal fun extractFirstWebUrl(value: String): String? = webUrlPattern.find(value)?.value
    ?.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}')
    ?.takeIf { it.isNotBlank() }

internal fun normalizePastedMealName(value: String): String = value.trim().replace(repeatedWhitespace, " ")
