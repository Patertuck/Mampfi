package ch.mampfi.app

internal data class SharedMealDraft(
    val name: String,
    val link: String,
)

internal fun parseSharedMealDraft(text: String?, subject: String?): SharedMealDraft? {
    val sharedText = text.orEmpty()
    val extractedLink = extractFirstWebUrl(sharedText)
        ?: extractFirstWebUrl(subject.orEmpty())
        ?: return null
    val link = normalizedWebUrlOrNull(extractedLink) ?: return null

    val subjectName = nameCandidate(subject, extractedLink)
    val textName = sharedText
        .lineSequence()
        .mapNotNull { line -> nameCandidate(line, extractedLink) }
        .firstOrNull()

    return SharedMealDraft(name = subjectName ?: textName.orEmpty(), link = link)
}

private fun nameCandidate(value: String?, sharedLink: String): String? {
    val candidate = value
        ?.replace(sharedLink, "")
        ?.trim(' ', '\t', '\r', '\n', '-', '–', '—', ':', '|')
        ?.let(::normalizePastedMealName)
        ?.takeIf { it.isNotBlank() }
        ?: return null
    return candidate.takeIf { normalizedWebUrlOrNull(it) == null }
}
