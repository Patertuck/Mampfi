package ch.mampfi.app.data

import kotlinx.serialization.Serializable
import java.text.NumberFormat
import java.util.Locale

@Serializable enum class GrocerySource { OPEN_FOOD_FACTS, MANUAL, RECIPE }
@Serializable enum class GroceryCategory(val label: String) {
    FRUIT("Obst"), VEGETABLES("Gemüse"), BAKERY("Brot & Backwaren"), DAIRY_EGGS("Milch & Eier"),
    MEAT_FISH("Fleisch & Fisch"), CHILLED("Kühlregal"), PANTRY("Vorräte"),
    SPICES_SAUCES("Gewürze & Saucen"), SWEETS_SNACKS("Süsses & Snacks"), FROZEN("Tiefkühlprodukte"),
    DRINKS("Getränke"), HOUSEHOLD_HYGIENE("Haushalt & Hygiene"), OTHER("Sonstiges"),
}
@Serializable enum class GroceryDimension { COUNT, WEIGHT, VOLUME, OTHER }
@Serializable enum class GroceryUnit(val label: String) {
    PIECE("Stück"), G("g"), KG("kg"), ML("ml"), DL("dl"), L("l"), TSP("TL"), TBSP("EL"),
    PINCH("Prise"), BUNCH("Bund"), PACKAGE("Packung"), CAN("Dose"), CLOVE("Zehe"), CUBE("Würfel"), CUSTOM("Einheit"),
}
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
    val baseQuantity: Double,
    val dimension: GroceryDimension,
    val preferredUnit: GroceryUnit,
    val customUnit: String? = null,
    val quantitySpecified: Boolean = true,
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
@Serializable data class GrocerySyncRequest(val deviceId: String, val sinceRevision: Long, val operations: List<GroceryMutation> = emptyList())
@Serializable data class GrocerySyncResponse(
    val revision: Long,
    val changes: List<GroceryItem>,
    val acknowledgedOperationIds: List<String>,
    val rejectedOperations: Map<String, String> = emptyMap(),
)
@Serializable data class GrocerySearchRequest(val query: String)
@Serializable data class GrocerySuggestion(val product: GroceryProductRef, val category: GroceryCategory)
data class RecipeGrocerySelection(
    val suggestion: GrocerySuggestion, val baseQuantity: Double, val unit: GroceryUnit,
    val customUnit: String? = null, val quantitySpecified: Boolean = true,
)

fun GroceryUnit.dimension() = when (this) {
    GroceryUnit.PIECE -> GroceryDimension.COUNT
    GroceryUnit.G, GroceryUnit.KG -> GroceryDimension.WEIGHT
    GroceryUnit.ML, GroceryUnit.DL, GroceryUnit.L -> GroceryDimension.VOLUME
    else -> GroceryDimension.OTHER
}

fun quantityToBase(value: String, unit: GroceryUnit): Double? {
    val parsed = value.trim().replace(',', '.').toBigDecimalOrNull() ?: return null
    if (parsed.signum() <= 0) return null
    val multiplier = when (unit) { GroceryUnit.KG, GroceryUnit.L -> 1000.toBigDecimal(); GroceryUnit.DL -> 100.toBigDecimal(); else -> 1.toBigDecimal() }
    return parsed.multiply(multiplier).toDouble()
}

fun GroceryItem.formattedQuantity(): String {
    if (!quantitySpecified) return customUnit ?: "nach Bedarf"
    val value = when (preferredUnit) {
        GroceryUnit.KG, GroceryUnit.L -> baseQuantity / 1000.0
        GroceryUnit.DL -> baseQuantity / 100.0
        else -> baseQuantity.toDouble()
    }
    val format = NumberFormat.getNumberInstance(Locale.GERMAN).apply { maximumFractionDigits = 3 }
    return "${format.format(value)} ${if (preferredUnit == GroceryUnit.CUSTOM) customUnit.orEmpty() else preferredUnit.label}".trim()
}
