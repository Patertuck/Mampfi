package ch.mampfi.server

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class GroceryApiTest {
    @Test fun `search returns remembered suggestions only`() = testApplication {
        val directory = createTempDirectory("mampfi-grocery-api").toFile()
        val database = File(directory, "mampfi.db")
        val remembered = GrocerySuggestion(
            GroceryProductRef(GrocerySource.MANUAL, "manual:bio-aepfel", "Bio Aepfel"),
            GroceryCategory.FRUIT,
        )
        GroceryRepository(database.path).sync(GrocerySyncRequest("phone", 0, listOf(GroceryMutation(
            "add-apples", GroceryMutationType.ADD, "apples", "2026-11-01T12:00:00Z",
            GroceryItem("apples", remembered.product, remembered.category, 1.0, GroceryDimension.COUNT, GroceryUnit.PIECE, createdAt = "2026-11-01T12:00:00Z"),
        ))))
        application { module(database.path, File(directory, "uploads"), object : GroceryCatalogProvider {
            override suspend fun lookupBarcode(barcode: String) = GroceryBarcodeLookup.NotFound
        }) }

        val response = client.post("/api/einkauf/suche") {
            contentType(ContentType.Application.Json)
            setBody("""{"query":"Aepfel"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(listOf(remembered), Json.decodeFromString<List<GrocerySuggestion>>(response.bodyAsText()))
    }

    @Test fun `barcode lookup returns exact external product`() = testApplication {
        val directory = createTempDirectory("mampfi-grocery-barcode-api").toFile()
        val external = GrocerySuggestion(
            GroceryProductRef(GrocerySource.OPEN_FOOD_FACTS, "off:761020001", "Vollmilch", barcode = "761020001"),
            GroceryCategory.DAIRY_EGGS,
        )
        application { module(File(directory, "mampfi.db").path, File(directory, "uploads"), object : GroceryCatalogProvider {
            override suspend fun lookupBarcode(barcode: String) = GroceryBarcodeLookup.Found(external)
        }) }

        val response = client.get("/api/einkauf/produkt/761020001")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(external, Json.decodeFromString<GrocerySuggestion>(response.bodyAsText()))
    }
}
