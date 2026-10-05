package ch.mampfi.app

import ch.mampfi.app.data.Mahlzeit
import ch.mampfi.app.data.Tag

internal enum class MealSort(val label: String) {
    LATEST("Neueste zuerst"),
    OLDEST("Älteste zuerst"),
    BEST_RATED("Beste Bewertung zuerst"),
    WORST_RATED("Niedrigste Bewertung zuerst"),
    MOST_COOKED("Am häufigsten gekocht"),
    LEAST_COOKED("Am seltensten gekocht"),
}

internal fun filterMeals(meals: List<Mahlzeit>, query: String, selectedTags: Set<Tag>): List<Mahlzeit> {
    val normalizedQuery = query.trim()
    return meals.filter { meal ->
        meal.name.contains(normalizedQuery, ignoreCase = true) && selectedTags.all(meal::hatTag)
    }
}

internal fun sortMeals(meals: List<Mahlzeit>, sort: MealSort): List<Mahlzeit> = when (sort) {
    MealSort.LATEST -> meals.sortedByDescending { it.letzterTermin().orEmpty() }
    MealSort.OLDEST -> meals.sortedBy { it.letzterTermin().orEmpty() }
    MealSort.BEST_RATED -> meals.sortedWith(
        compareByDescending<Mahlzeit> { it.durchschnitt() != null }.thenByDescending { it.durchschnitt() ?: 0.0 },
    )
    MealSort.WORST_RATED -> meals.sortedWith(
        compareByDescending<Mahlzeit> { it.durchschnitt() != null }.thenBy { it.durchschnitt() ?: 0.0 },
    )
    MealSort.MOST_COOKED -> meals.sortedByDescending { it.eintraege.size }
    MealSort.LEAST_COOKED -> meals.sortedBy { it.eintraege.size }
}
