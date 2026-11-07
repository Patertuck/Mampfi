package ch.mampfi.app

import ch.mampfi.app.data.GroceryCategory
import ch.mampfi.app.data.GroceryDimension
import ch.mampfi.app.data.GroceryItem
import ch.mampfi.app.data.GroceryProductRef
import ch.mampfi.app.data.GrocerySource
import ch.mampfi.app.data.GroceryUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GroceryIngredientMatchingTest {
    @Test fun `matches normalized plural descriptive and compound product names`() {
        val groceries = listOf(
            grocery("1", "Tomate"),
            grocery("2", "Cherrytomaten"),
            grocery("3", "Rote Paprika"),
        )

        assertEquals(listOf("Cherrytomaten", "Tomate"), matchingActiveGroceries("Tomaten", groceries).map { it.product.name })
        assertEquals(listOf("Rote Paprika"), matchingActiveGroceries("Paprika", groceries).map { it.product.name })
    }

    @Test fun `normalizes case punctuation umlauts and sharp s`() {
        assertEquals(listOf("GEMÜSE-SOSSE"), matchingActiveGroceries("Gemüse Soße", listOf(grocery("1", "GEMÜSE-SOSSE"))).map { it.product.name })
    }

    @Test fun `does not match unrelated or incidental short substrings`() {
        val groceries = listOf(grocery("1", "Preiselbeeren"), grocery("2", "Olivenöl"))

        assertTrue(matchingActiveGroceries("Reis", groceries).isEmpty())
        assertTrue(matchingActiveGroceries("Öl", groceries).isEmpty())
    }

    @Test fun `only returns active non-deleted items in deterministic order`() {
        val groceries = listOf(
            grocery("3", "Tomaten", completedAt = "2026-11-01T12:00:00Z"),
            grocery("2", "Tomaten gehackt", deleted = true),
            grocery("4", "Tomaten frisch"),
            grocery("1", "Bio Tomaten"),
        )

        assertEquals(listOf("Bio Tomaten", "Tomaten frisch"), matchingActiveGroceries("Tomate", groceries).map { it.product.name })
    }

    private fun grocery(id: String, name: String, completedAt: String? = null, deleted: Boolean = false) = GroceryItem(
        id = id,
        product = GroceryProductRef(GrocerySource.MANUAL, "manual:$id", name),
        category = GroceryCategory.OTHER,
        baseQuantity = 1.0,
        dimension = GroceryDimension.COUNT,
        preferredUnit = GroceryUnit.PIECE,
        createdAt = "2026-11-01T10:00:00Z",
        completedAt = completedAt,
        deleted = deleted,
    )
}
