package ch.mampfi.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.io.path.createTempDirectory

class RecipeExtractorTest {
    @Test fun `parses structured Fooby recipe ingredients and portions`() {
        val html = """
            <html><head><script type="application/ld+json">
            {"@context":"https://schema.org","@type":"Recipe","name":"Pasta","recipeYield":"4 Personen",
             "recipeIngredient":["300 g Pasta","1 ½ dl Rahm","2 Zehen Knoblauch","Salz"]}
            </script></head></html>
        """.trimIndent()

        val result = FoobyRecipeExtractor().parse(html)

        assertEquals(4, result.recipeServings)
        assertEquals(listOf("300", "1.5", "2", null), result.ingredients.map { it.amount })
        assertEquals(GroceryUnit.DL, result.ingredients[1].unit)
        assertEquals(GroceryUnit.CLOVE, result.ingredients[2].unit)
        assertNull(result.ingredients.last().amount)
    }

    @Test fun `parses numeric Fooby recipe yield as portions`() {
        val html = """
            <html><head><script type="application/ld+json">
            {"@context":"https://schema.org","@type":"Recipe","name":"Döner Bowl","recipeYield":"4",
             "recipeIngredient":["400 g Kartoffeln"]}
            </script></head></html>
        """.trimIndent()

        val result = FoobyRecipeExtractor().parse(html)

        assertEquals(4, result.recipeServings)
    }

    @Test fun `extraction endpoint returns provider result`() = testApplication {
        val directory = createTempDirectory("recipe-extract-api").toFile()
        val expected = RecipeExtractResponse("FOOBY", "Pasta", 2, listOf(RecipeIngredient(name = "Pasta", amount = "200", unit = GroceryUnit.G)))
        application { module(File(directory, "mampfi.db").path, File(directory, "uploads"), recipeExtractor = object : RecipeExtractor {
            override suspend fun extract(url: String) = expected
        }) }

        val response = client.post("/api/rezepte/extrahieren") {
            contentType(ContentType.Application.Json)
            setBody("""{"url":"https://fooby.ch/de/rezepte/1/test"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(expected, Json.decodeFromString<RecipeExtractResponse>(response.bodyAsText()))
    }
}
