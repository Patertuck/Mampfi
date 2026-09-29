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
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MealApiTest {
    @Test
    fun `dated entries are independent and final deletion removes meal`() = testApplication {
        val dataDirectory = createTempDirectory("mampfi-entry-test-").toFile()
        val uploads = File(dataDirectory, "uploads")
        application { module(File(dataDirectory, "mampfi.db").path, uploads) }

        val firstEntryId = "entry-2026"
        val meal = """{"id":"mac","name":"Mac and cheese","rezeptLink":"https://example.test/recipe","tags":["VEGETARISCH"],"notiz":"  Use more cheddar\nAdd breadcrumbs  ","eintraege":[{"id":"$firstEntryId","datum":"2026-03-03","bewertung":{"werte":[8.5,9.0]}}]}"""
        val created = client.post("/api/mahlzeiten") { contentType(ContentType.Application.Json); setBody(meal) }
        assertEquals(HttpStatusCode.Created, created.status)
        assertEquals("Use more cheddar\nAdd breadcrumbs", Json.decodeFromString<Mahlzeit>(created.bodyAsText()).notiz)

        val secondEntry = """{"id":"entry-2027","datum":"2027-06-06","bewertung":{"werte":[9.0,9.5]}}"""
        assertEquals(HttpStatusCode.Created, client.post("/api/mahlzeiten/mac/eintraege") { contentType(ContentType.Application.Json); setBody(secondEntry) }.status)
        assertEquals(HttpStatusCode.Conflict, client.post("/api/mahlzeiten/mac/eintraege") { contentType(ContentType.Application.Json); setBody("""{"datum":"2027-06-06"}""") }.status)

        val update = client.put("/api/mahlzeiten/mac") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Mac & cheese","rezeptLink":"https://example.test/new","tags":["VEGETARISCH"],"notiz":"Use mature cheddar"}""")
        }
        assertEquals(HttpStatusCode.OK, update.status)
        assertContains(update.bodyAsText(), "entry-2026")
        assertContains(update.bodyAsText(), "entry-2027")
        assertEquals("Use mature cheddar", Json.decodeFromString<Mahlzeit>(update.bodyAsText()).notiz)

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

        val afterUpload = Json.decodeFromString<Mahlzeit>(client.get("/api/mahlzeiten/mac").bodyAsText())
        assertEquals(listOf(imageUrl), afterUpload.eintraege.first { it.id == firstEntryId }.bilder.map { it.url })
        assertTrue(afterUpload.eintraege.first { it.id == "entry-2027" }.bilder.isEmpty())

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
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Pasta","eintraege":[{"datum":"2026-09-01"},{"datum":"2026-09-02"}]}""")
        }.status)
    }

    @Test
    fun `ideas can be created updated and deleted but scheduled meals cannot use idea deletion`() = testApplication {
        val dataDirectory = createTempDirectory("mampfi-idea-test-").toFile()
        application { module(File(dataDirectory, "mampfi.db").path, File(dataDirectory, "uploads")) }

        val created = client.post("/api/mahlzeiten") {
            contentType(ContentType.Application.Json)
            setBody("""{"id":"idea","name":"Ramen","tags":["VEGAN"],"istIdee":true,"eintraege":[]}""")
        }
        assertEquals(HttpStatusCode.Created, created.status)
        assertTrue(Json.decodeFromString<Mahlzeit>(created.bodyAsText()).let { it.istIdee && it.eintraege.isEmpty() })

        val updated = client.put("/api/mahlzeiten/idea") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Spicy ramen","rezeptLink":"https://example.test/ramen","tags":["VEGAN"],"istIdee":true}""")
        }
        assertEquals(HttpStatusCode.OK, updated.status)
        assertContains(updated.bodyAsText(), "Spicy ramen")
        assertEquals(HttpStatusCode.NoContent, client.delete("/api/mahlzeiten/idea").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/mahlzeiten/idea").status)
        assertEquals(HttpStatusCode.NotFound, client.delete("/api/mahlzeiten/idea").status)

        assertEquals(HttpStatusCode.Created, client.post("/api/mahlzeiten") {
            contentType(ContentType.Application.Json)
            setBody("""{"id":"planned","name":"Fondue","eintraege":[{"datum":"2026-12-01"}]}""")
        }.status)
        val markedIdea = client.put("/api/mahlzeiten/planned") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Fondue","istIdee":true}""")
        }
        assertEquals(HttpStatusCode.OK, markedIdea.status)
        assertTrue(Json.decodeFromString<Mahlzeit>(markedIdea.bodyAsText()).let { it.istIdee && it.eintraege.size == 1 })
        assertEquals(HttpStatusCode.Conflict, client.delete("/api/mahlzeiten/planned").status)
        assertEquals(HttpStatusCode.OK, client.get("/api/mahlzeiten/planned").status)
    }

    @Test
    fun `fresh v5 schema is initialized and can be reopened`() {
        val dataDirectory = createTempDirectory("mampfi-schema-test-").toFile()
        val database = File(dataDirectory, "mampfi.db")
        val repository = MealRepository(database.path)
        repository.insert(Mahlzeit(id = "mac", name = "Mac and cheese", eintraege = listOf(MahlzeitEintrag(datum = "2026-03-03")), notiz = "Extra crispy"))

        val reopened = MealRepository(database.path)
        assertEquals("Mac and cheese", reopened.find("mac")?.name)
        assertEquals("Extra crispy", reopened.find("mac")?.notiz)
        DriverManager.getConnection("jdbc:sqlite:${database.path}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA user_version").use { result -> assertTrue(result.next()); assertEquals(5, result.getInt(1)) }
                statement.executeQuery("PRAGMA table_info(mahlzeiten)").use { rows ->
                    val columns = buildSet { while (rows.next()) add(rows.getString("name")) }
                    assertTrue("ist_idee" in columns)
                    assertTrue("notiz" in columns)
                }
                statement.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table'").use { rows ->
                    val tables = buildSet { while (rows.next()) add(rows.getString("name")) }
                    assertTrue(tables.containsAll(setOf("mahlzeiten", "mahlzeit_eintraege", "mahlzeit_bilder", "auswaerts_eintraege")))
                }
            }
        }
    }

    @Test
    fun `v2 meals migrate with idea flag disabled`() {
        val dataDirectory = createTempDirectory("mampfi-v2-schema-test-").toFile()
        val database = File(dataDirectory, "mampfi.db")
        DriverManager.getConnection("jdbc:sqlite:${database.path}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("CREATE TABLE mahlzeiten (id TEXT PRIMARY KEY, name TEXT NOT NULL, rezept_link TEXT, tags TEXT NOT NULL)")
                statement.executeUpdate("INSERT INTO mahlzeiten VALUES ('pasta', 'Pasta', NULL, '[]')")
                statement.executeUpdate("PRAGMA user_version = 2")
            }
        }

        val repository = MealRepository(database.path)
        assertFalse(repository.find("pasta")!!.istIdee)
        DriverManager.getConnection("jdbc:sqlite:${database.path}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA user_version").use { result -> assertTrue(result.next()); assertEquals(5, result.getInt(1)) }
            }
        }
    }

    @Test
    fun `away entry blocks meals and can atomically replace planned occurrences`() = testApplication {
        val dataDirectory = createTempDirectory("mampfi-away-test-").toFile()
        application { module(File(dataDirectory, "mampfi.db").path, File(dataDirectory, "uploads")) }

        val away = """{"id":"away","datum":"2026-10-02","notiz":"Bei Anna"}"""
        assertEquals(HttpStatusCode.Created, client.post("/api/auswaerts") {
            contentType(ContentType.Application.Json); setBody(away)
        }.status)
        assertContains(client.get("/api/auswaerts").bodyAsText(), "Bei Anna")
        assertEquals(HttpStatusCode.Conflict, client.post("/api/mahlzeiten") {
            contentType(ContentType.Application.Json); setBody("""{"id":"blocked","name":"Pasta","eintraege":[{"datum":"2026-10-02"}]}""")
        }.status)

        assertEquals(HttpStatusCode.Created, client.post("/api/mahlzeiten") {
            contentType(ContentType.Application.Json); setBody("""{"id":"planned","name":"Curry","eintraege":[{"datum":"2026-10-03"}]}""")
        }.status)
        assertEquals(HttpStatusCode.Created, client.post("/api/mahlzeiten/planned/eintraege") {
            contentType(ContentType.Application.Json); setBody("""{"datum":"2026-10-04"}""")
        }.status)
        val replacement = """{"id":"replacement","datum":"2026-10-03"}"""
        assertEquals(HttpStatusCode.Conflict, client.post("/api/auswaerts") {
            contentType(ContentType.Application.Json); setBody(replacement)
        }.status)
        assertEquals(HttpStatusCode.Created, client.post("/api/auswaerts?ersetzen=true") {
            contentType(ContentType.Application.Json); setBody(replacement)
        }.status)
        val remainingMeal = client.get("/api/mahlzeiten/planned")
        assertEquals(HttpStatusCode.OK, remainingMeal.status)
        assertFalse(remainingMeal.bodyAsText().contains("2026-10-03"))
        assertContains(remainingMeal.bodyAsText(), "2026-10-04")

        assertEquals(HttpStatusCode.NoContent, client.delete("/api/auswaerts/away").status)
        assertEquals(HttpStatusCode.Created, client.post("/api/mahlzeiten") {
            contentType(ContentType.Application.Json); setBody("""{"id":"unblocked","name":"Pasta","eintraege":[{"datum":"2026-10-02"}]}""")
        }.status)
    }

    @Test
    fun `live backups are verified and retain the newest ten deployment snapshots`() {
        val dataDirectory = createTempDirectory("mampfi-backup-test-").toFile()
        val database = File(dataDirectory, "mampfi.db")
        val repository = MealRepository(database.path)
        repository.insert(Mahlzeit(id = "pasta", name = "Pasta", eintraege = listOf(MahlzeitEintrag(datum = "2026-09-09"))))

        repeat(12) { index ->
            DatabaseBackup.create(database.path, reason = "pre-update", revision = "revision-$index")
        }

        val backups = File(dataDirectory, "backups").listFiles().orEmpty().filter { it.name.startsWith("mampfi-pre-update-") }
        assertEquals(10, backups.size)
        val newest = backups.maxBy { it.name }
        DriverManager.getConnection("jdbc:sqlite:${newest.path}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA integrity_check").use { result -> assertTrue(result.next()); assertEquals("ok", result.getString(1)) }
                statement.executeQuery("SELECT COUNT(*) FROM mahlzeiten").use { result -> assertTrue(result.next()); assertEquals(1, result.getInt(1)) }
            }
        }
    }

    @Test
    fun `legacy v1 schema is rejected without modification`() {
        val dataDirectory = createTempDirectory("mampfi-legacy-schema-test-").toFile()
        val database = File(dataDirectory, "mampfi.db")
        createLegacyDatabase(database)

        val failure = assertFailsWith<IllegalStateException> { MealRepository(database.path) }
        assertContains(failure.message.orEmpty(), "Legacy v1 database schema is no longer supported")

        DriverManager.getConnection("jdbc:sqlite:${database.path}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA table_info(mahlzeiten)").use { rows ->
                    val columns = buildSet { while (rows.next()) add(rows.getString("name")) }
                    assertTrue("termine" in columns)
                    assertFalse("eintraege" in columns)
                }
                statement.executeQuery("PRAGMA user_version").use { result -> assertTrue(result.next()); assertEquals(0, result.getInt(1)) }
                statement.executeQuery("SELECT COUNT(*) FROM mahlzeiten").use { result -> assertTrue(result.next()); assertEquals(1, result.getInt(1)) }
            }
        }
        assertFalse(File(dataDirectory, "backups").exists())
    }

    private fun createLegacyDatabase(database: File) {
        DriverManager.getConnection("jdbc:sqlite:${database.path}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("CREATE TABLE mahlzeiten (id TEXT PRIMARY KEY, name TEXT NOT NULL, rezept_link TEXT, tags TEXT NOT NULL, termine TEXT NOT NULL, bilder TEXT NOT NULL, bewertungen TEXT NOT NULL)")
                statement.executeUpdate("INSERT INTO mahlzeiten VALUES ('legacy', 'Legacy', NULL, '[]', '[\"2026-01-01\"]', '[]', '[]')")
            }
        }
    }
}
