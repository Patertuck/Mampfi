package ch.mampfi.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.UUID

enum class Tag(val label: String) { VEGETARISCH("Vegetarisch"), VEGAN("Vegan"), DESSERT("Dessert"), AUFWANDIG("Aufwändig") }
@Serializable data class MahlzeitBild(val id: String = UUID.randomUUID().toString(), val url: String)
@Serializable data class MahlzeitBewertung(val werte: List<Double>)
@Serializable data class AuswaertsEintrag(
    val id: String = UUID.randomUUID().toString(), val datum: String, val notiz: String? = null,
)
@Serializable data class MahlzeitEintrag(
    val id: String = UUID.randomUUID().toString(), val datum: String,
    val bilder: List<MahlzeitBild> = emptyList(), val bewertung: MahlzeitBewertung? = null,
) {
    fun durchschnitt() = bewertung?.werte?.takeIf { it.isNotEmpty() }?.average()
    fun letztesBild() = bilder.lastOrNull()?.url
}
data class DatiertesMahlzeitBild(val url: String, val datum: String)
@Serializable data class Mahlzeit(
    val id: String = UUID.randomUUID().toString(), val name: String, val rezeptLink: String? = null,
    val tags: List<String> = emptyList(), val istIdee: Boolean = false, val eintraege: List<MahlzeitEintrag> = emptyList(),
    val notiz: String? = null,
) {
    val termine get() = eintraege.map { it.datum }
    val bilder get() = eintraege.flatMap { entry -> entry.bilder.map { DatiertesMahlzeitBild(it.url, entry.datum) } }
    fun durchschnitt() = eintraege.mapNotNull { it.bewertung }.flatMap { it.werte }.takeIf { it.isNotEmpty() }?.average()
    fun letztesBild() = bilder.maxByOrNull { it.datum }?.url
    fun letzterTermin() = termine.maxOrNull()
    fun hatTag(tag: Tag) = when (tag) {
        Tag.VEGETARISCH -> Tag.VEGETARISCH.name in tags || Tag.VEGAN.name in tags
        else -> tag.name in tags
    }
}

internal fun Set<Tag>.normalizedDietTags() = if (Tag.VEGAN in this) this - Tag.VEGETARISCH else this

internal fun Set<Tag>.toggleMealTag(tag: Tag): Set<Tag> {
    if (tag in this) return this - tag
    return when (tag) {
        Tag.VEGAN -> (this - Tag.VEGETARISCH) + Tag.VEGAN
        Tag.VEGETARISCH -> (this - Tag.VEGAN) + Tag.VEGETARISCH
        else -> this + tag
    }
}

@Entity(tableName = "mahlzeiten")
data class MahlzeitEntity(@PrimaryKey val id: String, val json: String)

@Entity(tableName = "auswaerts_eintraege")
data class AuswaertsEintragEntity(@PrimaryKey val id: String, val datum: String, val notiz: String?)

class MealConverters {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    @TypeConverter fun mealToString(value: Mahlzeit) = json.encodeToString(Mahlzeit.serializer(), value)
    @TypeConverter fun stringToMeal(value: String) = json.decodeFromString(Mahlzeit.serializer(), value)
}
