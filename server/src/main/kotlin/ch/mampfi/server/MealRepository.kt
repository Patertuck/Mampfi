package ch.mampfi.server

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.sql.DriverManager

class MealRepository(database: String) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val connection = DriverManager.getConnection("jdbc:sqlite:$database")
    init { connection.createStatement().use { it.executeUpdate("""CREATE TABLE IF NOT EXISTS mahlzeiten (
        id TEXT PRIMARY KEY, name TEXT NOT NULL, rezept_link TEXT, tags TEXT NOT NULL, termine TEXT NOT NULL,
        bilder TEXT NOT NULL, bewertungen TEXT NOT NULL)""") } }
    @Synchronized fun all(): List<Mahlzeit> = connection.prepareStatement("SELECT * FROM mahlzeiten ORDER BY name COLLATE NOCASE").use { s ->
        s.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.meal()) } }
    }
    @Synchronized fun find(id: String): Mahlzeit? = connection.prepareStatement("SELECT * FROM mahlzeiten WHERE id=?").use { s ->
        s.setString(1, id); s.executeQuery().use { if (it.next()) it.meal() else null }
    }
    @Synchronized fun insert(meal: Mahlzeit) { connection.prepareStatement("INSERT INTO mahlzeiten VALUES (?, ?, ?, ?, ?, ?, ?)").use { s -> bind(s, meal); s.executeUpdate() } }
    @Synchronized fun update(meal: Mahlzeit): Boolean = connection.prepareStatement("UPDATE mahlzeiten SET name=?, rezept_link=?, tags=?, termine=?, bilder=?, bewertungen=? WHERE id=?").use { s ->
        s.setString(1, meal.name); s.setString(2, meal.rezeptLink); s.setString(3, json.encodeToString(meal.tags)); s.setString(4, json.encodeToString(meal.termine)); s.setString(5, json.encodeToString(meal.bilder)); s.setString(6, json.encodeToString(meal.bewertungen)); s.setString(7, meal.id); s.executeUpdate() > 0 }
    @Synchronized fun delete(id: String) { connection.prepareStatement("DELETE FROM mahlzeiten WHERE id=?").use { it.setString(1, id); it.executeUpdate() } }
    private fun bind(s: java.sql.PreparedStatement, m: Mahlzeit) { s.setString(1,m.id); s.setString(2,m.name); s.setString(3,m.rezeptLink); s.setString(4,json.encodeToString(m.tags)); s.setString(5,json.encodeToString(m.termine)); s.setString(6,json.encodeToString(m.bilder)); s.setString(7,json.encodeToString(m.bewertungen)) }
    private fun java.sql.ResultSet.meal() = Mahlzeit(getString("id"), getString("name"), getString("rezept_link"), json.decodeFromString(getString("tags")), json.decodeFromString(getString("termine")), json.decodeFromString(getString("bilder")), json.decodeFromString(getString("bewertungen")))
}
