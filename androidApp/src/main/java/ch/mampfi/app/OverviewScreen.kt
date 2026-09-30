@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package ch.mampfi.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.mampfi.app.data.*
import coil3.compose.AsyncImage
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun OverviewScreen(meals: List<Mahlzeit>, open: (Mahlzeit) -> Unit, setIdea: (Mahlzeit, Boolean) -> Unit) {
    var selected by remember { mutableStateOf(setOf<Tag>()) }
    var query by rememberSaveable { mutableStateOf("") }
    var criterion by remember { mutableStateOf("Zuletzt gekocht") }
    var ascending by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var galleryMeal by remember { mutableStateOf<Mahlzeit?>(null) }
    val normalizedQuery = query.trim()
    val filtered = meals.filter { meal ->
        meal.name.contains(normalizedQuery, ignoreCase = true) && selected.all(meal::hatTag)
    }.sortedWith(compareBy<Mahlzeit> { when (criterion) { "Bewertung" -> it.durchschnitt() ?: -1.0; "Am häufigsten gekocht" -> it.termine.size; else -> it.letzterTermin() ?: "" } }.let { if (ascending) it else it.reversed() })
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Deine Mahlzeiten", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Finde schnell, worauf ihr Lust habt.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Mahlzeiten durchsuchen") },
            placeholder = { Text("Nach Namen suchen") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Outlined.Clear, contentDescription = "Suche löschen")
                    }
                }
            } else null,
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Tag.entries.forEach { tag -> FilterChip(tag in selected, { selected = selected.toggle(tag) }, { Text(tag.label) }) } }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box { AssistChip({ menu = true }, { Text(criterion) }); DropdownMenu(menu, { menu = false }) { listOf("Zuletzt gekocht", "Bewertung", "Am häufigsten gekocht").forEach { option -> DropdownMenuItem({ Text(option) }, { criterion = option; menu = false }) } } }
            AssistChip({ ascending = !ascending }, { Text(if (ascending) "Aufsteigend" else "Absteigend") })
        }
        Spacer(Modifier.height(14.dp))
        if (filtered.isEmpty()) Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(if (meals.isEmpty()) Icons.Outlined.CalendarMonth else Icons.Outlined.SearchOff, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Text(if (meals.isEmpty()) "Noch keine Mahlzeiten" else "Keine passenden Mahlzeiten", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(if (meals.isEmpty()) "Plane im Kalender euer erstes Essen." else "Passe deine Suche oder Filter an.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else LazyVerticalGrid(GridCells.Adaptive(164.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(filtered, key = { it.id }) { meal -> MealCard(meal, { open(meal) }, { setIdea(meal, !meal.istIdee) }) { galleryMeal = meal } } }
    }
    galleryMeal?.let { meal -> MealImageGallery(meal) { galleryMeal = null } }
}

internal fun Set<Tag>.toggle(item: Tag) = if (item in this) this - item else this + item

@Composable
private fun MealCard(meal: Mahlzeit, click: () -> Unit, toggleIdea: () -> Unit, openGallery: () -> Unit) = Card(Modifier.fillMaxWidth().clickable(onClick = click), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
    val lastCooked = meal.letzterTermin()?.let { date ->
        runCatching { LocalDate.parse(date).format(DateTimeFormatter.ofPattern("dd.MM.yy")) }.getOrNull()
    } ?: "–"
    Column {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(meal.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                DietMarker(meal, Modifier.padding(start = 8.dp))
                IconButton(onClick = toggleIdea, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (meal.istIdee) Icons.Filled.Lightbulb else Icons.Outlined.Lightbulb,
                        contentDescription = if (meal.istIdee) "Aus Ideen entfernen" else "Zu Ideen hinzufügen",
                        tint = if (meal.istIdee) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                    Text(meal.durchschnitt()?.let { String.format(Locale.GERMANY, "%.1f / 10", it) } ?: "– / 10", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Text("${meal.eintraege.size}×", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = "Zuletzt gekocht", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(lastCooked, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            meal.notiz?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        meal.letztesBild()?.let { image ->
            AsyncImage(
                model = image,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clickable(onClick = openGallery),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
