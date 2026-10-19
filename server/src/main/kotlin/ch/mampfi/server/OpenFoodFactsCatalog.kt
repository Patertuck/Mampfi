package ch.mampfi.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.serialization.json.*
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

interface GroceryCatalogProvider {
    suspend fun search(query: String): List<GrocerySuggestion>
}

class OpenFoodFactsCatalog(
    private val endpoint: String = "https://search.openfoodfacts.org/search",
    private val client: HttpClient = HttpClient(CIO),
) : GroceryCatalogProvider {
    private data class CacheEntry(val createdAt: Instant, val results: List<GrocerySuggestion>)
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val requestTimes = ArrayDeque<Instant>()

    override suspend fun search(query: String): List<GrocerySuggestion> {
        val normalized = query.trim().lowercase()
        if (normalized.length < 3) return emptyList()
        cache[normalized]?.takeIf { it.createdAt.isAfter(Instant.now().minusSeconds(86_400)) }?.let { return it.results }
        synchronized(requestTimes) {
            val cutoff = Instant.now().minusSeconds(60)
            while (requestTimes.firstOrNull()?.isBefore(cutoff) == true) requestTimes.removeFirst()
            if (requestTimes.size >= 8) return emptyList()
            requestTimes.addLast(Instant.now())
        }
        return runCatching {
            val response = client.post(endpoint) {
                contentType(ContentType.Application.Json)
                userAgent("Mampfi/1.0 (private grocery planner; Open Food Facts integration)")
                setBody(buildJsonObject {
                    put("q", query.trim())
                    put("page_size", 12)
                    put("boost_phrase", true)
                    putJsonArray("langs") { listOf("de", "fr", "it", "en").forEach(::add) }
                    putJsonArray("fields") {
                        listOf("code", "product_name", "product_name_de", "product_name_fr", "product_name_it", "brands", "quantity", "image_front_url", "categories_tags").forEach(::add)
                    }
                }.toString())
            }
            if (!response.status.isSuccess()) error("Open Food Facts search returned ${response.status}")
            parse(response.bodyAsText()).also { cache[normalized] = CacheEntry(Instant.now(), it) }
        }.getOrDefault(emptyList())
    }

    internal fun parse(body: String): List<GrocerySuggestion> {
        val root = json.parseToJsonElement(body).jsonObject
        val hitValue = root["hits"]
        val hits = when (hitValue) {
            is JsonArray -> hitValue
            is JsonObject -> hitValue["hits"] as? JsonArray ?: JsonArray(emptyList())
            else -> root["products"] as? JsonArray ?: JsonArray(emptyList())
        }
        return hits.mapNotNull { hit ->
            val wrapper = hit.jsonObject
            val source = (wrapper["_source"] ?: hit).jsonObject
            val code = source.string("code") ?: return@mapNotNull null
            val name = sequenceOf("product_name_de", "product_name", "product_name_fr", "product_name_it")
                .mapNotNull { key -> source.string(key) }.firstOrNull { it.isNotBlank() } ?: return@mapNotNull null
            val categories = source["categories_tags"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
            GrocerySuggestion(
                GroceryProductRef(
                    source = GrocerySource.OPEN_FOOD_FACTS,
                    stableKey = "off:$code",
                    name = name,
                    barcode = code,
                    brand = source.string("brands"),
                    packageSize = source.string("quantity"),
                    imageUrl = source.string("image_front_url"),
                ),
                mapCategory(categories),
            )
        }.distinctBy { it.product.stableKey }
    }

    private fun mapCategory(tags: List<String>): GroceryCategory {
        val normalizedTags = tags.map { it.substringAfter(':').lowercase() }
        if (normalizedTags.any { ("vegetable" in it || "gemüse" in it || "gemuse" in it || "legume" in it || "légume" in it) && it !in setOf("fruits-and-vegetables", "fruit-and-vegetable-products") }) return GroceryCategory.VEGETABLES
        if (normalizedTags.any { ("fruit" in it || "obst" in it || "frucht" in it) && it !in setOf("fruits-and-vegetables", "fruit-and-vegetable-products") }) return GroceryCategory.FRUIT
        val text = normalizedTags.joinToString(" ")
        val rules = listOf(
            GroceryCategory.BAKERY to listOf("bread", "bakery", "brot", "backware"),
            GroceryCategory.DAIRY_EGGS to listOf("dair", "milk", "cheese", "yogurt", "egg", "milch", "käse", "eier"),
            GroceryCategory.MEAT_FISH to listOf("meat", "fish", "fleisch", "fisch"),
            GroceryCategory.FROZEN to listOf("frozen", "tiefkuhl", "tiefkühl"),
            GroceryCategory.DRINKS to listOf("beverage", "drink", "getrank", "getränk"),
            GroceryCategory.SWEETS_SNACKS to listOf("snack", "sweet", "chocolate", "candy", "suss", "süss"),
            GroceryCategory.SPICES_SAUCES to listOf("spice", "sauce", "gewurz", "gewürz"),
            GroceryCategory.CHILLED to listOf("refrigerated", "chilled", "fresh-food"),
            GroceryCategory.HOUSEHOLD_HYGIENE to listOf("cleaning", "hygiene", "household"),
        )
        return rules.firstOrNull { (_, words) -> words.any(text::contains) }?.first ?: GroceryCategory.OTHER
    }

    private fun JsonObject.string(key: String) = get(key)?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
}
