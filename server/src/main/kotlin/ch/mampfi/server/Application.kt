package ch.mampfi.server

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.util.UUID

@Serializable data class MahlzeitBild(val url: String, val datum: String)
@Serializable data class MahlzeitBewertung(val werte: List<Double>, val datum: String)
@Serializable data class Mahlzeit(
    val id: String = UUID.randomUUID().toString(), val name: String,
    val rezeptLink: String? = null, val tags: List<String> = emptyList(),
    val termine: List<String> = emptyList(), val bilder: List<MahlzeitBild> = emptyList(),
    val bewertungen: List<MahlzeitBewertung> = emptyList()
)

fun main() {
    embeddedServer(Netty, port = System.getenv("PORT")?.toIntOrNull() ?: 8080) { module() }.start(wait = true)
}

fun Application.module(
    databasePath: String = System.getenv("DATABASE_URL") ?: "mampfi.db",
    uploadDirectory: File = File(System.getenv("UPLOAD_DIR") ?: "uploads")
) {
    val uploads = uploadDirectory.apply { mkdirs() }
    val repository = MealRepository(databasePath)
    val logger = environment.log
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
    install(CORS) { anyHost(); allowMethod(HttpMethod.Delete); allowMethod(HttpMethod.Put); allowHeader(HttpHeaders.ContentType) }
    install(StatusPages) { exception<Throwable> { call, cause ->
        logger.error("Unerwarteter Fehler", cause)
        call.respond(HttpStatusCode.InternalServerError, mapOf("fehler" to "Serverfehler"))
    } }
    routing {
        staticFiles("/uploads", uploads)
        route("/api/mahlzeiten") {
            get { call.respond(repository.all()) }
            get("/{id}") { repository.find(call.parameters["id"]!!)?.let { call.respond(it) }
                ?: call.respond(HttpStatusCode.NotFound) }
            post { val meal = call.receive<Mahlzeit>(); validate(meal)?.let { call.respond(HttpStatusCode.BadRequest, it); return@post }
                if (repository.find(meal.id) != null) call.respond(HttpStatusCode.Conflict) else { repository.insert(meal); call.respond(HttpStatusCode.Created, meal) } }
            put("/{id}") { val id = call.parameters["id"]!!; val meal = call.receive<Mahlzeit>().copy(id = id)
                validate(meal)?.let { call.respond(HttpStatusCode.BadRequest, it); return@put }
                if (repository.update(meal)) call.respond(meal) else call.respond(HttpStatusCode.NotFound) }
            delete("/{id}") { val meal = repository.find(call.parameters["id"]!!)
                if (meal == null) call.respond(HttpStatusCode.NotFound) else {
                    meal.bilder.mapNotNull { it.url.substringAfterLast('/').takeIf { n -> n.isNotBlank() } }.forEach { File(uploads, it).delete() }
                    repository.delete(meal.id); call.respond(HttpStatusCode.NoContent)
                }
            }
            delete("/{id}/termine/{datum}") { val id = call.parameters["id"]!!; val date = call.parameters["datum"]!!
                val meal = repository.find(id) ?: run { call.respond(HttpStatusCode.NotFound); return@delete }
                repository.update(meal.copy(termine = meal.termine.filterNot { it == date })); call.respond(HttpStatusCode.NoContent)
            }
        }
        post("/api/bilder") {
            val part = call.receiveMultipart().readPart() as? PartData.FileItem
                ?: run { call.respond(HttpStatusCode.BadRequest, mapOf("fehler" to "Bilddatei fehlt")); return@post }
            val extension = part.originalFileName?.substringAfterLast('.', "jpg")?.lowercase()?.takeIf { it.matches(Regex("[a-z0-9]{1,5}")) } ?: "jpg"
            val filename = "${UUID.randomUUID()}.$extension"
            part.provider().copyAndClose(File(uploads, filename).writeChannel())
            part.dispose()
            call.respond(mapOf("url" to "/uploads/$filename"))
        }
    }
}

private fun validate(meal: Mahlzeit): Map<String, String>? = when {
    meal.name.isBlank() -> mapOf("fehler" to "Name darf nicht leer sein")
    meal.termine.any { runCatching { LocalDate.parse(it) }.isFailure } -> mapOf("fehler" to "Ungültiges Datum")
    meal.termine.distinct().size != meal.termine.size -> mapOf("fehler" to "Termin darf nicht doppelt vorkommen")
    meal.bewertungen.any { it.werte.size != 2 } -> mapOf("fehler" to "Jede Bewertung braucht genau zwei Werte")
    meal.bewertungen.any { bewertung -> bewertung.werte.any { it !in 1.0..10.0 } } -> mapOf("fehler" to "Bewertung muss zwischen 1 und 10 liegen")
    else -> null
}
