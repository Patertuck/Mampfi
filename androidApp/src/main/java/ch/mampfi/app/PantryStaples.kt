package ch.mampfi.app

import ch.mampfi.app.data.RecipeIngredient
import java.util.Locale

internal fun RecipeIngredient.isIgnoredPantryStaple(): Boolean = name.isIgnoredPantryStaple()

internal fun Iterable<RecipeIngredient>.withoutIgnoredPantryStaples(): List<RecipeIngredient> = filterNot(RecipeIngredient::isIgnoredPantryStaple)

internal fun String.isIgnoredPantryStaple(): Boolean {
    val normalized = lowercase(Locale.GERMAN)
        .replace("ä", "ae")
        .replace("ö", "oe")
        .replace("ü", "ue")
        .replace("ß", "ss")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
    if (normalized.isEmpty()) return false
    val words = normalized.split(' ').filter(String::isNotEmpty)

    if (words.any { it == "salz" || it.endsWith("salz") }) return true
    if (words.any { it == "pfeffer" || it.endsWith("pfeffer") || it.endsWith("pfefferkoerner") }) return true
    if (words.any { it == "oel" || it.endsWith("oel") }) return true
    if (words.any { it == "essig" || it.endsWith("essig") || it.startsWith("balsamico") }) return true

    val waterWords = setOf(
        "wasser", "leitungswasser", "kalt", "kaltes", "kalten", "warm", "warmes", "warmen",
        "heiss", "heisses", "heissen", "lauwarm", "lauwarmes", "frisch", "frisches", "kochend",
        "kochendes", "zum", "kochen", "nach", "bedarf",
    )
    if (words.any { it == "wasser" || it == "leitungswasser" } && words.all(waterWords::contains)) return true

    val genericSeasonings = setOf("gewuerz", "gewuerze", "gewuerzmischung", "gewuerzmischungen")
    val seasoningModifiers = setOf("nach", "wahl", "bedarf", "zum", "wuerzen", "abschmecken")
    return words.any(genericSeasonings::contains) && words.all { it in genericSeasonings || it in seasoningModifiers }
}
