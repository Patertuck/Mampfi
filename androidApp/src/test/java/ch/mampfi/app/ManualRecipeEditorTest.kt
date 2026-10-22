package ch.mampfi.app

import ch.mampfi.app.data.GroceryUnit
import ch.mampfi.app.data.Mahlzeit
import ch.mampfi.app.data.RecipeIngredient
import ch.mampfi.app.data.RecipeStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ManualRecipeEditorTest {
    @Test
    fun `manual recipe requires servings ingredients and steps`() {
        val empty = validateManualRecipe("", emptyList(), emptyList())
        val complete = validateManualRecipe(
            "2",
            listOf(RecipeIngredient(name = "Pasta", amount = "200", unit = GroceryUnit.G)),
            listOf(RecipeStep(text = "Pasta kochen")),
        )

        assertFalse(empty.isValid)
        assertTrue(complete.isValid)
    }

    @Test
    fun `manual recipe rejects invalid quantities and empty custom units`() {
        val amount = RecipeIngredient(id = "amount", name = "Pasta", amount = "zero")
        val custom = RecipeIngredient(id = "custom", name = "Salz", unit = GroceryUnit.CUSTOM)

        val result = validateManualRecipe("2", listOf(amount, custom), listOf(RecipeStep(text = "Kochen")))

        assertEquals(setOf("amount"), result.invalidAmountIds)
        assertEquals(setOf("custom"), result.invalidCustomUnitIds)
        assertFalse(result.isValid)
    }

    @Test
    fun `manual recipe normalization trims content and removes blank rows`() {
        val normalized = normalizedManualRecipe(
            Mahlzeit(name = "Pasta", rezeptLink = "https://example.test"),
            "4",
            listOf(
                RecipeIngredient(name = "  Pasta  ", amount = "1,5", unit = GroceryUnit.KG),
                RecipeIngredient(name = "   "),
            ),
            listOf(RecipeStep(text = "  Wasser kochen  "), RecipeStep(text = "   ")),
        )

        assertNull(normalized.rezeptLink)
        assertEquals(4, normalized.recipeServings)
        assertEquals(listOf("Pasta"), normalized.ingredients.map { it.name })
        assertEquals(listOf("1.5"), normalized.ingredients.map { it.amount })
        assertEquals(listOf("Wasser kochen"), normalized.recipeSteps.map { it.text })
        assertTrue(normalized.hatManuellesRezept())
    }
}
