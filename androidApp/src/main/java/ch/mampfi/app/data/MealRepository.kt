package ch.mampfi.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.io.InputStream
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class MealRepository(private val api: MealApi, private val dao: MealDao) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val meals: Flow<List<Mahlzeit>> = dao.observeAll().map { rows -> rows.map { json.decodeFromString(Mahlzeit.serializer(), it.json) } }
    suspend fun refresh(): Result<Unit> = runCatching { cache(api.all()) }
    suspend fun save(meal: Mahlzeit): Result<Mahlzeit> = runCatching {
        val remote = runCatching { api.update(meal.id, meal) }.getOrElse { api.create(meal) }; refresh(); remote
    }
    suspend fun removeDate(id: String, date: String) = runCatching { api.removeDate(id, date); refresh() }
    suspend fun delete(id: String) = runCatching { api.delete(id); refresh() }
    suspend fun upload(name: String, stream: InputStream): Result<String> = runCatching {
        val body = stream.readBytes().toRequestBody("image/*".toMediaType())
        api.upload(MultipartBody.Part.createFormData("datei", name, body)).url
    }
    private suspend fun cache(items: List<Mahlzeit>) { dao.clear(); dao.upsertAll(items.map { MahlzeitEntity(it.id, json.encodeToString(Mahlzeit.serializer(), it)) }) }
}
