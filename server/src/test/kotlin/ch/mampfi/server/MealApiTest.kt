package ch.mampfi.server

import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import java.io.File
import java.sql.DriverManager
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MealApiTest {
    @Test
    fun `dated entries are independent and final deletion removes meal`() = testApplication {
        val dataDirectory = createTempDirectory("mampfi-entry-test-").toFile()
        val uploads = File(dataDirectory, "uploads")
        application { module(File(dataDirectory, "mampfi.db").path, uploads) }

        val firstEntryId = "entry-2026"
        val meal = """{"id":"mac","name":"Mac and cheese","rezeptLink":"https://example.test/recipe","tags":["VEGETARISCH"],"eintraege":[{"id":"$firstEntryId","datum":"2026-03-03","bewertung":{"werte":[8.5,9.0]}}]}"""
        assertEquals(HttpStatusCode.Created, client.post("/api/mahlzeiten") { contentType(ContentType.Application.Json); setBody(meal) }.status)

        val secondEntry = """{"id":"entry-2027","datum":"2027-06-06","bewertung":{"werte":[9.0,9.5]}}"""
        assertEquals(HttpStatusCode.Created, client.post("/api/mahlzeiten/mac/eintraege") { contentType(ContentType.Application.Json); setBody(secondEntry) }.status)
        assertEquals(HttpStatusCode.Conflict, client.post("/api/mahlzeiten/mac/eintraege") { contentType(ContentType.Application.Json); setBody("""{"datum":"2027-06-06"}""") }.status)

        val update = client.put("/api/mahlzeiten/mac") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Mac & cheese","rezeptLink":"https://example.test/new","tags":["VEGETARISCH"]}""")
        }
        assertEquals(HttpStatusCode.OK, update.status)
        assertContains(update.bodyAsText(), "entry-2026")
        assertContains(update.bodyAsText(), "entry-2027")

        val uploaded = client.submitFormWithBinaryData(
            url = "/api/mahlzeiten/mac/eintraege/$firstEntryId/bilder",
            formData = formData {
                append("datei", byteArrayOf(1, 2, 3), Headers.build {
                    append(HttpHeaders.ContentDisposition, "form-data; name=\"datei\"; filename=\"test.jpg\"")
                    append(HttpHeaders.ContentType, ContentType.Image.JPEG.toString())
                })
            },
        )
        assertEquals(HttpStatusCode.OK, uploaded.status)
        val imageUrl = Json.parseToJsonElement(uploaded.bodyAsText()).jsonObject.getValue("url").jsonPrimitive.content
        val imageFile = File(uploads, imageUrl.substringAfterLast('/'))
        assertTrue(imageFile.isFile)

        assertEquals(HttpStatusCode.NoContent, client.delete("/api/mahlzeiten/mac/eintraege/$firstEntryId").status)
        assertFalse(imageFile.exists())
        val remaining = client.get("/api/mahlzeiten/mac")
        assertEquals(HttpStatusCode.OK, remaining.status)
        assertContains(remaining.bodyAsText(), "entry-2027")
        assertFalse(remaining.bodyAsText().contains(firstEntryId))

        assertEquals(HttpStatusCode.NoContent, client.delete("/api/mahlzeiten/mac/eintraege/entry-2027").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/mahlzeiten/mac").status)
    }

    @Test
    fun `entry and shared meal validation is enforced`() = testApplication {
        val dataDirectory = createTempDirectory("mampfi-validation-test-").toFile()
        application { module(File(dataDirectory, "mampfi.db").path, File(dataDirectory, "uploads")) }

        assertEquals(HttpStatusCode.BadRequest, client.post("/api/mahlzeiten") {
            contentType(ContentType.Application.Json); setBody("""{"name":" ","eintraege":[{"datum":"2026-09-01"}]}""")
        }.status)
        val invalidRating = client.post("/api/mahlzeiten") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Pasta","eintraege":[{"datum":"2026-09-01","bewertung":{"werte":[8.5]}}]}""")
        }
        assertEquals(HttpStatusCode.BadRequest, invalidRating.status)
        assertContains(invalidRating.bodyAsText(), "genau zwei Werte")
        assertEquals(HttpStatusCode.BadRequest, client.post("/api/mahlzeiten") {
            contentType(ContentType.Application.Json); setBody("""{"name":"Pasta","eintraege":[]}""")
        }.status)
    }

    @Test
    fun `legacy aggregate rows migrate into dated entries`() {
        val dataDirectory = createTempDirectory("mampfi-migration-test-").toFile()
        val database = File(dataDirectory, "mampfi.db")
        DriverManager.getConnection("jdbc:sqlite:${database.path}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("CREATE TABLE mahlzeiten (id TEXT PRIMARY KEY, name TEXT NOT NULL, rezept_link TEXT, tags TEXT NOT NULL, termine TEXT NOT NULL, bilder TEXT NOT NULL, bewertungen TEXT NOT NULL)")
                statement.executeUpdate("INSERT INTO mahlzeiten VALUES ('mac', 'Mac and cheese', 'https://example.test/recipe', '[\"VEGETARISCH\"]', '[\"2026-03-03\",\"2027-06-06\"]', '[{\"url\":\"/uploads/first.jpg\",\"datum\":\"2026-03-03\"}]', '[{\"werte\":[8.0,9.0],\"datum\":\"2026-03-03\"}]')")
                statement.executeUpdate("INSERT INTO mahlzeiten VALUES ('unused', 'Unused', NULL, '[]', '[]', '[]', '[]')")
            }
        }

        val repository = MealRepository(database.path)
        val migrated = assertNotNull(repository.find("mac"))
        assertEquals("https://example.test/recipe", migrated.rezeptLink)
        assertEquals(2, migrated.eintraege.size)
        assertNotEquals(migrated.eintraege[0].id, migrated.eintraege[1].id)
        assertEquals(listOf("/uploads/first.jpg"), migrated.eintraege.first { it.datum == "2026-03-03" }.bilder.map { it.url })
        assertEquals(listOf(8.0, 9.0), migrated.eintraege.first { it.datum == "2026-03-03" }.bewertung?.werte)
        assertTrue(assertNotNull(repository.find("unused")).eintraege.isEmpty())
        assertTrue(dataDirectory.listFiles().orEmpty().any { it.name.startsWith("mampfi.db.pre-entry-migration-") })

        val reopened = MealRepository(database.path)
        assertEquals(2, assertNotNull(reopened.find("mac")).eintraege.size)
    }
}
