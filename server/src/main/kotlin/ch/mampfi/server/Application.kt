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

@Serializable data class MahlzeitBild(val id: String = UUID.randomUUID().toString(), val url: String)
@Serializable data class MahlzeitBewertung(val werte: List<Double>)
@Serializable data class MahlzeitEintrag(
    val id: String = UUID.randomUUID().toString(), val datum: String,
    val bilder: List<MahlzeitBild> = emptyList(), val bewertung: MahlzeitBewertung? = null,
)
@Serializable data class Mahlzeit(
    val id: String = UUID.randomUUID().toString(), val name: String,
    val rezeptLink: String? = null, val tags: List<String> = emptyList(),
    val istIdee: Boolean = false,
    val eintraege: List<MahlzeitEintrag> = emptyList(),
)

fun main(args: Array<String>) {
    if (args.singleOrNull() == "backup-before-update") {
        val databasePath = System.getenv("DATABASE_URL") ?: "mampfi.db"
        val backupDirectory = System.getenv("BACKUP_DIR")?.let(::File) ?: DatabaseBackup.defaultDirectory(databasePath)
        val backup = DatabaseBackup.create(databasePath, backupDirectory, reason = "pre-update")
        println("Created verified SQLite backup: ${backup.absolutePath}")
        return
    }
    embeddedServer(Netty, port = System.getenv("PORT")?.toIntOrNull() ?: 8080) { module() }.start(wait = true)
}

fun Application.module(
    databasePath: String = System.getenv("DATABASE_URL") ?: "mampfi.db",
    uploadDirectory: File = File(System.getenv("UPLOAD_DIR") ?: "uploads"),
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
            get("/{id}") { repository.find(call.parameters["id"]!!)?.let { call.respond(it) } ?: call.respond(HttpStatusCode.NotFound) }
            post {
                val meal = call.receive<Mahlzeit>()
                validateNewMeal(meal)?.let { call.respond(HttpStatusCode.BadRequest, it); return@post }
                if (repository.find(meal.id) != null) call.respond(HttpStatusCode.Conflict) else try {
                    repository.insert(meal)
                    call.respond(HttpStatusCode.Created, repository.find(meal.id)!!)
                } catch (error: java.sql.SQLException) {
                    if (error.isConflict()) call.respond(HttpStatusCode.Conflict) else throw error
                }
            }
            put("/{id}") {
                val id = call.parameters["id"]!!
                val meal = call.receive<Mahlzeit>().copy(id = id)
                validateShared(meal)?.let { call.respond(HttpStatusCode.BadRequest, it); return@put }
                if (repository.updateMeal(meal)) call.respond(repository.find(id)!!) else call.respond(HttpStatusCode.NotFound)
            }
            delete("/{id}") {
                when (repository.deleteIdea(call.parameters["id"]!!)) {
                    DeleteIdeaResult.DELETED -> call.respond(HttpStatusCode.NoContent)
                    DeleteIdeaResult.NOT_FOUND -> call.respond(HttpStatusCode.NotFound)
                    DeleteIdeaResult.HAS_ENTRIES -> call.respond(HttpStatusCode.Conflict, mapOf("fehler" to "Nur Ideen ohne Termine können gelöscht werden"))
                }
            }
            post("/{id}/eintraege") {
                val mealId = call.parameters["id"]!!
                val entry = call.receive<MahlzeitEintrag>()
                validateEntry(entry)?.let { call.respond(HttpStatusCode.BadRequest, it); return@post }
                try {
                    if (repository.insertEntry(mealId, entry)) call.respond(HttpStatusCode.Created, entry) else call.respond(HttpStatusCode.NotFound)
                } catch (error: java.sql.SQLException) {
                    if (error.isConflict()) call.respond(HttpStatusCode.Conflict) else throw error
                }
            }
            put("/{id}/eintraege/{entryId}") {
                val mealId = call.parameters["id"]!!
                val entryId = call.parameters["entryId"]!!
                val entry = call.receive<MahlzeitEintrag>().copy(id = entryId)
                validateEntry(entry)?.let { call.respond(HttpStatusCode.BadRequest, it); return@put }
                try {
                    if (repository.updateEntry(mealId, entry)) {
                        call.respond(repository.find(mealId)!!.eintraege.first { it.id == entryId })
                    } else call.respond(HttpStatusCode.NotFound)
                } catch (error: java.sql.SQLException) {
                    if (error.isConflict()) call.respond(HttpStatusCode.Conflict) else throw error
                }
            }
            delete("/{id}/eintraege/{entryId}") {
                val deleted = repository.deleteEntry(call.parameters["id"]!!, call.parameters["entryId"]!!)
                    ?: run { call.respond(HttpStatusCode.NotFound); return@delete }
                deleted.imageUrls.forEach { deleteUploadedFile(uploads, it) }
                call.respond(HttpStatusCode.NoContent)
            }
            post("/{id}/eintraege/{entryId}/bilder") {
                val part = call.receiveMultipart().readPart() as? PartData.FileItem
                    ?: run { call.respond(HttpStatusCode.BadRequest, mapOf("fehler" to "Bilddatei fehlt")); return@post }
                val image = saveImage(part, uploads)
                if (repository.addImage(call.parameters["id"]!!, call.parameters["entryId"]!!, image)) call.respond(image) else {
                    deleteUploadedFile(uploads, image.url)
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }
    }
}

private fun validateShared(meal: Mahlzeit): Map<String, String>? = when {
    meal.name.isBlank() -> mapOf("fehler" to "Name darf nicht leer sein")
    "VEGAN" in meal.tags && "VEGETARISCH" in meal.tags -> mapOf("fehler" to "Eine Mahlzeit kann nicht gleichzeitig vegan und vegetarisch sein")
    else -> null
}

private fun validateNewMeal(meal: Mahlzeit): Map<String, String>? =
    validateShared(meal) ?: when {
        meal.eintraege.isEmpty() && !meal.istIdee -> mapOf("fehler" to "Eine Mahlzeit ohne Termin muss als Idee markiert sein")
        meal.eintraege.size > 1 -> mapOf("fehler" to "Eine neue Mahlzeit darf höchstens einen Eintrag haben")
        meal.eintraege.map { it.datum }.distinct().size != meal.eintraege.size -> mapOf("fehler" to "Termin darf nicht doppelt vorkommen")
        else -> meal.eintraege.firstNotNullOfOrNull(::validateEntry)
    }

private fun validateEntry(entry: MahlzeitEintrag): Map<String, String>? = when {
    runCatching { LocalDate.parse(entry.datum) }.isFailure -> mapOf("fehler" to "Ungültiges Datum")
    entry.bewertung?.werte?.size?.let { it != 2 } == true -> mapOf("fehler" to "Jede Bewertung braucht genau zwei Werte")
    entry.bewertung?.werte?.any { it !in 1.0..10.0 } == true -> mapOf("fehler" to "Bewertung muss zwischen 1 und 10 liegen")
    else -> null
}

private suspend fun saveImage(part: PartData.FileItem, uploads: File): MahlzeitBild {
    val extension = part.originalFileName?.substringAfterLast('.', "jpg")?.lowercase()?.takeIf { it.matches(Regex("[a-z0-9]{1,5}")) } ?: "jpg"
    val filename = "${UUID.randomUUID()}.$extension"
    part.provider().copyAndClose(File(uploads, filename).writeChannel())
    part.dispose()
    return MahlzeitBild(url = "/uploads/$filename")
}

private fun deleteUploadedFile(uploads: File, url: String) {
    val filename = url.substringAfterLast('/').takeIf { it.isNotBlank() } ?: return
    File(uploads, filename).takeIf { it.parentFile.canonicalFile == uploads.canonicalFile }?.delete()
}

private fun java.sql.SQLException.isConflict() = message?.contains("UNIQUE constraint failed", ignoreCase = true) == true
