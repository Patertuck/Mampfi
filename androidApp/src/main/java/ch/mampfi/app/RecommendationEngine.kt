package ch.mampfi.app

import ch.mampfi.app.data.DatiertesMahlzeitBild
import ch.mampfi.app.data.Mahlzeit
import ch.mampfi.app.data.Tag
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.random.Random

internal data class RecommendationFilters(
    val vegetarian: Boolean = false,
    val vegan: Boolean = false,
    val dessert: Boolean = false,
    val notElaborate: Boolean = false,
) {
    val activeCount get() = listOf(vegetarian, vegan, dessert, notElaborate).count { it }
}

internal data class MealRecommendation(
    val meal: Mahlzeit,
    val score: Double,
    val reason: String,
    val lastCooked: LocalDate,
    val cookCount: Int,
    val averageRating: Double?,
    val photos: List<DatiertesMahlzeitBild>,
)

internal fun recommendationDeck(
    meals: List<Mahlzeit>,
    targetDate: LocalDate,
    today: LocalDate = LocalDate.now(),
    filters: RecommendationFilters = RecommendationFilters(),
    random: Random = Random.Default,
): List<MealRecommendation> {
    val candidates = meals.mapNotNull { meal ->
        val datedEntries = meal.eintraege.mapNotNull { entry ->
            runCatching { LocalDate.parse(entry.datum) }.getOrNull()?.let { it to entry }
        }
        if (datedEntries.any { (date, _) -> date == targetDate || date > today }) return@mapNotNull null
        if (filters.vegetarian && !meal.hatTag(Tag.VEGETARISCH)) return@mapNotNull null
        if (filters.vegan && !meal.hatTag(Tag.VEGAN)) return@mapNotNull null
        if (filters.dessert && !meal.hatTag(Tag.DESSERT)) return@mapNotNull null
        if (filters.notElaborate && meal.hatTag(Tag.AUFWANDIG)) return@mapNotNull null

        val pastEntries = datedEntries.filter { (date, _) -> date <= today }
        if (pastEntries.isEmpty()) return@mapNotNull null
        val lastCooked = pastEntries.maxOf { it.first }
        val ratings = pastEntries.flatMap { (_, entry) -> entry.bewertung?.werte.orEmpty() }
        val averageRating = ratings.takeIf { it.isNotEmpty() }?.average()
        val daysSince = ChronoUnit.DAYS.between(lastCooked, today).coerceAtLeast(0)
        val recentCookCount = pastEntries.count { (date, _) -> date >= today.minusDays(60) }
        val recency = (daysSince.toDouble() / 180.0).coerceIn(0.0, 1.0)
        val rating = averageRating?.let { ((it - 1.0) / 9.0).coerceIn(0.0, 1.0) } ?: 0.5
        val novelty = 1.0 / (1.0 + recentCookCount)
        val score = recency * 0.50 + rating * 0.35 + novelty * 0.15
        val reason = when {
            recency * 0.50 >= rating * 0.35 && daysSince >= 60 -> "Lange nicht gekocht"
            averageRating != null && averageRating >= 8.0 -> "Sehr gut bewertet"
            recentCookCount == 0 -> "Mal wieder etwas anderes"
            else -> "Passt zu euren Favoriten"
        }
        val photos = pastEntries
            .flatMap { (date, entry) -> entry.bilder.map { DatiertesMahlzeitBild(it.url, date.toString()) } }
            .sortedByDescending { it.datum }
        MealRecommendation(meal, score, reason, lastCooked, pastEntries.size, averageRating, photos)
    }
    return weightedOrder(candidates, random)
}

private fun weightedOrder(candidates: List<MealRecommendation>, random: Random): List<MealRecommendation> {
    val remaining = candidates.toMutableList()
    return buildList {
        while (remaining.isNotEmpty()) {
            val totalWeight = remaining.sumOf { it.score + 0.1 }
            var draw = random.nextDouble(totalWeight)
            var selectedIndex = remaining.lastIndex
            for (index in remaining.indices) {
                draw -= remaining[index].score + 0.1
                if (draw <= 0.0) {
                    selectedIndex = index
                    break
                }
            }
            add(remaining.removeAt(selectedIndex))
        }
    }
}
