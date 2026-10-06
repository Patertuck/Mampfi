package ch.mampfi.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MealFormValidationTest {
    @Test
    fun `name is required`() {
        val result = validateMealForm("  ")

        assertFalse(result.isValid)
        assertEquals("Bitte gib der Mahlzeit einen Namen.", result.nameError)
    }

    @Test
    fun `ratings are optional as a pair`() {
        val result = validateMealForm("Ramen")

        assertTrue(result.isValid)
        assertNull(result.ratings)
    }

    @Test
    fun `both ratings are required when either is entered`() {
        val result = validateMealForm("Ramen", "8,5", "")

        assertFalse(result.isValid)
        assertEquals("Bitte gib beide Bewertungen ein.", result.secondRatingError)
    }

    @Test
    fun `ratings accept decimal comma and stay within range`() {
        val valid = validateMealForm("Ramen", "8,5", "10")
        val invalid = validateMealForm("Ramen", "0", "11")

        assertTrue(valid.isValid)
        assertEquals(listOf(8.5, 10.0), valid.ratings)
        assertFalse(invalid.isValid)
        assertEquals("Bitte gib eine Zahl von 1 bis 10 ein.", invalid.firstRatingError)
        assertEquals("Bitte gib eine Zahl von 1 bis 10 ein.", invalid.secondRatingError)
    }

    @Test
    fun `rating adjustment snaps to quarter steps and respects bounds`() {
        assertEquals(7.5, adjustRatingByQuarter(7.3, 1))
        assertEquals(7.0, adjustRatingByQuarter(7.3, -1))
        assertEquals(1.0, adjustRatingByQuarter(1.0, -1))
        assertEquals(10.0, adjustRatingByQuarter(10.0, 1))
    }
}
