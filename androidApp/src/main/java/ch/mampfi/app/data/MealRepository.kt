package ch.mampfi.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.io.InputStream
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class MealRepository(private val api: MealApi, private val dao: MealDao, private val awayDao: AwayEntryDao, private val baseUrl: String) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val meals: Flow<List<Mahlzeit>> = dao.observeAll().map { rows -> rows.map { json.decodeFromString(Mahlzeit.serializer(), it.json).withResolvedImageUrls() } }
    val awayEntries: Flow<List<AuswaertsEintrag>> = awayDao.observeAll().map { rows -> rows.map { AuswaertsEintrag(it.id, it.datum, it.notiz) } }
    suspend fun refresh(): Result<Unit> = runCatching {
        val meals = api.all()
        val awayEntries = api.allAwayEntries()
        cache(meals)
        cacheAwayEntries(awayEntries)
    }
    suspend fun createAwayEntry(entry: AuswaertsEintrag, replaceMeals: Boolean): Result<Unit> = runCatching {
        api.createAwayEntry(replaceMeals, entry)
        refresh().getOrThrow()
    }
    suspend fun updateAwayEntry(entry: AuswaertsEintrag, replaceMeals: Boolean): Result<Unit> = runCatching {
        api.updateAwayEntry(entry.id, replaceMeals, entry)
        refresh().getOrThrow()
    }
    suspend fun deleteAwayEntry(id: String): Result<Unit> = runCatching {
        api.deleteAwayEntry(id)
        refresh().getOrThrow()
    }
    suspend fun createMeal(meal: Mahlzeit, entry: MahlzeitEintrag): Result<Unit> = runCatching {
        api.create(meal.copy(istIdee = false, eintraege = listOf(entry)).withRelativeImageUrls())
        refresh().getOrThrow()
    }
    suspend fun createIdea(meal: Mahlzeit): Result<Unit> = runCatching {
        api.create(meal.copy(istIdee = true, eintraege = emptyList()).withRelativeImageUrls())
        refresh().getOrThrow()
    }
    suspend fun updateIdea(meal: Mahlzeit): Result<Unit> = runCatching {
        api.update(meal.id, meal.copy(istIdee = true).withRelativeImageUrls())
        refresh().getOrThrow()
    }
    suspend fun updateMeal(meal: Mahlzeit): Result<Unit> = runCatching {
        api.update(meal.id, meal.withRelativeImageUrls())
        refresh().getOrThrow()
    }
    suspend fun setIdea(meal: Mahlzeit, isIdea: Boolean): Result<Unit> = runCatching {
        api.update(meal.id, meal.copy(istIdee = isIdea).withRelativeImageUrls())
        refresh().getOrThrow()
    }
    suspend fun deleteIdea(mealId: String): Result<Unit> = runCatching {
        api.deleteIdea(mealId)
        refresh().getOrThrow()
    }
    suspend fun createEntry(meal: Mahlzeit, entry: MahlzeitEintrag): Result<Unit> = runCatching {
        api.update(meal.id, meal.copy(istIdee = false).withRelativeImageUrls())
        api.createEntry(meal.id, entry.withRelativeImageUrls())
        refresh().getOrThrow()
    }
    suspend fun updateEntry(meal: Mahlzeit, entry: MahlzeitEintrag): Result<Unit> = runCatching {
        api.update(meal.id, meal.withRelativeImageUrls())
        api.updateEntry(meal.id, entry.id, entry.withRelativeImageUrls())
        refresh().getOrThrow()
    }
    suspend fun deleteEntry(mealId: String, entryId: String) = runCatching { api.deleteEntry(mealId, entryId); refresh().getOrThrow() }
    suspend fun upload(mealId: String, entryId: String, name: String, stream: InputStream): Result<MahlzeitBild> = runCatching {
        val body = stream.readBytes().toRequestBody("image/*".toMediaType())
        api.upload(mealId, entryId, MultipartBody.Part.createFormData("datei", name, body)).withResolvedImageUrl()
            .also { refresh().getOrThrow() }
    }
    private suspend fun cache(items: List<Mahlzeit>) { dao.clear(); dao.upsertAll(items.map { MahlzeitEntity(it.id, json.encodeToString(Mahlzeit.serializer(), it)) }) }
    private suspend fun cacheAwayEntries(items: List<AuswaertsEintrag>) {
        awayDao.clear()
        awayDao.upsertAll(items.map { AuswaertsEintragEntity(it.id, it.datum, it.notiz) })
    }
    private fun MahlzeitBild.withResolvedImageUrl() = copy(url = if (url.startsWith("/")) baseUrl.dropLast(1) + url else url)
    private fun MahlzeitBild.withRelativeImageUrl() = copy(url = url.substringAfter("/uploads/", url).let { if (it == url) url else "/uploads/$it" })
    private fun MahlzeitEintrag.withResolvedImageUrls() = copy(bilder = bilder.map { it.withResolvedImageUrl() })
    private fun MahlzeitEintrag.withRelativeImageUrls() = copy(bilder = bilder.map { it.withRelativeImageUrl() })
    private fun Mahlzeit.withResolvedImageUrls() = copy(eintraege = eintraege.map { it.withResolvedImageUrls() })
    private fun Mahlzeit.withRelativeImageUrls() = copy(eintraege = eintraege.map { it.withRelativeImageUrls() })
}
