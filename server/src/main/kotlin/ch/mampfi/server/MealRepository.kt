package ch.mampfi.server

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.sql.DriverManager

data class DeletedEntry(val imageUrls: List<String>)
enum class DeleteIdeaResult { DELETED, NOT_FOUND, HAS_ENTRIES }
class DateBlockedException : IllegalStateException()
enum class AwayWriteStatus { CREATED, UPDATED, NOT_FOUND, DATE_OCCUPIED, MEALS_EXIST }
data class AwayWriteResult(val status: AwayWriteStatus, val deletedImageUrls: List<String> = emptyList())

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
        "SELECT id, name, rezept_link, tags, ist_idee FROM mahlzeiten ORDER BY name COLLATE NOCASE",
    ).use { statement -> statement.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.meal()) } } }

    @Synchronized fun find(id: String): Mahlzeit? = connection.prepareStatement(
        "SELECT id, name, rezept_link, tags, ist_idee FROM mahlzeiten WHERE id = ?",
    ).use { statement ->
        statement.setString(1, id)
        statement.executeQuery().use { rows -> if (rows.next()) rows.meal() else null }
    }

    @Synchronized fun allAwayEntries(): List<AuswaertsEintrag> = connection.prepareStatement(
        "SELECT id, datum, notiz FROM auswaerts_eintraege ORDER BY datum",
    ).use { statement -> statement.executeQuery().use { rows -> buildList {
        while (rows.next()) add(AuswaertsEintrag(rows.getString("id"), rows.getString("datum"), rows.getString("notiz")))
    } } }

    @Synchronized fun createAwayEntry(entry: AuswaertsEintrag, replaceMeals: Boolean): AwayWriteResult = transaction {
        if (awayDateOccupied(entry.datum)) return@transaction AwayWriteResult(AwayWriteStatus.DATE_OCCUPIED)
        val mealCount = mealCountOn(entry.datum)
        if (mealCount > 0 && !replaceMeals) return@transaction AwayWriteResult(AwayWriteStatus.MEALS_EXIST)
        val images = if (mealCount > 0) removeMealsOn(entry.datum) else emptyList()
        connection.prepareStatement("INSERT INTO auswaerts_eintraege (id, datum, notiz) VALUES (?, ?, ?)").use { statement ->
            statement.setString(1, entry.id); statement.setString(2, entry.datum); statement.setString(3, entry.notiz); statement.executeUpdate()
        }
        AwayWriteResult(AwayWriteStatus.CREATED, images)
    }

    @Synchronized fun updateAwayEntry(entry: AuswaertsEintrag, replaceMeals: Boolean): AwayWriteResult = transaction {
        val exists = connection.prepareStatement("SELECT 1 FROM auswaerts_eintraege WHERE id = ?").use { statement ->
            statement.setString(1, entry.id); statement.executeQuery().use { it.next() }
        }
        if (!exists) return@transaction AwayWriteResult(AwayWriteStatus.NOT_FOUND)
        if (awayDateOccupied(entry.datum, entry.id)) return@transaction AwayWriteResult(AwayWriteStatus.DATE_OCCUPIED)
        val mealCount = mealCountOn(entry.datum)
        if (mealCount > 0 && !replaceMeals) return@transaction AwayWriteResult(AwayWriteStatus.MEALS_EXIST)
        val images = if (mealCount > 0) removeMealsOn(entry.datum) else emptyList()
        connection.prepareStatement("UPDATE auswaerts_eintraege SET datum = ?, notiz = ? WHERE id = ?").use { statement ->
            statement.setString(1, entry.datum); statement.setString(2, entry.notiz); statement.setString(3, entry.id); statement.executeUpdate()
        }
        AwayWriteResult(AwayWriteStatus.UPDATED, images)
    }

    @Synchronized fun deleteAwayEntry(id: String): Boolean = connection.prepareStatement(
        "DELETE FROM auswaerts_eintraege WHERE id = ?",
    ).use { statement -> statement.setString(1, id); statement.executeUpdate() > 0 }

    @Synchronized fun insert(meal: Mahlzeit) = transaction {
        if (meal.eintraege.any { awayDateOccupied(it.datum) }) throw DateBlockedException()
        connection.prepareStatement("INSERT INTO mahlzeiten (id, name, rezept_link, tags, ist_idee) VALUES (?, ?, ?, ?, ?)").use { statement ->
            bindMeal(statement, meal)
            statement.executeUpdate()
        }
        meal.eintraege.forEach { insertEntryRow(meal.id, it) }
    }

    @Synchronized fun updateMeal(meal: Mahlzeit): Boolean = connection.prepareStatement(
        "UPDATE mahlzeiten SET name = ?, rezept_link = ?, tags = ?, ist_idee = ? WHERE id = ?",
    ).use { statement ->
        statement.setString(1, meal.name)
        statement.setString(2, meal.rezeptLink)
        statement.setString(3, json.encodeToString(meal.tags))
        statement.setBoolean(4, meal.istIdee)
        statement.setString(5, meal.id)
        statement.executeUpdate() > 0
    }

    @Synchronized fun deleteIdea(mealId: String): DeleteIdeaResult = transaction {
        val entryCount = connection.prepareStatement("SELECT COUNT(*) FROM mahlzeit_eintraege WHERE mahlzeit_id = ?").use { statement ->
            statement.setString(1, mealId)
            statement.executeQuery().use { rows -> rows.next(); rows.getInt(1) }
        }
        if (entryCount > 0) return@transaction DeleteIdeaResult.HAS_ENTRIES
        val deleted = connection.prepareStatement("DELETE FROM mahlzeiten WHERE id = ?").use { statement ->
            statement.setString(1, mealId)
            statement.executeUpdate()
        }
        if (deleted > 0) DeleteIdeaResult.DELETED else DeleteIdeaResult.NOT_FOUND
    }

    @Synchronized fun insertEntry(mealId: String, entry: MahlzeitEintrag): Boolean {
        if (find(mealId) == null) return false
        if (awayDateOccupied(entry.datum)) throw DateBlockedException()
        insertEntryRow(mealId, entry)
        return true
    }

    @Synchronized fun updateEntry(mealId: String, entry: MahlzeitEintrag): Boolean = connection.prepareStatement(
        "UPDATE mahlzeit_eintraege SET datum = ?, bewertung = ? WHERE id = ? AND mahlzeit_id = ?",
    ).use { statement ->
        if (awayDateOccupied(entry.datum)) throw DateBlockedException()
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
        return Mahlzeit(mealId, getString("name"), getString("rezept_link"), json.decodeFromString(getString("tags")), getBoolean("ist_idee"), entries(mealId))
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
        statement.setBoolean(5, meal.istIdee)
    }

    private fun awayDateOccupied(date: String, excludingId: String? = null): Boolean = connection.prepareStatement(
        "SELECT 1 FROM auswaerts_eintraege WHERE datum = ? AND (? IS NULL OR id <> ?)",
    ).use { statement ->
        statement.setString(1, date); statement.setString(2, excludingId); statement.setString(3, excludingId)
        statement.executeQuery().use { it.next() }
    }

    private fun mealCountOn(date: String): Int = connection.prepareStatement(
        "SELECT COUNT(*) FROM mahlzeit_eintraege WHERE datum = ?",
    ).use { statement -> statement.setString(1, date); statement.executeQuery().use { it.next(); it.getInt(1) } }

    private fun removeMealsOn(date: String): List<String> {
        val images = connection.prepareStatement(
            "SELECT b.url FROM mahlzeit_bilder b JOIN mahlzeit_eintraege e ON e.id = b.eintrag_id WHERE e.datum = ?",
        ).use { statement -> statement.setString(1, date); statement.executeQuery().use { rows -> buildList {
            while (rows.next()) add(rows.getString("url"))
        } } }
        connection.prepareStatement("DELETE FROM mahlzeit_eintraege WHERE datum = ?").use { statement ->
            statement.setString(1, date); statement.executeUpdate()
        }
        connection.createStatement().use { it.executeUpdate("DELETE FROM mahlzeiten WHERE ist_idee = 0 AND NOT EXISTS (SELECT 1 FROM mahlzeit_eintraege WHERE mahlzeit_id = mahlzeiten.id)") }
        return images
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
        if ("ist_idee" !in columns) connection.createStatement().use { it.executeUpdate("ALTER TABLE mahlzeiten ADD COLUMN ist_idee INTEGER NOT NULL DEFAULT 0") }
        createSchema()
        setSchemaVersion()
    }

    private fun createSchema() {
        connection.createStatement().use { statement ->
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS mahlzeiten (id TEXT PRIMARY KEY, name TEXT NOT NULL, rezept_link TEXT, tags TEXT NOT NULL, ist_idee INTEGER NOT NULL DEFAULT 0)")
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS mahlzeit_eintraege (id TEXT PRIMARY KEY, mahlzeit_id TEXT NOT NULL, datum TEXT NOT NULL, bewertung TEXT, FOREIGN KEY (mahlzeit_id) REFERENCES mahlzeiten(id) ON DELETE CASCADE, UNIQUE (mahlzeit_id, datum))")
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS mahlzeit_bilder (id TEXT PRIMARY KEY, eintrag_id TEXT NOT NULL, url TEXT NOT NULL, FOREIGN KEY (eintrag_id) REFERENCES mahlzeit_eintraege(id) ON DELETE CASCADE)")
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS auswaerts_eintraege (id TEXT PRIMARY KEY, datum TEXT NOT NULL UNIQUE, notiz TEXT)")
        }
    }

    private fun tableColumns(table: String): Set<String> = connection.createStatement().use { statement ->
        statement.executeQuery("PRAGMA table_info($table)").use { rows -> buildSet { while (rows.next()) add(rows.getString("name")) } }
    }

    private fun setSchemaVersion() { connection.createStatement().use { it.execute("PRAGMA user_version = 4") } }

    private fun <T> transaction(block: () -> T): T {
        val previousAutoCommit = connection.autoCommit
        connection.autoCommit = false
        return try { block().also { connection.commit() } } catch (error: Throwable) {
            connection.rollback()
            throw error
        } finally { connection.autoCommit = previousAutoCommit }
    }
}
