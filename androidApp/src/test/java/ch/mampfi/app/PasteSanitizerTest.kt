package ch.mampfi.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PasteSanitizerTest {
    @Test
    fun `extracts first web url from shared multiline text`() {
        val pasted = """Mac and cheese
            |Try this recipe:
            |https://example.test/mac-and-cheese?servings=4
            |Alternative: https://example.test/other
        """.trimMargin()

        assertEquals("https://example.test/mac-and-cheese?servings=4", extractFirstWebUrl(pasted))
    }

    @Test
    fun `recognizes www links and removes surrounding sentence punctuation`() {
        assertEquals("www.example.test/recipe", extractFirstWebUrl("See (www.example.test/recipe)."))
    }

    @Test
    fun `extracts a link from surrounding single line text`() {
        assertEquals("https://example.test/recipe", extractFirstWebUrl("Recipe: https://example.test/recipe by Alex"))
    }

    @Test
    fun `returns null when pasted text contains no link`() {
        assertNull(extractFirstWebUrl("Mac and cheese recipe"))
    }

    @Test
    fun `normalizes supported recipe web links`() {
        assertEquals("https://example.test/recipe", normalizedWebUrlOrNull(" https://example.test/recipe "))
        assertEquals("http://example.test/recipe", normalizedWebUrlOrNull("http://example.test/recipe"))
        assertEquals("https://www.example.test/recipe", normalizedWebUrlOrNull("www.example.test/recipe"))
    }

    @Test
    fun `rejects recipe values that are not web links`() {
        assertNull(normalizedWebUrlOrNull("Grandma's cookbook, page 12"))
        assertNull(normalizedWebUrlOrNull("example.test/recipe"))
        assertNull(normalizedWebUrlOrNull("https://"))
        assertNull(normalizedWebUrlOrNull("ftp://example.test/recipe"))
    }

    @Test
    fun `normalizes a multiline pasted meal name`() {
        assertEquals("Creamy Mac and Cheese", normalizePastedMealName("  Creamy Mac\nand   Cheese  "))
    }

    @Test
    fun `creates shared draft from title and recipe link`() {
        assertEquals(
            SharedMealDraft("Creamy Mac and Cheese", "https://example.test/recipe"),
            parseSharedMealDraft("Creamy Mac and Cheese\nhttps://example.test/recipe", null),
        )
    }

    @Test
    fun `prefers shared subject as meal name`() {
        assertEquals(
            SharedMealDraft("Dinner tonight", "https://example.test/recipe"),
            parseSharedMealDraft("Different text\nhttps://example.test/recipe", "Dinner tonight"),
        )
    }

    @Test
    fun `normalizes www link and leaves url-only share unnamed`() {
        assertEquals(
            SharedMealDraft("", "https://www.example.test/recipe"),
            parseSharedMealDraft("www.example.test/recipe", null),
        )
    }

    @Test
    fun `removes link from a single-line shared subject`() {
        assertEquals(
            SharedMealDraft("Creamy pasta", "https://example.test/pasta"),
            parseSharedMealDraft("https://example.test/pasta", "Creamy pasta – https://example.test/pasta"),
        )
    }

    @Test
    fun `rejects shares without a web link`() {
        assertNull(parseSharedMealDraft("Creamy pasta", "Recipe"))
    }
}
