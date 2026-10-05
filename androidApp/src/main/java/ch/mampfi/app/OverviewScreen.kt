@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package ch.mampfi.app

import androidx.compose.foundation.clickable
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.mampfi.app.data.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun OverviewScreen(meals: List<Mahlzeit>, open: (Mahlzeit) -> Unit, setIdea: (Mahlzeit, Boolean) -> Unit, isRefreshing: Boolean, refresh: () -> Unit) {
    var selectedTagNames by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var query by rememberSaveable { mutableStateOf("") }
    var sortName by rememberSaveable { mutableStateOf(MealSort.LATEST.name) }
    var filterSheetVisible by rememberSaveable { mutableStateOf(false) }
    var galleryMeal by remember { mutableStateOf<Mahlzeit?>(null) }
    val selected = selectedTagNames.mapNotNull { name -> Tag.entries.find { it.name == name } }.toSet()
    val sort = runCatching { MealSort.valueOf(sortName) }.getOrDefault(MealSort.LATEST)
    val filtered = sortMeals(filterMeals(meals, query, selected), sort)
    val activeCount = selected.size + if (sort != MealSort.LATEST) 1 else 0
    val gridState = rememberLazyGridState()
    fun updateSelected(tags: Set<Tag>) { selectedTagNames = tags.map { it.name }.sorted() }
    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = refresh, modifier = Modifier.fillMaxSize()) {
      Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Eure Mahlzeiten", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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
        FilterChip(
            selected = activeCount > 0,
            onClick = { filterSheetVisible = true },
            label = { Text(if (activeCount == 0) "Filter & Sortierung" else "Filter & Sortierung ($activeCount)") },
            leadingIcon = { Icon(Icons.Outlined.FilterAlt, contentDescription = null) },
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { filterSheetVisible = true }, label = { Text(sort.label) }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = null, Modifier.size(18.dp)) })
            selected.forEach { tag ->
                InputChip(
                    selected = true,
                    onClick = { updateSelected(selected - tag) },
                    label = { Text(tag.label) },
                    trailingIcon = { Icon(Icons.Outlined.Clear, contentDescription = "${tag.label} entfernen", Modifier.size(18.dp)) },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        if (filtered.isEmpty()) Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                if (meals.isEmpty()) MampfiEmptyMascot(Modifier.size(96.dp))
                else Icon(Icons.Outlined.SearchOff, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Text(if (meals.isEmpty()) "Noch keine Mahlzeiten" else "Keine passenden Mahlzeiten", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(if (meals.isEmpty()) "Plant im Kalender euer erstes Essen." else "Passt eure Suche oder Filter an.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (meals.isNotEmpty()) TextButton(onClick = { query = ""; updateSelected(emptySet()); sortName = MealSort.LATEST.name }) { Text("Suche und Filter zurücksetzen") }
            }
        } else LazyVerticalGrid(GridCells.Adaptive(164.dp), state = gridState, verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(filtered, key = { it.id }) { meal -> MealCard(meal, { open(meal) }, { setIdea(meal, !meal.istIdee) }) { galleryMeal = meal } } }
      }
    }
    if (filterSheetVisible) OverviewFilterSheet(
        selected = selected,
        sort = sort,
        apply = { tags, selectedSort -> updateSelected(tags); sortName = selectedSort.name; filterSheetVisible = false },
        dismiss = { filterSheetVisible = false },
    )
    galleryMeal?.let { meal -> MealImageGallery(meal) { galleryMeal = null } }
}

@Composable
private fun OverviewFilterSheet(selected: Set<Tag>, sort: MealSort, apply: (Set<Tag>, MealSort) -> Unit, dismiss: () -> Unit) {
    var editingTags by remember(selected) { mutableStateOf(selected) }
    var editingSort by remember(sort) { mutableStateOf(sort) }
    MampfiFilterDialog(
        title = "Filter & Sortierung",
        dismiss = dismiss,
        content = {
            Text("Eigenschaften", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Tag.entries.forEach { tag -> FilterChip(tag in editingTags, { editingTags = editingTags.toggle(tag) }, { Text(tag.label) }) }
            }
            Text("Reihenfolge", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MealSort.entries.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { editingSort = option }.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = editingSort == option, onClick = { editingSort = option })
                        Text(option.label, Modifier.padding(start = 6.dp))
                    }
                }
            }
        },
        actions = {
            TextButton(onClick = { apply(emptySet(), MealSort.LATEST) }, modifier = Modifier.weight(1f)) { Text("Zurücksetzen") }
            Button(onClick = { apply(editingTags, editingSort) }, modifier = Modifier.weight(1f)) { Text("Anwenden") }
        },
    )
}

internal fun Set<Tag>.toggle(item: Tag) = if (item in this) this - item else this + item

@Composable
private fun MealCard(meal: Mahlzeit, click: () -> Unit, toggleIdea: () -> Unit, openGallery: () -> Unit) = Card(Modifier.fillMaxWidth().animateContentSize().clickable(onClick = click), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
    val lastCooked = meal.letzterTermin()?.let { date ->
        runCatching { LocalDate.parse(date).format(DateTimeFormatter.ofPattern("dd.MM.yy")) }.getOrNull()
    } ?: "–"
    Column {
        MealThumbnail(
            imageUrl = meal.letztesBild(),
            contentDescription = meal.letztesBild()?.let { "Bild von ${meal.name} ansehen" },
            modifier = Modifier.fillMaxWidth().height(136.dp),
            openGallery = if (meal.letztesBild() != null) openGallery else null,
        )
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(meal.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                DietMarker(meal, Modifier.padding(start = 8.dp))
                IconButton(onClick = toggleIdea, modifier = Modifier.size(48.dp)) {
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
    }
}
