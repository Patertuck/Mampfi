package ch.mampfi.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

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

    @Test fun `follows KptnCook share redirects and parses supplied recipe`() = runBlocking {
        val html = kptnCookHtml(
            "Blumenkohl süß-sauer",
            "1 Blumenkohl", "30 ml Ketchup", "1 EL Sojasauce", "2 EL Rohrohrzucker",
            "2 EL Reisessig", "100 g Speisestärke", "40 ml Wasser", "3 EL Olivenöl",
            "Salz", "Pfeffer", "130 g Basmati-Reis", "1 Frühlingszwiebel",
        )
        val client = HttpClient(MockEngine { request ->
            when {
                request.url.host == "share.kptncook.com" -> respond(
                    "", HttpStatusCode.MovedPermanently,
                    headersOf(HttpHeaders.Location, "https://app.kptncook.com/de/recipes/Blumenkohl%20s%C3%BC%C3%9F-sauer/d1b96d10"),
                )
                else -> respond(html, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString()))
            }
        }) { followRedirects = false }

        val result = SupportedRecipeExtractor(client).extract("https://share.kptncook.com/Dh4a/n7482bp6")

        assertEquals("KPTNCOOK", result.source)
        assertEquals("Blumenkohl süß-sauer", result.title)
        assertEquals(2, result.recipeServings)
        assertEquals(12, result.ingredients.size)
        assertEquals(RecipeIngredientValues("Blumenkohl", "1", GroceryUnit.PIECE, null), result.ingredients[0].values())
        assertEquals(RecipeIngredientValues("Ketchup", "30", GroceryUnit.ML, null), result.ingredients[1].values())
        assertEquals(RecipeIngredientValues("Sojasauce", "1", GroceryUnit.TBSP, null), result.ingredients[2].values())
        assertEquals(RecipeIngredientValues("Salz", null, GroceryUnit.CUSTOM, "nach Bedarf"), result.ingredients[8].values())
        assertEquals(RecipeIngredientValues("Frühlingszwiebel", "1", GroceryUnit.PIECE, null), result.ingredients[11].values())
    }

    @Test fun `follows Fooby redirect to little Fooby and parses supplied recipe`() = runBlocking {
        val html = """
            <html><head><script type="application/ld+json">
            {"@context":"https://schema.org","@type":"Recipe","name":"Aelpler One Pot","recipeYield":"4",
             "recipeIngredient":["200 g Kartoffeln","1 Apfel"]}
            </script></head></html>
        """.trimIndent()
        val client = HttpClient(MockEngine { request ->
            if (request.url.host == "fooby.ch") respond(
                "", HttpStatusCode.MovedPermanently,
                headersOf(HttpHeaders.Location, "https://little.fooby.ch/de/rezepte/19291/aelpler-one-pot"),
            ) else respond(html, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString()))
        }) { followRedirects = false }

        val result = SupportedRecipeExtractor(client).extract("https://fooby.ch/de/rezepte/19291/")

        assertEquals("FOOBY", result.source)
        assertEquals("Aelpler One Pot", result.title)
        assertEquals(4, result.recipeServings)
        assertEquals(listOf("Kartoffeln", "Apfel"), result.ingredients.map { it.name })
    }

    @Test fun `parses KptnCook count names fractions and provider units`() {
        val html = kptnCookHtml(
            "Testrezept",
            "2 rote Paprikas", "1 Zehe(n) Knoblauch", "2 Zweig(e) Thymian", "1/2 Zitrone",
        )

        val result = SupportedRecipeExtractor().parse(html, "KPTNCOOK")

        assertEquals(RecipeIngredientValues("rote Paprikas", "2", GroceryUnit.PIECE, null), result.ingredients[0].values())
        assertEquals(RecipeIngredientValues("Knoblauch", "1", GroceryUnit.CLOVE, null), result.ingredients[1].values())
        assertEquals(RecipeIngredientValues("Thymian", "2", GroceryUnit.CUSTOM, "Zweig(e)"), result.ingredients[2].values())
        assertEquals(RecipeIngredientValues("Zitrone", "0.5", GroceryUnit.PIECE, null), result.ingredients[3].values())
    }

    @Test fun `rejects KptnCook redirect to unsupported host`() = runBlocking {
        val client = HttpClient(MockEngine {
            respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://example.test/recipe"))
        }) { followRedirects = false }

        assertFailsWith<IllegalArgumentException> {
            SupportedRecipeExtractor(client).extract("https://share.kptncook.com/Dh4a/n7482bp6")
        }
        Unit
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

    private fun kptnCookHtml(title: String, vararg ingredients: String): String = """
        <html><head><title>$title</title></head><body itemscope itemtype="http://schema.org/Recipe">
        <span itemprop="name">$title</span>
        <span itemprop="recipeYield">Für 2 Portionen</span>
        ${ingredients.joinToString("\n") { ingredient ->
            val match = Regex("^((?:\\d+(?:[.,]\\d+)?|\\d+/\\d+))\\s+(.+)$").matchEntire(ingredient)
            if (match == null) {
                "<span itemprop=\"ingredients\">$ingredient</span>"
            } else {
                val amount = match.groupValues[1]
                val remainder = match.groupValues[2]
                val token = remainder.substringBefore(' ')
                val hasUnit = token.lowercase().trimEnd('.').replace("(n)", "n").replace("(e)", "e") in setOf(
                    "g", "kg", "ml", "dl", "l", "tl", "el", "zehe", "zehen", "zweig", "zweige", "blatt", "blätter",
                )
                val measure = if (hasUnit) "$amount $token" else amount
                val name = if (hasUnit) remainder.substringAfter(' ') else remainder
                "<span itemprop=\"ingredients\">\n$measure\n$name\n</span>"
            }
        }}
        </body></html>
    """.trimIndent()

    private data class RecipeIngredientValues(
        val name: String, val amount: String?, val unit: GroceryUnit, val customUnit: String?,
    )

    private fun RecipeIngredient.values() = RecipeIngredientValues(name, amount, unit, customUnit)
}
