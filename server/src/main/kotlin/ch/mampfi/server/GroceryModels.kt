package ch.mampfi.server

import kotlinx.serialization.Serializable

@Serializable enum class GrocerySource { OPEN_FOOD_FACTS, MANUAL }
@Serializable enum class GroceryCategory {
    FRUIT, VEGETABLES, BAKERY, DAIRY_EGGS, MEAT_FISH, CHILLED, PANTRY,
    SPICES_SAUCES, SWEETS_SNACKS, FROZEN, DRINKS, HOUSEHOLD_HYGIENE, OTHER,
}
@Serializable enum class GroceryDimension { COUNT, WEIGHT, VOLUME }
@Serializable enum class GroceryUnit { PIECE, G, KG, ML, L }

@Serializable data class GroceryProductRef(
    val source: GrocerySource,
    val stableKey: String,
    val name: String,
    val barcode: String? = null,
    val brand: String? = null,
    val packageSize: String? = null,
    val imageUrl: String? = null,
)

@Serializable data class GroceryItem(
    val id: String,
    val product: GroceryProductRef,
    val category: GroceryCategory,
    val baseQuantity: Long,
    val dimension: GroceryDimension,
    val preferredUnit: GroceryUnit,
    val createdAt: String,
    val completedAt: String? = null,
    val revision: Long = 0,
    val deleted: Boolean = false,
)

@Serializable enum class GroceryMutationType { ADD, UPDATE, COMPLETE, RESTORE, DELETE }
@Serializable data class GroceryMutation(
    val operationId: String,
    val type: GroceryMutationType,
    val itemId: String,
    val occurredAt: String,
    val item: GroceryItem? = null,
)
@Serializable data class GrocerySyncRequest(
    val deviceId: String,
    val sinceRevision: Long,
    val operations: List<GroceryMutation> = emptyList(),
)
@Serializable data class GrocerySyncResponse(
    val revision: Long,
    val changes: List<GroceryItem>,
    val acknowledgedOperationIds: List<String>,
    val rejectedOperations: Map<String, String> = emptyMap(),
)
@Serializable data class GrocerySearchRequest(val query: String)
@Serializable data class GrocerySuggestion(val product: GroceryProductRef, val category: GroceryCategory)

internal fun GroceryUnit.dimension() = when (this) {
    GroceryUnit.PIECE -> GroceryDimension.COUNT
    GroceryUnit.G, GroceryUnit.KG -> GroceryDimension.WEIGHT
    GroceryUnit.ML, GroceryUnit.L -> GroceryDimension.VOLUME
}
