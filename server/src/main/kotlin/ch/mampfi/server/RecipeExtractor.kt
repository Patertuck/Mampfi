package ch.mampfi.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.math.BigDecimal
import java.net.URI
import java.util.UUID

@Serializable data class RecipeExtractRequest(val url: String)
@Serializable data class RecipeExtractResponse(
    val source: String, val title: String? = null, val recipeServings: Int? = null,
    val ingredients: List<RecipeIngredient>,
)

interface RecipeExtractor { suspend fun extract(url: String): RecipeExtractResponse }

class SupportedRecipeExtractor(private val client: HttpClient = HttpClient(CIO) {
    followRedirects = false
    install(HttpTimeout) { requestTimeoutMillis = 10_000; connectTimeoutMillis = 5_000; socketTimeoutMillis = 10_000 }
}) : RecipeExtractor {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun extract(url: String): RecipeExtractResponse {
        val uri = url.toRecipeUri()
        val provider = RecipeProvider.forHost(uri.host)
            ?: throw IllegalArgumentException("Dieser Rezept-Link wird noch nicht unterstützt")
        return parse(fetch(uri, provider), provider.source)
    }

    private suspend fun fetch(initialUri: URI, provider: RecipeProvider): String {
        var uri = initialUri
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val response = client.get(uri.toString()) {
                headers.append(HttpHeaders.UserAgent, "Mampfi/1.0 (private recipe planner)")
            }
            if (response.status.isSuccess()) {
                val body = response.bodyAsText()
                require(body.length <= MAX_BODY_LENGTH) { "Rezeptseite ist zu gross" }
                return body
            }
            if (response.status.value !in 300..399) {
                error("${provider.label} antwortete mit ${response.status.value}")
            }
            if (redirectCount == MAX_REDIRECTS) error("Zu viele Weiterleitungen beim Laden des Rezepts")
            val location = response.headers[HttpHeaders.Location]
                ?: error("Ungültige Weiterleitung beim Laden des Rezepts")
            val redirected = runCatching { uri.resolve(location) }
                .getOrElse { throw IllegalArgumentException("Ungültige Weiterleitung beim Laden des Rezepts") }
            require(redirected.scheme.equals("https", ignoreCase = true) && redirected.host?.lowercase() in provider.hosts) {
                "Nicht unterstützte Weiterleitung beim Laden des Rezepts"
            }
            uri = redirected
        }
        error("Rezeptseite konnte nicht geladen werden")
    }

    internal fun parse(html: String, source: String = RecipeProvider.FOOBY.source): RecipeExtractResponse {
        val document = Jsoup.parse(html)
        val recipeJson = document.select("script[type=application/ld+json]").asSequence()
            .mapNotNull { runCatching { json.parseToJsonElement(it.data()) }.getOrNull() }
            .flatMap(::jsonObjects)
            .firstOrNull { node -> node["@type"].hasRecipeType() }

        val structuredIngredients = recipeJson?.get("recipeIngredient")?.jsonArrayOrNull()
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?.takeIf { it.isNotEmpty() }
            ?.mapNotNull(::parseIngredient)
        val ingredients = structuredIngredients ?: if (source == RecipeProvider.KPTNCOOK.source) {
            document.select("[itemprop=ingredients]").mapNotNull(::parseKptnCookIngredient).distinctBy(::ingredientKey)
        } else {
            document.select("[itemprop=recipeIngredient], .recipe-ingredients li, .ingredient-list li, [class*=ingredient] li")
                .map { it.text() }.filter { it.isNotBlank() }.distinct().mapNotNull(::parseIngredient)
        }
        require(ingredients.isNotEmpty()) { "Auf dieser Seite konnten keine Zutaten erkannt werden" }

        val yield = recipeJson?.get("recipeYield")?.let(::yieldText)
            ?: document.selectFirst("[itemprop=recipeYield]")?.text()
        val title = recipeJson?.get("name")?.jsonPrimitive?.contentOrNull
            ?: document.selectFirst("[itemprop=name]")?.text()
            ?: document.selectFirst("h1")?.text()
            ?: document.title().takeIf { it.isNotBlank() }
        return RecipeExtractResponse(
            source = source,
            title = title,
            recipeServings = yield?.let(::servingsFromYield),
            ingredients = ingredients,
        )
    }

    private fun parseKptnCookIngredient(element: Element): RecipeIngredient? {
        val lines = element.wholeText().lineSequence()
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotBlank() }
            .toList()
        if (lines.size < 2) return parseIngredient(element.text())

        val measurement = MEASUREMENT.matchEntire(lines.first()) ?: return parseIngredient(element.text())
        val name = lines.drop(1).joinToString(" ").trim()
        val amount = decimal(measurement.groupValues[1])
        val unitToken = measurement.groupValues[2].trim()
        val unit = if (unitToken.isBlank()) GroceryUnit.PIECE to null else unitFor(unitToken)
        return ingredient(name, amount, unit.first, unit.second)
    }

    private fun parseIngredient(raw: String): RecipeIngredient? {
        val text = raw.trim().replace(Regex("\\s+"), " ")
        val match = INGREDIENT.matchEntire(text)
            ?: return ingredient(text, null, GroceryUnit.CUSTOM, "nach Bedarf")
        val amount = decimal(match.groupValues[1])
        val remainder = match.groupValues[2].trim()
        if (remainder.isBlank()) return null

        val unitToken = remainder.substringBefore(' ')
        val remainderAfterUnit = remainder.substringAfter(' ', "").trim()
        val recognizedUnit = unitForOrNull(unitToken)
        return if (recognizedUnit != null && remainderAfterUnit.isNotBlank()) {
            ingredient(remainderAfterUnit, amount, recognizedUnit.first, recognizedUnit.second)
        } else {
            ingredient(remainder, amount, GroceryUnit.PIECE, null)
        }
    }

    private fun ingredient(
        name: String,
        amount: String?,
        unit: GroceryUnit,
        customUnit: String?,
    ): RecipeIngredient? = name.takeIf { it.isNotBlank() }?.let {
        RecipeIngredient(
            id = UUID.randomUUID().toString(), name = it, amount = amount,
            unit = unit, customUnit = customUnit, category = categoryFor(it),
        )
    }

    private fun ingredientKey(ingredient: RecipeIngredient) = listOf(
        ingredient.name.lowercase(), ingredient.amount.orEmpty(), ingredient.unit.name, ingredient.customUnit.orEmpty(),
    ).joinToString("|")

    private fun unitFor(token: String): Pair<GroceryUnit, String?> =
        unitForOrNull(token) ?: (GroceryUnit.CUSTOM to token.takeIf { it.isNotBlank() })

    private fun unitForOrNull(token: String): Pair<GroceryUnit, String?>? {
        val normalized = token.trim().lowercase().trimEnd('.').replace("(n)", "n").replace("(e)", "e")
        return when (normalized) {
            "g", "gr" -> GroceryUnit.G to null; "kg" -> GroceryUnit.KG to null
            "ml" -> GroceryUnit.ML to null; "dl" -> GroceryUnit.DL to null; "l" -> GroceryUnit.L to null
            "tl", "teelöffel" -> GroceryUnit.TSP to null; "el", "esslöffel" -> GroceryUnit.TBSP to null
            "prise", "prisen" -> GroceryUnit.PINCH to null; "bund" -> GroceryUnit.BUNCH to null
            "packung", "pkg", "päckli" -> GroceryUnit.PACKAGE to null; "dose", "dosen" -> GroceryUnit.CAN to null
            "zehe", "zehen" -> GroceryUnit.CLOVE to null; "würfel" -> GroceryUnit.CUBE to null
            "stück", "stk" -> GroceryUnit.PIECE to null
            "zweig", "zweige", "blatt", "blätter", "scheibe", "scheiben", "handvoll", "becher" ->
                GroceryUnit.CUSTOM to token
            else -> null
        }
    }

    private fun decimal(value: String): String {
        val normalized = value.trim()
        val vulgar = mapOf('¼' to ".25", '½' to ".5", '¾' to ".75", '⅓' to ".333", '⅔' to ".667", '⅛' to ".125", '⅜' to ".375", '⅝' to ".625", '⅞' to ".875")
        val fraction = normalized.lastOrNull()?.let(vulgar::get)
        if (fraction != null) {
            val whole = normalized.dropLast(1).trim().toBigDecimalOrNull() ?: BigDecimal.ZERO
            return (whole + fraction.toBigDecimal()).stripTrailingZeros().toPlainString()
        }
        val slashFraction = SLASH_FRACTION.matchEntire(normalized)
        if (slashFraction != null) {
            val whole = slashFraction.groupValues[1].toBigDecimalOrNull() ?: BigDecimal.ZERO
            val numerator = slashFraction.groupValues[2].toBigDecimal()
            val denominator = slashFraction.groupValues[3].toBigDecimal()
            require(denominator.signum() != 0) { "Ungültige Mengenangabe" }
            return whole.add(numerator.divide(denominator, 6, java.math.RoundingMode.HALF_UP))
                .stripTrailingZeros().toPlainString()
        }
        return normalized.replace(',', '.').toBigDecimal().stripTrailingZeros().toPlainString()
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

    private fun String.toRecipeUri(): URI {
        val uri = runCatching { URI(this) }.getOrElse { throw IllegalArgumentException("Ungültiger Rezept-Link") }
        require(uri.scheme.equals("https", ignoreCase = true) && uri.host != null) { "Ungültiger Rezept-Link" }
        return uri
    }

    private enum class RecipeProvider(val source: String, val label: String, val hosts: Set<String>) {
        FOOBY("FOOBY", "FOOBY", setOf("fooby.ch", "www.fooby.ch")),
        KPTNCOOK("KPTNCOOK", "KptnCook", setOf("share.kptncook.com", "mobile.kptncook.com"));

        companion object {
            fun forHost(host: String?) = entries.firstOrNull { host?.lowercase() in it.hosts }
        }
    }

    private companion object {
        const val MAX_REDIRECTS = 5
        const val MAX_BODY_LENGTH = 2_000_000
        const val AMOUNT = "(?:[0-9]+\\s+[0-9]+/[0-9]+|[0-9]+/[0-9]+|[0-9]+\\s+[¼½¾⅓⅔⅛⅜⅝⅞]|[0-9]+(?:[.,][0-9]+)?|[¼½¾⅓⅔⅛⅜⅝⅞])"
        val INGREDIENT = Regex("^($AMOUNT)(?:\\s+(.+))?$")
        val MEASUREMENT = Regex("^($AMOUNT)(?:\\s+(.+))?$")
        val SLASH_FRACTION = Regex("^(?:(\\d+)\\s+)?(\\d+)/(\\d+)$")
    }
}

typealias FoobyRecipeExtractor = SupportedRecipeExtractor
