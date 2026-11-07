package ch.mampfi.app

import ch.mampfi.app.data.GroceryItem
import java.text.Normalizer
import java.util.Locale

private const val MIN_GROCERY_MATCH_LENGTH = 4

internal fun matchingActiveGroceries(
    ingredientName: String,
    groceries: List<GroceryItem>,
): List<GroceryItem> {
    val ingredient = groceryMatchName(ingredientName) ?: return emptyList()
    return groceries
        .asSequence()
        .filter { !it.deleted && it.completedAt == null }
        .mapNotNull { grocery ->
            val candidate = groceryMatchName(grocery.product.name) ?: return@mapNotNull null
            grocery.takeIf { ingredient.matches(candidate) }
        }
        .sortedWith(compareBy({ it.product.name.lowercase(Locale.GERMAN) }, { it.id }))
        .toList()
}

private data class GroceryMatchName(val normalized: String, val words: Set<String>) {
    fun matches(other: GroceryMatchName): Boolean {
        if (normalized == other.normalized) return true
        return words.any { left ->
            other.words.any { right ->
                left == right ||
                    (left.length >= MIN_GROCERY_MATCH_LENGTH && right.length >= MIN_GROCERY_MATCH_LENGTH &&
                        (left.startsWith(right) || left.endsWith(right) || right.startsWith(left) || right.endsWith(left)))
            }
        }
    }
}

private fun groceryMatchName(value: String): GroceryMatchName? {
    val normalized = Normalizer.normalize(value.trim().lowercase(Locale.GERMAN).replace("ß", "ss"), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
    if (normalized.isEmpty()) return null
    val words = normalized.split(' ')
        .map(::groceryWordStem)
        .filter { it.length >= MIN_GROCERY_MATCH_LENGTH }
        .toSet()
    return GroceryMatchName(normalized, words)
}

private fun groceryWordStem(word: String): String {
    val suffix = listOf("ern", "en", "er", "es", "e", "n", "s")
        .firstOrNull { word.endsWith(it) && word.length - it.length >= MIN_GROCERY_MATCH_LENGTH }
    return if (suffix == null) word else word.dropLast(suffix.length)
}
