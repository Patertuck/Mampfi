package ch.mampfi.server

import java.nio.file.Files
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GroceryRepositoryTest {
    @Test fun `adds merge idempotently and completion remains in history`() {
        val database = Files.createTempDirectory("mampfi-grocery").resolve("test.db").toString()
        val repository = GroceryRepository(database)
        val first = item("first", 1_000, GroceryUnit.KG)
        val second = item("second", 500, GroceryUnit.G)

        val addFirst = mutation("add-first", GroceryMutationType.ADD, first)
        repository.sync(GrocerySyncRequest("phone-a", 0, listOf(addFirst, addFirst)))
        val merged = repository.sync(GrocerySyncRequest("phone-b", 0, listOf(mutation("add-second", GroceryMutationType.ADD, second))))
        val active = merged.changes.single { !it.deleted }
        assertEquals(1_500.0, active.baseQuantity)
        assertTrue(merged.changes.any { it.id == second.id && it.deleted })

        val completedAt = "2026-10-19T12:00:00Z"
        val completed = repository.sync(GrocerySyncRequest("phone-a", merged.revision, listOf(
            GroceryMutation("complete", GroceryMutationType.COMPLETE, active.id, completedAt),
        )))
        assertEquals(completedAt, completed.changes.single().completedAt)
    }

    @Test fun `restoring purchase merges into an existing active item`() {
        val database = Files.createTempDirectory("mampfi-grocery-restore").resolve("test.db").toString()
        val repository = GroceryRepository(database)
        val historical = item("history", 500, GroceryUnit.G)
        repository.sync(GrocerySyncRequest("phone", 0, listOf(mutation("a", GroceryMutationType.ADD, historical))))
        repository.sync(GrocerySyncRequest("phone", 0, listOf(GroceryMutation("c", GroceryMutationType.COMPLETE, historical.id, Instant.now().toString()))))
        val active = item("active", 250, GroceryUnit.G)
        repository.sync(GrocerySyncRequest("phone", 0, listOf(mutation("b", GroceryMutationType.ADD, active))))

        val response = repository.sync(GrocerySyncRequest("phone", 0, listOf(GroceryMutation("r", GroceryMutationType.RESTORE, historical.id, Instant.now().toString()))))
        assertEquals(750.0, response.changes.single { it.id == active.id }.baseQuantity)
        assertTrue(response.changes.single { it.id == historical.id }.deleted)
    }

    private fun item(id: String, quantity: Long, unit: GroceryUnit) = GroceryItem(
        id = id,
        product = GroceryProductRef(GrocerySource.OPEN_FOOD_FACTS, "off:761234", "Testprodukt", barcode = "761234"),
        category = GroceryCategory.PANTRY,
        baseQuantity = quantity.toDouble(),
        dimension = GroceryDimension.WEIGHT,
        preferredUnit = unit,
        createdAt = Instant.now().toString(),
    )

    private fun mutation(id: String = UUID.randomUUID().toString(), type: GroceryMutationType, item: GroceryItem) =
        GroceryMutation(id, type, item.id, Instant.now().toString(), item)
}
