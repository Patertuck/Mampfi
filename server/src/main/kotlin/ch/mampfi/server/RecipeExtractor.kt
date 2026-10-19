package ch.mampfi.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.*
import io.ktor.client.plugins.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.http.HttpHeaders
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.jsoup.Jsoup
import java.math.BigDecimal
import java.net.URI
import java.util.UUID

@Serializable data class RecipeExtractRequest(val url: String)
@Serializable data class RecipeExtractResponse(
    val source: String, val title: String? = null, val recipeServings: Int? = null,
    val ingredients: List<RecipeIngredient>,
)

interface RecipeExtractor { suspend fun extract(url: String): RecipeExtractResponse }

class FoobyRecipeExtractor(private val client: HttpClient = HttpClient(CIO) {
    followRedirects = false
    install(HttpTimeout) { requestTimeoutMillis = 10_000; connectTimeoutMillis = 5_000; socketTimeoutMillis = 10_000 }
}) : RecipeExtractor {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun extract(url: String): RecipeExtractResponse {
        val uri = runCatching { URI(url) }.getOrElse { throw IllegalArgumentException("Ungültiger Rezept-Link") }
        require(uri.scheme == "https" && uri.host?.lowercase() in setOf("fooby.ch", "www.fooby.ch")) { "Dieser Rezept-Link wird noch nicht unterstützt" }
        val response = client.get(uri.toString()) { headers.append(HttpHeaders.UserAgent, "Mampfi/1.0 (private recipe planner)") }
        if (!response.status.isSuccess()) error("FOOBY antwortete mit ${response.status.value}")
        val body = response.bodyAsText()
        require(body.length <= 2_000_000) { "Rezeptseite ist zu gross" }
        return parse(body)
    }

    internal fun parse(html: String): RecipeExtractResponse {
        val document = Jsoup.parse(html)
        val recipeJson = document.select("script[type=application/ld+json]").asSequence()
            .mapNotNull { runCatching { json.parseToJsonElement(it.data()) }.getOrNull() }
            .flatMap(::jsonObjects)
            .firstOrNull { node -> node["@type"].hasRecipeType() }
        val rawIngredients = recipeJson?.get("recipeIngredient")?.jsonArrayOrNull()?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?.takeIf { it.isNotEmpty() }
            ?: document.select("[itemprop=recipeIngredient], .recipe-ingredients li, .ingredient-list li, [class*=ingredient] li")
                .map { it.text() }.filter { it.isNotBlank() }.distinct()
        require(rawIngredients.isNotEmpty()) { "Auf dieser Seite konnten keine Zutaten erkannt werden" }
        val yield = recipeJson?.get("recipeYield")?.let(::yieldText)
        return RecipeExtractResponse(
            source = "FOOBY",
            title = recipeJson?.get("name")?.jsonPrimitive?.contentOrNull ?: document.selectFirst("h1")?.text(),
            recipeServings = yield?.let(::servingsFromYield),
            ingredients = rawIngredients.mapNotNull(::parseIngredient),
        )
    }

    private fun parseIngredient(raw: String): RecipeIngredient? {
        val text = raw.trim().replace(Regex("\\s+"), " ")
        val match = Regex("^([0-9]+\\s+[¼½¾⅓⅔⅛⅜⅝⅞]|[0-9]+(?:[.,][0-9]+)?|[¼½¾⅓⅔⅛⅜⅝⅞])?\\s*([^\\s]+)?\\s*(.+)$").matchEntire(text) ?: return null
        val amount = match.groupValues[1].takeIf { it.isNotBlank() }?.let(::decimal)
        var unitToken = match.groupValues[2]
        var name = match.groupValues[3].trim()
        val unit = unitFor(unitToken)
        if (amount == null) { name = listOf(unitToken, name).filter { it.isNotBlank() }.joinToString(" "); unitToken = "" }
        return name.takeIf { it.isNotBlank() }?.let {
            RecipeIngredient(
                id = UUID.randomUUID().toString(), name = it, amount = amount,
                unit = if (amount == null) GroceryUnit.CUSTOM else unit.first,
                customUnit = if (amount == null) "nach Bedarf" else unit.second,
                category = categoryFor(it), note = raw.takeIf { original -> original != text },
            )
        }
    }

    private fun unitFor(token: String): Pair<GroceryUnit, String?> = when (token.trim().lowercase().trimEnd('.')) {
        "g", "gr" -> GroceryUnit.G to null; "kg" -> GroceryUnit.KG to null
        "ml" -> GroceryUnit.ML to null; "dl" -> GroceryUnit.DL to null; "l" -> GroceryUnit.L to null
        "tl", "teelöffel" -> GroceryUnit.TSP to null; "el", "esslöffel" -> GroceryUnit.TBSP to null
        "prise", "prisen" -> GroceryUnit.PINCH to null; "bund" -> GroceryUnit.BUNCH to null
        "packung", "pkg", "päckli" -> GroceryUnit.PACKAGE to null; "dose", "dosen" -> GroceryUnit.CAN to null
        "zehe", "zehen" -> GroceryUnit.CLOVE to null; "würfel" -> GroceryUnit.CUBE to null
        "stück", "stk" -> GroceryUnit.PIECE to null
        else -> GroceryUnit.CUSTOM to token.takeIf { it.isNotBlank() }
    }

    private fun decimal(value: String): String {
        val vulgar = mapOf('¼' to ".25", '½' to ".5", '¾' to ".75", '⅓' to ".333", '⅔' to ".667", '⅛' to ".125", '⅜' to ".375", '⅝' to ".625", '⅞' to ".875")
        val fraction = value.lastOrNull()?.let(vulgar::get)
        if (fraction == null) return value.replace(',', '.').toBigDecimal().stripTrailingZeros().toPlainString()
        val whole = value.dropLast(1).trim().toBigDecimalOrNull() ?: BigDecimal.ZERO
        return (whole + fraction.toBigDecimal()).stripTrailingZeros().toPlainString()
    }

    private fun categoryFor(name: String): GroceryCategory {
        val value = name.lowercase()
        return when {
            listOf("tomat", "zwiebel", "knoblauch", "kartoff", "rüebli", "gemüse", "zucchini", "peperoni").any(value::contains) -> GroceryCategory.VEGETABLES
            listOf("apfel", "birne", "zitrone", "frucht", "beere").any(value::contains) -> GroceryCategory.FRUIT
            listOf("milch", "käse", "joghurt", "rahm", "butter", "ei ").any(value::contains) -> GroceryCategory.DAIRY_EGGS
            listOf("fleisch", "poulet", "huhn", "fisch", "lachs").any(value::contains) -> GroceryCategory.MEAT_FISH
            listOf("salz", "pfeffer", "gewürz", "öl", "essig", "sauce").any(value::contains) -> GroceryCategory.SPICES_SAUCES
            listOf("mehl", "reis", "pasta", "couscous", "zucker").any(value::contains) -> GroceryCategory.PANTRY
            else -> GroceryCategory.OTHER
        }
    }

    private fun jsonObjects(element: JsonElement): Sequence<JsonObject> = sequence {
        when (element) {
            is JsonObject -> { yield(element); element["@graph"]?.let { yieldAll(jsonObjects(it)) } }
            is JsonArray -> element.forEach { yieldAll(jsonObjects(it)) }
            else -> Unit
        }
    }
    private fun JsonElement?.hasRecipeType() = when (this) {
        is JsonPrimitive -> contentOrNull == "Recipe"
        is JsonArray -> any { it.jsonPrimitive.contentOrNull == "Recipe" }
        else -> false
    }
    private fun JsonElement.jsonArrayOrNull() = this as? JsonArray
    private fun yieldText(value: JsonElement) = when (value) {
        is JsonPrimitive -> value.contentOrNull
        is JsonArray -> value.firstOrNull()?.jsonPrimitive?.contentOrNull
        else -> null
    }

    private fun servingsFromYield(value: String): Int? {
        val text = value.trim()
        if (!text.matches(Regex("\\d+")) && !text.contains(Regex("Person|Portion", RegexOption.IGNORE_CASE))) return null
        return Regex("\\d+").find(text)?.value?.toIntOrNull()?.takeIf { it > 0 }
    }
}
