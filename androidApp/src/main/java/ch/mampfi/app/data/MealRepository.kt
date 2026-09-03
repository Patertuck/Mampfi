package ch.mampfi.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.io.InputStream
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class MealRepository(private val api: MealApi, private val dao: MealDao, private val baseUrl: String) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val meals: Flow<List<Mahlzeit>> = dao.observeAll().map { rows -> rows.map { json.decodeFromString(Mahlzeit.serializer(), it.json).withResolvedImageUrls() } }
    suspend fun refresh(): Result<Unit> = runCatching { cache(api.all()) }
    suspend fun save(meal: Mahlzeit): Result<Mahlzeit> = runCatching {
        val request = meal.withRelativeImageUrls()
        val remote = runCatching { api.update(request.id, request) }.getOrElse { api.create(request) }; refresh(); remote
    }
    suspend fun removeDate(id: String, date: String) = runCatching { api.removeDate(id, date); refresh() }
    suspend fun delete(id: String) = runCatching { api.delete(id); refresh() }
    suspend fun upload(name: String, stream: InputStream): Result<String> = runCatching {
        val body = stream.readBytes().toRequestBody("image/*".toMediaType())
        api.upload(MultipartBody.Part.createFormData("datei", name, body)).url
    }
    private suspend fun cache(items: List<Mahlzeit>) { dao.clear(); dao.upsertAll(items.map { MahlzeitEntity(it.id, json.encodeToString(Mahlzeit.serializer(), it)) }) }
    private fun Mahlzeit.withResolvedImageUrls() = copy(bilder = bilder.map { image -> image.copy(url = if (image.url.startsWith("/")) baseUrl.dropLast(1) + image.url else image.url) })
    private fun Mahlzeit.withRelativeImageUrls() = copy(bilder = bilder.map { image -> image.copy(url = image.url.substringAfter("/uploads/", image.url).let { if (it == image.url) image.url else "/uploads/$it" }) })
}
