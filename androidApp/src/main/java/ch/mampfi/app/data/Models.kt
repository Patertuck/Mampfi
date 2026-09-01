package ch.mampfi.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.UUID

enum class Tag(val label: String) { VEGETARISCH("Vegetarisch"), VEGAN("Vegan"), DESSERT("Dessert"), AUFWANDIG("Aufwändig") }
@Serializable data class MahlzeitBild(val url: String, val datum: String)
@Serializable data class MahlzeitBewertung(val werte: List<Double>, val datum: String)
@Serializable data class Mahlzeit(
    val id: String = UUID.randomUUID().toString(), val name: String, val rezeptLink: String? = null,
    val tags: List<String> = emptyList(), val termine: List<String> = emptyList(),
    val bilder: List<MahlzeitBild> = emptyList(), val bewertungen: List<MahlzeitBewertung> = emptyList()
) {
    fun durchschnitt() = bewertungen.flatMap { it.werte }.takeIf { it.isNotEmpty() }?.average()
    fun letztesBild() = bilder.maxByOrNull { it.datum }?.url
    fun letzterTermin() = termine.maxOrNull()
    fun hatTag(tag: Tag) = tag.name in tags
}

@Entity(tableName = "mahlzeiten")
data class MahlzeitEntity(@PrimaryKey val id: String, val json: String)

class MealConverters {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    @TypeConverter fun mealToString(value: Mahlzeit) = json.encodeToString(Mahlzeit.serializer(), value)
    @TypeConverter fun stringToMeal(value: String) = json.decodeFromString(Mahlzeit.serializer(), value)
}
