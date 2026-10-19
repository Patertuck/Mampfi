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
    @Test fun `search combines reusable and external suggestions`() = testApplication {
        val directory = createTempDirectory("mampfi-grocery-api").toFile()
        val external = GrocerySuggestion(
            GroceryProductRef(GrocerySource.OPEN_FOOD_FACTS, "off:123", "Bio Äpfel", barcode = "123"),
            GroceryCategory.FRUIT,
        )
        application { module(File(directory, "mampfi.db").path, File(directory, "uploads"), object : GroceryCatalogProvider {
            override suspend fun search(query: String) = listOf(external)
            override suspend fun lookupBarcode(barcode: String) = GroceryBarcodeLookup.Found(external)
        }) }

        val response = client.post("/api/einkauf/suche") {
            contentType(ContentType.Application.Json)
            setBody("""{"query":"Äpfel"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(listOf(external), Json.decodeFromString<List<GrocerySuggestion>>(response.bodyAsText()))
    }

    @Test fun `barcode lookup returns exact external product`() = testApplication {
        val directory = createTempDirectory("mampfi-grocery-barcode-api").toFile()
        val external = GrocerySuggestion(
            GroceryProductRef(GrocerySource.OPEN_FOOD_FACTS, "off:761020001", "Vollmilch", barcode = "761020001"),
            GroceryCategory.DAIRY_EGGS,
        )
        application { module(File(directory, "mampfi.db").path, File(directory, "uploads"), object : GroceryCatalogProvider {
            override suspend fun search(query: String) = emptyList<GrocerySuggestion>()
            override suspend fun lookupBarcode(barcode: String) = GroceryBarcodeLookup.Found(external)
        }) }

        val response = client.get("/api/einkauf/produkt/761020001")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(external, Json.decodeFromString<GrocerySuggestion>(response.bodyAsText()))
    }
}
