package ch.mampfi.app

import ch.mampfi.app.data.RecipeIngredient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PantryStaplesTest {
    @Test fun `common pantry staples are ignored`() {
        listOf(
            "Salz", "Meersalz", "Salz und Pfeffer", "schwarzer Pfeffer", "Pfefferkörner",
            "Öl", "Olivenöl zum Braten", "Rapsoel", "Sonnenblumenöl",
            "Essig", "Apfelessig", "Balsamico-Creme",
            "Wasser", "kaltes Wasser", "kochendes Leitungswasser", "Wasser zum Kochen",
            "Gewürze", "Gewürzmischung nach Wahl", "Gewürze zum Abschmecken",
        ).forEach { assertTrue(it.isIgnoredPantryStaple(), it) }
    }

    @Test fun `specific and similarly named ingredients remain eligible`() {
        listOf(
            "Pfefferminze", "Essiggurken", "Kokoswasser", "Rosenwasser", "Mineralwasser",
            "Paprika", "Kreuzkümmel", "italienische Gewürzmischung", "Butter", "Zucker", "Mehl",
        ).forEach { assertFalse(it.isIgnoredPantryStaple(), it) }
    }

    @Test fun `recipe filtering keeps only ingredients that belong on the shopping list`() {
        val ingredients = listOf("Salz", "Pasta", "Olivenöl", "Tomaten", "Kokoswasser").map { RecipeIngredient(name = it) }

        assertEquals(listOf("Pasta", "Tomaten", "Kokoswasser"), ingredients.withoutIgnoredPantryStaples().map { it.name })
        assertTrue(listOf("Salz", "Pfeffer", "Wasser").map { RecipeIngredient(name = it) }.withoutIgnoredPantryStaples().isEmpty())
    }
}
