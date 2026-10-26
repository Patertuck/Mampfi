package ch.mampfi.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID
import java.io.InputStream
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

sealed interface GroceryBarcodeResult {
    data class Found(val suggestion: GrocerySuggestion) : GroceryBarcodeResult
    data object NotFound : GroceryBarcodeResult
    data object Unavailable : GroceryBarcodeResult
}

class GroceryRepository(
    private val api: MealApi,
    private val dao: GroceryDao,
    private val deviceId: String,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val syncMutex = Mutex()
    val items: Flow<List<GroceryItem>> = dao.observeAll().map { rows ->
        rows.map { json.decodeFromString<GroceryItem>(it.json) }.filterNot { it.deleted }
    }

    suspend fun add(suggestion: GrocerySuggestion, baseQuantity: Double, unit: GroceryUnit, customUnit: String? = null, quantitySpecified: Boolean = true): Result<Unit> = runCatching {
        addLocal(suggestion, baseQuantity, unit, customUnit, quantitySpecified)
        synchronize()
    }

    suspend fun addAll(items: List<RecipeGrocerySelection>): Result<Unit> = runCatching {
        items.forEach { addLocal(it.suggestion, it.baseQuantity, it.unit, it.customUnit, it.quantitySpecified) }
        synchronize().getOrThrow()
    }

    private suspend fun addLocal(suggestion: GrocerySuggestion, baseQuantity: Double, unit: GroceryUnit, customUnit: String?, quantitySpecified: Boolean) {
        require(!quantitySpecified || baseQuantity > 0)
        val now = Instant.now().toString()
        val dimension = unit.dimension()
        val all = decodedItems()
        val existing = all.firstOrNull {
            !it.deleted && it.completedAt == null && it.product.stableKey == suggestion.product.stableKey && it.dimension == dimension &&
                (dimension != GroceryDimension.OTHER || (it.preferredUnit == unit && it.customUnit == customUnit))
        }
        val item = existing?.copy(baseQuantity = existing.baseQuantity + baseQuantity, preferredUnit = existing.preferredUnit)
            ?: GroceryItem(UUID.randomUUID().toString(), suggestion.product, suggestion.category, baseQuantity, dimension, unit, customUnit, quantitySpecified, now)
        val type = if (existing == null) GroceryMutationType.ADD else GroceryMutationType.UPDATE
        persistLocal(item, GroceryMutation(UUID.randomUUID().toString(), type, item.id, now, item))
        remember(suggestion.copy(category = item.category), now)
    }

    suspend fun update(item: GroceryItem): Result<Unit> = runCatching {
        remember(GrocerySuggestion(item.product, item.category), Instant.now().toString())
        mutate(item.copy(revision = item.revision), GroceryMutationType.UPDATE).getOrThrow()
    }
    suspend fun replaceImage(itemId: String, name: String, stream: InputStream): Result<Unit> = runCatching {
        val body = stream.readBytes().toRequestBody("image/*".toMediaType())
        val updated = api.replaceGroceryImage(itemId, MultipartBody.Part.createFormData("datei", name, body))
        dao.upsert(updated.entity())
        synchronize().getOrThrow()
    }
    suspend fun complete(item: GroceryItem): Result<Unit> {
        val now = Instant.now().toString()
        return mutate(item.copy(completedAt = now), GroceryMutationType.COMPLETE, now)
    }
    suspend fun restore(item: GroceryItem): Result<Unit> = mutate(item.copy(completedAt = null), GroceryMutationType.RESTORE)
    suspend fun delete(item: GroceryItem): Result<Unit> = mutate(item.copy(deleted = true), GroceryMutationType.DELETE)

    suspend fun search(query: String): List<GrocerySuggestion> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return emptyList()
        val local = dao.suggestions(trimmed).map { json.decodeFromString<GrocerySuggestion>(it.json) }
        val remote = runCatching { api.searchGroceries(GrocerySearchRequest(trimmed)) }.getOrDefault(emptyList())
        return (local + remote).distinctBy { it.product.stableKey }
    }

    suspend fun lookupBarcode(barcode: String): GroceryBarcodeResult = runCatching {
        val response = api.groceryByBarcode(barcode)
        when {
            response.isSuccessful -> response.body()?.let(GroceryBarcodeResult::Found) ?: GroceryBarcodeResult.NotFound
            response.code() == 404 -> GroceryBarcodeResult.NotFound
            else -> GroceryBarcodeResult.Unavailable
        }
    }.getOrDefault(GroceryBarcodeResult.Unavailable)

    suspend fun synchronize(): Result<List<String>> = syncMutex.withLock {
        runCatching {
            val pendingRows = dao.pending()
            val pending = pendingRows.map { json.decodeFromString<GroceryMutation>(it.json) }
            val response = api.syncGroceries(GrocerySyncRequest(deviceId, dao.revision() ?: 0, pending))
            response.changes.forEach { dao.upsert(it.entity()) }
            val handled = response.acknowledgedOperationIds + response.rejectedOperations.keys
            if (handled.isNotEmpty()) dao.removeMutations(handled)
            dao.setState(GrocerySyncStateEntity(revision = response.revision))
            if (response.rejectedOperations.isNotEmpty()) {
                val authoritative = api.syncGroceries(GrocerySyncRequest(deviceId, 0, emptyList()))
                dao.clearItems()
                dao.upsertAll(authoritative.changes.map { it.entity() })
                dao.setState(GrocerySyncStateEntity(revision = authoritative.revision))
            }
            response.rejectedOperations.values.toList()
        }
    }

    private suspend fun mutate(item: GroceryItem, type: GroceryMutationType, at: String = Instant.now().toString()): Result<Unit> = runCatching {
        persistLocal(item, GroceryMutation(UUID.randomUUID().toString(), type, item.id, at, if (type in setOf(GroceryMutationType.UPDATE, GroceryMutationType.ADD)) item else null))
        synchronize()
    }

    private suspend fun persistLocal(item: GroceryItem, mutation: GroceryMutation) {
        dao.upsert(item.entity())
        dao.enqueue(GroceryMutationEntity(mutation.operationId, json.encodeToString(GroceryMutation.serializer(), mutation), mutation.occurredAt))
    }

    private suspend fun remember(suggestion: GrocerySuggestion, now: String) = dao.rememberSuggestion(
        GrocerySuggestionEntity(
            suggestion.product.stableKey,
            suggestion.product.name,
            json.encodeToString(GrocerySuggestion.serializer(), suggestion),
            now,
        ),
    )

    private suspend fun decodedItems() = dao.all().map { json.decodeFromString<GroceryItem>(it.json) }
    private fun GroceryItem.entity() = GroceryItemEntity(id, json.encodeToString(GroceryItem.serializer(), this), revision, deleted)
}
