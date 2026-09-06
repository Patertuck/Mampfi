package ch.mampfi.app.data

import kotlin.test.Test
import kotlin.test.assertFalse
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
}
