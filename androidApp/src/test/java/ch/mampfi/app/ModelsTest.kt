package ch.mampfi.app.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModelsTest {
    @Test
    fun `vegetarian filter includes vegan meals but vegan filter stays exact`() {
        val vegan = Mahlzeit(name = "Vegan", tags = listOf(Tag.VEGAN.name))
        val vegetarian = Mahlzeit(name = "Vegetarisch", tags = listOf(Tag.VEGETARISCH.name))

        assertTrue(vegan.hatTag(Tag.VEGAN))
        assertTrue(vegan.hatTag(Tag.VEGETARISCH))
        assertTrue(vegetarian.hatTag(Tag.VEGETARISCH))
        assertFalse(vegetarian.hatTag(Tag.VEGAN))
    }

    @Test
    fun `selecting a diet tag clears the conflicting tag`() {
        assertTrue(Tag.VEGAN in setOf(Tag.VEGETARISCH).toggleMealTag(Tag.VEGAN))
        assertFalse(Tag.VEGETARISCH in setOf(Tag.VEGETARISCH).toggleMealTag(Tag.VEGAN))
        assertTrue(Tag.VEGETARISCH in setOf(Tag.VEGAN).toggleMealTag(Tag.VEGETARISCH))
        assertFalse(Tag.VEGAN in setOf(Tag.VEGAN).toggleMealTag(Tag.VEGETARISCH))
    }

    @Test
    fun `meal overview aggregates independent dated entries`() {
        val meal = Mahlzeit(
            name = "Mac and cheese",
            eintraege = listOf(
                MahlzeitEintrag(
                    datum = "2026-03-03",
                    bilder = listOf(MahlzeitBild(url = "/uploads/first.jpg")),
                    bewertung = MahlzeitBewertung(listOf(8.0, 9.0)),
                ),
                MahlzeitEintrag(
                    datum = "2027-06-06",
                    bilder = listOf(MahlzeitBild(url = "/uploads/second.jpg")),
                    bewertung = MahlzeitBewertung(listOf(9.0, 10.0)),
                ),
            ),
        )

        assertEquals(listOf("2026-03-03", "2027-06-06"), meal.termine)
        assertEquals("2027-06-06", meal.letzterTermin())
        assertEquals("/uploads/second.jpg", meal.letztesBild())
        assertEquals(9.0, meal.durchschnitt())
    }
}
