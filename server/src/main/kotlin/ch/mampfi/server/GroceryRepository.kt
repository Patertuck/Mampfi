package ch.mampfi.server

import kotlinx.serialization.json.Json
import java.sql.Connection
import java.sql.DriverManager

class GroceryRepository(database: String) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val connection = DriverManager.getConnection("jdbc:sqlite:$database")

    init {
        connection.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
        createSchema()
    }

    @Synchronized fun sync(request: GrocerySyncRequest): GrocerySyncResponse = transaction {
        val acknowledged = mutableListOf<String>()
        val rejected = linkedMapOf<String, String>()
        request.operations.forEach { mutation ->
            if (wasProcessed(mutation.operationId)) {
                acknowledged += mutation.operationId
                return@forEach
            }
            val failure = applyMutation(mutation)
            markProcessed(mutation.operationId)
            if (failure == null) acknowledged += mutation.operationId else rejected[mutation.operationId] = failure
        }
        GrocerySyncResponse(currentRevision(), changesSince(request.sinceRevision), acknowledged, rejected)
    }

    @Synchronized fun manualSuggestions(query: String, limit: Int = 8): List<GrocerySuggestion> = connection.prepareStatement(
        "SELECT product_json, category FROM grocery_products WHERE lower(name) LIKE ? ORDER BY last_used_at DESC LIMIT ?",
    ).use { statement ->
        statement.setString(1, "%${query.trim().lowercase()}%")
        statement.setInt(2, limit)
        statement.executeQuery().use { rows -> buildList {
            while (rows.next()) add(GrocerySuggestion(
                json.decodeFromString<GroceryProductRef>(rows.getString("product_json")),
                GroceryCategory.valueOf(rows.getString("category")),
            ))
        } }
    }

    @Synchronized fun categoryFor(stableKey: String): GroceryCategory? = connection.prepareStatement(
        "SELECT category FROM grocery_products WHERE stable_key = ?",
    ).use { statement ->
        statement.setString(1, stableKey)
        statement.executeQuery().use { rows -> if (rows.next()) GroceryCategory.valueOf(rows.getString(1)) else null }
    }

    private fun applyMutation(mutation: GroceryMutation): String? {
        return when (mutation.type) {
            GroceryMutationType.ADD -> add(mutation.item ?: return "Item fehlt")
            GroceryMutationType.UPDATE -> update(mutation.item ?: return "Item fehlt")
            GroceryMutationType.COMPLETE -> complete(mutation.itemId, mutation.occurredAt)
            GroceryMutationType.RESTORE -> restore(mutation.itemId)
            GroceryMutationType.DELETE -> delete(mutation.itemId)
        }
    }

    private fun add(item: GroceryItem): String? {
        validate(item)?.let { return it }
        val existing = activeMatch(item)
        val revision = nextRevision()
        if (existing != null) {
            connection.prepareStatement("UPDATE grocery_items SET base_quantity = ?, revision = ? WHERE id = ?").use {
                it.setDouble(1, existing.baseQuantity + item.baseQuantity); it.setLong(2, revision); it.setString(3, existing.id); it.executeUpdate()
            }
            if (existing.id != item.id && find(item.id) == null) insert(item.copy(revision = nextRevision(), completedAt = null, deleted = true))
        } else insert(item.copy(revision = revision, completedAt = null, deleted = false))
        rememberProduct(item)
        return null
    }

    private fun update(item: GroceryItem): String? {
        validate(item)?.let { return it }
        val current = find(item.id) ?: return "Eintrag existiert nicht"
        if (current.deleted || current.completedAt != null) return "Nur offene Einträge können bearbeitet werden"
        val revision = nextRevision()
        connection.prepareStatement(
            "UPDATE grocery_items SET product_json = ?, stable_key = ?, category = ?, base_quantity = ?, dimension = ?, preferred_unit = ?, custom_unit = ?, quantity_specified = ?, revision = ? WHERE id = ?",
        ).use {
            it.setString(1, json.encodeToString(GroceryProductRef.serializer(), item.product)); it.setString(2, item.product.stableKey)
            it.setString(3, item.category.name); it.setDouble(4, item.baseQuantity); it.setString(5, item.dimension.name)
            it.setString(6, item.preferredUnit.name); it.setString(7, item.customUnit); it.setBoolean(8, item.quantitySpecified)
            it.setLong(9, revision); it.setString(10, item.id); it.executeUpdate()
        }
        rememberProduct(item)
        return null
    }

    private fun complete(id: String, at: String): String? {
        val current = find(id) ?: return "Eintrag existiert nicht"
        if (current.deleted || current.completedAt != null) return null
        connection.prepareStatement("UPDATE grocery_items SET completed_at = ?, revision = ? WHERE id = ?").use {
            it.setString(1, at); it.setLong(2, nextRevision()); it.setString(3, id); it.executeUpdate()
        }
        return null
    }

    private fun restore(id: String): String? {
        val current = find(id) ?: return "Eintrag existiert nicht"
        if (current.deleted || current.completedAt == null) return null
        val match = activeMatch(current)
        if (match != null && match.id != current.id) {
            val revision = nextRevision()
            connection.prepareStatement("UPDATE grocery_items SET base_quantity = ?, revision = ? WHERE id = ?").use {
                it.setDouble(1, match.baseQuantity + current.baseQuantity); it.setLong(2, revision); it.setString(3, match.id); it.executeUpdate()
            }
            connection.prepareStatement("UPDATE grocery_items SET deleted = 1, revision = ? WHERE id = ?").use {
                it.setLong(1, nextRevision()); it.setString(2, current.id); it.executeUpdate()
            }
        } else connection.prepareStatement("UPDATE grocery_items SET completed_at = NULL, revision = ? WHERE id = ?").use {
            it.setLong(1, nextRevision()); it.setString(2, id); it.executeUpdate()
        }
        return null
    }

    private fun delete(id: String): String? {
        val current = find(id) ?: return "Eintrag existiert nicht"
        if (current.deleted) return null
        connection.prepareStatement("UPDATE grocery_items SET deleted = 1, revision = ? WHERE id = ?").use {
            it.setLong(1, nextRevision()); it.setString(2, id); it.executeUpdate()
        }
        return null
    }

    private fun validate(item: GroceryItem): String? = when {
        item.product.name.isBlank() -> "Name darf nicht leer sein"
        item.product.stableKey.isBlank() -> "Produktschlüssel fehlt"
        item.quantitySpecified && item.baseQuantity <= 0 -> "Menge muss positiv sein"
        item.preferredUnit.dimension() != item.dimension -> "Einheit passt nicht zur Menge"
        else -> null
    }

    private fun rememberProduct(item: GroceryItem) {
        connection.prepareStatement(
            "INSERT INTO grocery_products (stable_key, name, product_json, category, last_used_at) VALUES (?, ?, ?, ?, ?) " +
                "ON CONFLICT(stable_key) DO UPDATE SET name=excluded.name, product_json=excluded.product_json, category=excluded.category, last_used_at=excluded.last_used_at",
        ).use {
            it.setString(1, item.product.stableKey); it.setString(2, item.product.name)
            it.setString(3, json.encodeToString(GroceryProductRef.serializer(), item.product)); it.setString(4, item.category.name)
            it.setString(5, item.createdAt); it.executeUpdate()
        }
    }

    private fun activeMatch(item: GroceryItem): GroceryItem? = connection.prepareStatement(
        "SELECT * FROM grocery_items WHERE stable_key = ? AND dimension = ? AND (? <> 'OTHER' OR (preferred_unit = ? AND coalesce(custom_unit, '') = coalesce(?, ''))) AND completed_at IS NULL AND deleted = 0 LIMIT 1",
    ).use { statement ->
        statement.setString(1, item.product.stableKey); statement.setString(2, item.dimension.name); statement.setString(3, item.dimension.name)
        statement.setString(4, item.preferredUnit.name); statement.setString(5, item.customUnit)
        statement.executeQuery().use { rows -> if (rows.next()) rows.item() else null }
    }

    private fun find(id: String): GroceryItem? = connection.prepareStatement("SELECT * FROM grocery_items WHERE id = ?").use { statement ->
        statement.setString(1, id); statement.executeQuery().use { rows -> if (rows.next()) rows.item() else null }
    }

    private fun insert(item: GroceryItem) = connection.prepareStatement(
        "INSERT INTO grocery_items (id, product_json, stable_key, category, base_quantity, dimension, preferred_unit, custom_unit, quantity_specified, created_at, completed_at, revision, deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
    ).use {
        it.setString(1, item.id); it.setString(2, json.encodeToString(GroceryProductRef.serializer(), item.product)); it.setString(3, item.product.stableKey)
        it.setString(4, item.category.name); it.setDouble(5, item.baseQuantity); it.setString(6, item.dimension.name); it.setString(7, item.preferredUnit.name)
        it.setString(8, item.customUnit); it.setBoolean(9, item.quantitySpecified); it.setString(10, item.createdAt); it.setString(11, item.completedAt)
        it.setLong(12, item.revision); it.setBoolean(13, item.deleted); it.executeUpdate()
    }

    private fun changesSince(revision: Long): List<GroceryItem> = connection.prepareStatement(
        "SELECT * FROM grocery_items WHERE revision > ? ORDER BY revision",
    ).use { statement ->
        statement.setLong(1, revision); statement.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.item()) } }
    }

    private fun java.sql.ResultSet.item() = GroceryItem(
        id = getString("id"), product = json.decodeFromString(getString("product_json")),
        category = GroceryCategory.valueOf(getString("category")), baseQuantity = getDouble("base_quantity"),
        dimension = GroceryDimension.valueOf(getString("dimension")), preferredUnit = GroceryUnit.valueOf(getString("preferred_unit")),
        customUnit = getString("custom_unit"), quantitySpecified = getBoolean("quantity_specified"),
        createdAt = getString("created_at"), completedAt = getString("completed_at"), revision = getLong("revision"), deleted = getBoolean("deleted"),
    )

    private fun currentRevision(): Long = connection.createStatement().use { statement ->
        statement.executeQuery("SELECT revision FROM grocery_meta WHERE id = 1").use { it.next(); it.getLong(1) }
    }
    private fun nextRevision(): Long {
        connection.createStatement().use { it.executeUpdate("UPDATE grocery_meta SET revision = revision + 1 WHERE id = 1") }
        return currentRevision()
    }
    private fun wasProcessed(id: String) = connection.prepareStatement("SELECT 1 FROM grocery_mutations WHERE operation_id = ?").use {
        it.setString(1, id); it.executeQuery().use { rows -> rows.next() }
    }
    private fun markProcessed(id: String) = connection.prepareStatement("INSERT INTO grocery_mutations (operation_id) VALUES (?)").use {
        it.setString(1, id); it.executeUpdate()
    }

    private fun createSchema() = connection.createStatement().use { statement ->
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS grocery_meta (id INTEGER PRIMARY KEY CHECK(id=1), revision INTEGER NOT NULL)")
        statement.executeUpdate("INSERT OR IGNORE INTO grocery_meta (id, revision) VALUES (1, 0)")
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS grocery_items (id TEXT PRIMARY KEY, product_json TEXT NOT NULL, stable_key TEXT NOT NULL, category TEXT NOT NULL, base_quantity INTEGER NOT NULL, dimension TEXT NOT NULL, preferred_unit TEXT NOT NULL, created_at TEXT NOT NULL, completed_at TEXT, revision INTEGER NOT NULL, deleted INTEGER NOT NULL DEFAULT 0)")
        val columns = statement.executeQuery("PRAGMA table_info(grocery_items)").use { rows -> buildSet { while (rows.next()) add(rows.getString("name")) } }
        if ("custom_unit" !in columns) statement.executeUpdate("ALTER TABLE grocery_items ADD COLUMN custom_unit TEXT")
        if ("quantity_specified" !in columns) statement.executeUpdate("ALTER TABLE grocery_items ADD COLUMN quantity_specified INTEGER NOT NULL DEFAULT 1")
        statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_grocery_items_revision ON grocery_items(revision)")
        statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_grocery_items_active ON grocery_items(stable_key, dimension, completed_at, deleted)")
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS grocery_products (stable_key TEXT PRIMARY KEY, name TEXT NOT NULL, product_json TEXT NOT NULL, category TEXT NOT NULL, last_used_at TEXT NOT NULL)")
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS grocery_mutations (operation_id TEXT PRIMARY KEY)")
        statement.execute("PRAGMA user_version = 7")
    }

    private fun <T> transaction(block: () -> T): T {
        val previous = connection.autoCommit
        connection.autoCommit = false
        return try { block().also { connection.commit() } } catch (error: Throwable) {
            connection.rollback(); throw error
        } finally { connection.autoCommit = previous }
    }
}
