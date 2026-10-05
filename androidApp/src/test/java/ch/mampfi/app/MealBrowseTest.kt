package ch.mampfi.app

import ch.mampfi.app.data.Mahlzeit
import ch.mampfi.app.data.MahlzeitBewertung
import ch.mampfi.app.data.MahlzeitEintrag
import ch.mampfi.app.data.Tag
import kotlin.test.Test
import kotlin.test.assertEquals

class MealBrowseTest {
    private val ramen = Mahlzeit(
        name = "Ramen",
        tags = listOf(Tag.VEGAN.name),
        eintraege = listOf(MahlzeitEintrag(datum = "2026-01-01", bewertung = MahlzeitBewertung(listOf(9.0, 8.0)))),
    )
    private val pizza = Mahlzeit(
        name = "Pizza",
        tags = listOf(Tag.VEGETARISCH.name),
        eintraege = listOf(
            MahlzeitEintrag(datum = "2025-01-01", bewertung = MahlzeitBewertung(listOf(7.0, 8.0))),
            MahlzeitEintrag(datum = "2025-02-01", bewertung = MahlzeitBewertung(listOf(8.0, 8.0))),
        ),
    )
    private val soup = Mahlzeit(name = "Soup", eintraege = listOf(MahlzeitEintrag(datum = "2024-01-01")))
    private val meals = listOf(ramen, pizza, soup)

    @Test
    fun `query and tags combine`() {
        assertEquals(listOf(ramen), filterMeals(meals, "ram", setOf(Tag.VEGETARISCH)))
        assertEquals(listOf(pizza), filterMeals(meals, "", setOf(Tag.VEGETARISCH)).filterNot { it.hatTag(Tag.VEGAN) })
    }

    @Test
    fun `date and frequency sorts are explicit`() {
        assertEquals(listOf("Ramen", "Pizza", "Soup"), sortMeals(meals, MealSort.LATEST).map { it.name })
        assertEquals(listOf("Soup", "Pizza", "Ramen"), sortMeals(meals, MealSort.OLDEST).map { it.name })
        assertEquals("Pizza", sortMeals(meals, MealSort.MOST_COOKED).first().name)
        assertEquals("Ramen", sortMeals(meals, MealSort.LEAST_COOKED).first().name)
    }

    @Test
    fun `unrated meals stay behind rated meals`() {
        assertEquals(listOf("Ramen", "Pizza", "Soup"), sortMeals(meals, MealSort.BEST_RATED).map { it.name })
        assertEquals(listOf("Pizza", "Ramen", "Soup"), sortMeals(meals, MealSort.WORST_RATED).map { it.name })
    }
}
