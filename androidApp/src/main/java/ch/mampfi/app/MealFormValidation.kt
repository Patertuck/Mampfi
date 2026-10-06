package ch.mampfi.app

internal data class MealFormValidation(
    val nameError: String? = null,
    val firstRatingError: String? = null,
    val secondRatingError: String? = null,
    val ratings: List<Double>? = null,
) {
    val isValid: Boolean get() = nameError == null && firstRatingError == null && secondRatingError == null
}

internal fun validateMealForm(name: String, firstRating: String = "", secondRating: String = ""): MealFormValidation {
    val nameError = if (name.isBlank()) "Bitte gib der Mahlzeit einen Namen." else null
    if (firstRating.isBlank() && secondRating.isBlank()) return MealFormValidation(nameError = nameError)

    fun parseRating(value: String): Double? = value.replace(',', '.').toDoubleOrNull()?.takeIf { it in 1.0..10.0 }
    val first = parseRating(firstRating)
    val second = parseRating(secondRating)
    val pairedMessage = "Bitte gib beide Bewertungen ein."
    val rangeMessage = "Bitte gib eine Zahl von 1 bis 10 ein."
    val firstError = when {
        firstRating.isBlank() -> pairedMessage
        first == null -> rangeMessage
        else -> null
    }
    val secondError = when {
        secondRating.isBlank() -> pairedMessage
        second == null -> rangeMessage
        else -> null
    }
    return MealFormValidation(
        nameError = nameError,
        firstRatingError = firstError,
        secondRatingError = secondError,
        ratings = if (firstError == null && secondError == null) listOf(first!!, second!!) else null,
    )
}
