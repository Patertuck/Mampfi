package ch.mampfi.server

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.sql.DriverManager

data class DeletedEntry(val imageUrls: List<String>)

class MealRepository(private val database: String) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val connection = DriverManager.getConnection("jdbc:sqlite:$database")

    init {
        try {
            connection.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
            initializeSchema()
        } catch (error: Throwable) {
            connection.close()
            throw error
        }
    }

    @Synchronized fun all(): List<Mahlzeit> = connection.prepareStatement(
        "SELECT id, name, rezept_link, tags FROM mahlzeiten ORDER BY name COLLATE NOCASE",
    ).use { statement -> statement.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.meal()) } } }

    @Synchronized fun find(id: String): Mahlzeit? = connection.prepareStatement(
        "SELECT id, name, rezept_link, tags FROM mahlzeiten WHERE id = ?",
    ).use { statement ->
        statement.setString(1, id)
        statement.executeQuery().use { rows -> if (rows.next()) rows.meal() else null }
    }

    @Synchronized fun insert(meal: Mahlzeit) = transaction {
        connection.prepareStatement("INSERT INTO mahlzeiten (id, name, rezept_link, tags) VALUES (?, ?, ?, ?)").use { statement ->
            bindMeal(statement, meal)
            statement.executeUpdate()
        }
        meal.eintraege.forEach { insertEntryRow(meal.id, it) }
    }

    @Synchronized fun updateMeal(meal: Mahlzeit): Boolean = connection.prepareStatement(
        "UPDATE mahlzeiten SET name = ?, rezept_link = ?, tags = ? WHERE id = ?",
    ).use { statement ->
        statement.setString(1, meal.name)
        statement.setString(2, meal.rezeptLink)
        statement.setString(3, json.encodeToString(meal.tags))
        statement.setString(4, meal.id)
        statement.executeUpdate() > 0
    }

    @Synchronized fun insertEntry(mealId: String, entry: MahlzeitEintrag): Boolean {
        if (find(mealId) == null) return false
        insertEntryRow(mealId, entry)
        return true
    }

    @Synchronized fun updateEntry(mealId: String, entry: MahlzeitEintrag): Boolean = connection.prepareStatement(
        "UPDATE mahlzeit_eintraege SET datum = ?, bewertung = ? WHERE id = ? AND mahlzeit_id = ?",
    ).use { statement ->
        statement.setString(1, entry.datum)
        statement.setString(2, entry.bewertung?.let { json.encodeToString(it) })
        statement.setString(3, entry.id)
        statement.setString(4, mealId)
        statement.executeUpdate() > 0
    }

    @Synchronized fun addImage(mealId: String, entryId: String, image: MahlzeitBild): Boolean = connection.prepareStatement(
        "INSERT INTO mahlzeit_bilder (id, eintrag_id, url) " +
            "SELECT ?, e.id, ? FROM mahlzeit_eintraege e WHERE e.id = ? AND e.mahlzeit_id = ?",
    ).use { statement ->
        statement.setString(1, image.id)
        statement.setString(2, image.url)
        statement.setString(3, entryId)
        statement.setString(4, mealId)
        statement.executeUpdate() > 0
    }

    @Synchronized fun deleteEntry(mealId: String, entryId: String): DeletedEntry? = transaction {
        val imageUrls = connection.prepareStatement(
            "SELECT b.url FROM mahlzeit_bilder b JOIN mahlzeit_eintraege e ON e.id = b.eintrag_id " +
                "WHERE e.id = ? AND e.mahlzeit_id = ?",
        ).use { statement ->
            statement.setString(1, entryId)
            statement.setString(2, mealId)
            statement.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.getString("url")) } }
        }
        val deleted = connection.prepareStatement("DELETE FROM mahlzeit_eintraege WHERE id = ? AND mahlzeit_id = ?").use { statement ->
            statement.setString(1, entryId)
            statement.setString(2, mealId)
            statement.executeUpdate()
        }
        if (deleted == 0) return@transaction null
        connection.prepareStatement(
            "DELETE FROM mahlzeiten WHERE id = ? AND NOT EXISTS (SELECT 1 FROM mahlzeit_eintraege WHERE mahlzeit_id = ?)",
        ).use { statement ->
            statement.setString(1, mealId)
            statement.setString(2, mealId)
            statement.executeUpdate()
        }
        DeletedEntry(imageUrls)
    }

    private fun java.sql.ResultSet.meal(): Mahlzeit {
        val mealId = getString("id")
        return Mahlzeit(mealId, getString("name"), getString("rezept_link"), json.decodeFromString(getString("tags")), entries(mealId))
    }

    private fun entries(mealId: String): List<MahlzeitEintrag> = connection.prepareStatement(
        "SELECT id, datum, bewertung FROM mahlzeit_eintraege WHERE mahlzeit_id = ? ORDER BY datum",
    ).use { statement ->
        statement.setString(1, mealId)
        statement.executeQuery().use { rows -> buildList {
            while (rows.next()) {
                val entryId = rows.getString("id")
                add(MahlzeitEintrag(entryId, rows.getString("datum"), images(entryId), rows.getString("bewertung")?.let { json.decodeFromString(it) }))
            }
        } }
    }

    private fun images(entryId: String): List<MahlzeitBild> = connection.prepareStatement(
        "SELECT id, url FROM mahlzeit_bilder WHERE eintrag_id = ? ORDER BY rowid",
    ).use { statement ->
        statement.setString(1, entryId)
        statement.executeQuery().use { rows -> buildList { while (rows.next()) add(MahlzeitBild(rows.getString("id"), rows.getString("url"))) } }
    }

    private fun insertEntryRow(mealId: String, entry: MahlzeitEintrag) {
        connection.prepareStatement("INSERT INTO mahlzeit_eintraege (id, mahlzeit_id, datum, bewertung) VALUES (?, ?, ?, ?)").use { statement ->
            statement.setString(1, entry.id)
            statement.setString(2, mealId)
            statement.setString(3, entry.datum)
            statement.setString(4, entry.bewertung?.let { json.encodeToString(it) })
            statement.executeUpdate()
        }
        entry.bilder.forEach { image ->
            connection.prepareStatement("INSERT INTO mahlzeit_bilder (id, eintrag_id, url) VALUES (?, ?, ?)").use { statement ->
                statement.setString(1, image.id)
                statement.setString(2, entry.id)
                statement.setString(3, image.url)
                statement.executeUpdate()
            }
        }
    }

    private fun bindMeal(statement: java.sql.PreparedStatement, meal: Mahlzeit) {
        statement.setString(1, meal.id)
        statement.setString(2, meal.name)
        statement.setString(3, meal.rezeptLink)
        statement.setString(4, json.encodeToString(meal.tags))
    }

    private fun initializeSchema() {
        val columns = tableColumns("mahlzeiten")
        if (columns.isEmpty()) {
            createSchema()
            setSchemaVersion()
            return
        }
        check("termine" !in columns) {
            "Legacy v1 database schema is no longer supported; start once with a migration-capable backend image before upgrading"
        }
        val requiredColumns = setOf("id", "name", "rezept_link", "tags")
        check(columns.containsAll(requiredColumns)) {
            "Unsupported mahlzeiten schema; expected columns $requiredColumns but found $columns"
        }
        createSchema()
        setSchemaVersion()
    }

    private fun createSchema() {
        connection.createStatement().use { statement ->
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS mahlzeiten (id TEXT PRIMARY KEY, name TEXT NOT NULL, rezept_link TEXT, tags TEXT NOT NULL)")
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS mahlzeit_eintraege (id TEXT PRIMARY KEY, mahlzeit_id TEXT NOT NULL, datum TEXT NOT NULL, bewertung TEXT, FOREIGN KEY (mahlzeit_id) REFERENCES mahlzeiten(id) ON DELETE CASCADE, UNIQUE (mahlzeit_id, datum))")
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS mahlzeit_bilder (id TEXT PRIMARY KEY, eintrag_id TEXT NOT NULL, url TEXT NOT NULL, FOREIGN KEY (eintrag_id) REFERENCES mahlzeit_eintraege(id) ON DELETE CASCADE)")
        }
    }

    private fun tableColumns(table: String): Set<String> = connection.createStatement().use { statement ->
        statement.executeQuery("PRAGMA table_info($table)").use { rows -> buildSet { while (rows.next()) add(rows.getString("name")) } }
    }

    private fun setSchemaVersion() { connection.createStatement().use { it.execute("PRAGMA user_version = 2") } }

    private fun <T> transaction(block: () -> T): T {
        val previousAutoCommit = connection.autoCommit
        connection.autoCommit = false
        return try { block().also { connection.commit() } } catch (error: Throwable) {
            connection.rollback()
            throw error
        } finally { connection.autoCommit = previousAutoCommit }
    }
}
