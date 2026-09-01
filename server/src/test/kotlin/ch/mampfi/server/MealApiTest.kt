package ch.mampfi.server

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class MealApiTest {
    @Test
    fun `meal lifecycle and validation`() = testApplication {
        val dataDirectory = createTempDirectory("mampfi-test-").toFile()
        application { module(File(dataDirectory, "mampfi.db").path, File(dataDirectory, "uploads")) }

        val emptyName = client.post("/api/mahlzeiten") {
            contentType(ContentType.Application.Json)
            setBody("""{"id":"empty","name":" "}""")
        }
        assertEquals(HttpStatusCode.BadRequest, emptyName.status)
        assertContains(emptyName.bodyAsText(), "Name darf nicht leer sein")

        val singleRating = client.post("/api/mahlzeiten") {
            contentType(ContentType.Application.Json)
            setBody("""{"id":"single-rating","name":"Pasta","bewertungen":[{"werte":[8.5],"datum":"2026-09-01"}]}""")
        }
        assertEquals(HttpStatusCode.BadRequest, singleRating.status)
        assertContains(singleRating.bodyAsText(), "genau zwei Werte")

        val meal = """{"id":"pasta","name":"Pasta","tags":["VEGETARISCH"],"termine":["2026-09-01"],"bewertungen":[{"werte":[8.5,9.0],"datum":"2026-09-01"}]}"""
        assertEquals(HttpStatusCode.Created, client.post("/api/mahlzeiten") {
            contentType(ContentType.Application.Json)
            setBody(meal)
        }.status)
        assertEquals(HttpStatusCode.Conflict, client.post("/api/mahlzeiten") {
            contentType(ContentType.Application.Json)
            setBody(meal)
        }.status)

        val listed = client.get("/api/mahlzeiten")
        assertEquals(HttpStatusCode.OK, listed.status)
        assertContains(listed.bodyAsText(), "Pasta")

        val update = client.put("/api/mahlzeiten/pasta") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Pasta al Limone","termine":["2026-09-01"]}""")
        }
        assertEquals(HttpStatusCode.OK, update.status)
        assertContains(update.bodyAsText(), "Pasta al Limone")

        assertEquals(HttpStatusCode.NoContent, client.delete("/api/mahlzeiten/pasta/termine/2026-09-01").status)
        val withoutDate = client.get("/api/mahlzeiten/pasta")
        assertEquals(HttpStatusCode.OK, withoutDate.status)
        assertContains(withoutDate.bodyAsText(), "\"termine\":[]")

        assertEquals(HttpStatusCode.NoContent, client.delete("/api/mahlzeiten/pasta").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/mahlzeiten/pasta").status)
    }
}
