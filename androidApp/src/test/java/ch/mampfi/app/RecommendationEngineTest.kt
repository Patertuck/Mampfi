package ch.mampfi.app

import ch.mampfi.app.data.Mahlzeit
import ch.mampfi.app.data.MahlzeitBewertung
import ch.mampfi.app.data.MahlzeitEintrag
import ch.mampfi.app.data.Tag
import java.time.LocalDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecommendationEngineTest {
    private val today = LocalDate.of(2026, 10, 1)

    @Test
    fun `only previously cooked unscheduled meals are eligible`() {
        val cooked = meal("Cooked", today.minusDays(100))
        val idea = Mahlzeit(name = "Idea", istIdee = true)
        val scheduled = meal("Scheduled", today.minusDays(100), today.plusDays(2))
        val alreadyOnTarget = meal("Target", today.minusDays(100), today.plusDays(1))

        val result = recommendationDeck(
            listOf(cooked, idea, scheduled, alreadyOnTarget),
            targetDate = today.plusDays(1),
            today = today,
            random = Random(1),
        )

        assertEquals(listOf("Cooked"), result.map { it.meal.name })
    }

    @Test
    fun `recency has more influence than rating`() {
        val recentFavourite = ratedMeal("Recent", today.minusDays(1), 10.0)
        val forgottenAverage = ratedMeal("Forgotten", today.minusDays(180), 6.0)

        val result = recommendationDeck(
            listOf(recentFavourite, forgottenAverage), today.plusDays(1), today, random = Random(2),
        ).associateBy { it.meal.name }

        assertTrue(result.getValue("Forgotten").score > result.getValue("Recent").score)
        assertEquals("Lange nicht gekocht", result.getValue("Forgotten").reason)
    }

    @Test
    fun `filters use meal tag semantics and preserve all candidates without duplicates`() {
        val vegan = meal("Vegan", today.minusDays(20), tags = listOf(Tag.VEGAN.name))
        val vegetarian = meal("Vegetarian", today.minusDays(30), tags = listOf(Tag.VEGETARISCH.name))
        val elaborate = meal("Elaborate", today.minusDays(40), tags = listOf(Tag.VEGETARISCH.name, Tag.AUFWANDIG.name))

        val result = recommendationDeck(
            listOf(vegan, vegetarian, elaborate),
            today.plusDays(1),
            today,
            RecommendationFilters(vegetarian = true, notElaborate = true),
            Random(3),
        )

        assertEquals(setOf("Vegan", "Vegetarian"), result.map { it.meal.name }.toSet())
        assertEquals(result.size, result.map { it.meal.id }.distinct().size)
    }

    private fun meal(name: String, vararg dates: LocalDate, tags: List<String> = emptyList()) = Mahlzeit(
        name = name,
        tags = tags,
        eintraege = dates.map { MahlzeitEintrag(datum = it.toString()) },
    )

    private fun ratedMeal(name: String, date: LocalDate, rating: Double) = Mahlzeit(
        name = name,
        eintraege = listOf(MahlzeitEintrag(datum = date.toString(), bewertung = MahlzeitBewertung(listOf(rating, rating)))),
    )
}
