@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package ch.mampfi.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.mampfi.app.data.*
import coil3.compose.AsyncImage
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun MealDetailScreen(
    vm: MealViewModel,
    mealId: String,
    firstRaterName: String,
    secondRaterName: String,
    back: () -> Unit,
    edit: (String) -> Unit,
    schedule: (String) -> Unit,
) {
    val meal = vm.meals.collectAsState().value.find { it.id == mealId }
    var galleryImageUrl by remember { mutableStateOf<String?>(null) }
    if (meal == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val uriHandler = LocalUriHandler.current
    val recipeUrl = normalizedWebUrlOrNull(meal.rezeptLink.orEmpty())
    val history = remember(meal.eintraege) { meal.eintraege.sortedByDescending { it.datum } }
    var historyExpanded by rememberSaveable(meal.id) { mutableStateOf(false) }
    val visibleHistory = if (historyExpanded) history else history.take(5)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(meal.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Zurück") } },
            )
        },
    ) { scaffoldPadding -> LazyColumn(
        modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { schedule(meal.id) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Einplanen")
                }
                OutlinedButton(onClick = { edit(meal.id) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Edit, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Bearbeiten")
                }
            }
        }
        item {
            FormSection("Mahlzeit", trailing = { DietMarker(meal) }) {
                meal.tags.filterNot { it == Tag.VEGETARISCH.name || it == Tag.VEGAN.name }.takeIf { it.isNotEmpty() }?.let { tags ->
                    Text(tags.joinToString(" · ") { value -> Tag.entries.find { it.name == value }?.label ?: value }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = recipeUrl != null) { recipeUrl?.let { runCatching { uriHandler.openUri(it) } } },
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            meal.rezeptLink?.takeIf { it.isNotBlank() } ?: "Kein Rezept-Link",
                            Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (recipeUrl == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                            textDecoration = if (recipeUrl == null) null else TextDecoration.Underline,
                        )
                    }
                }
                meal.notiz?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        item {
            FormSection("Statistik") {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    maxItemsInEachRow = 2,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MealStat("Gekocht", meal.eintraege.size.toString(), modifier = Modifier.weight(1f))
                    MealStat("Ø gesamt", formatRating(meal.durchschnitt()), modifier = Modifier.weight(1f))
                    MealStat(firstRaterName, formatRating(meal.durchschnittFuer(0)), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    MealStat(secondRaterName, formatRating(meal.durchschnittFuer(1)), MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                    MealStat("Zum ersten Mal", meal.ersterTermin()?.let(::formatImageDate) ?: "–", modifier = Modifier.weight(1f))
                    MealStat("Zuletzt", meal.letzterTermin()?.let(::formatImageDate) ?: "–", modifier = Modifier.weight(1f))
                }
            }
        }
        item { Text("Kochhistorie", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
        items(visibleHistory, key = { it.id }) { occurrence ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth()) {
                    occurrence.bilder.firstOrNull()?.let { leadImage ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clickable { galleryImageUrl = leadImage.url },
                        ) {
                            AsyncImage(
                                leadImage.url,
                                "Fotos vom ${formatImageDate(occurrence.datum)} ansehen",
                                Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                            val additionalImages = occurrence.bilder.size - 1
                            if (additionalImages > 0) Surface(
                                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f),
                                contentColor = Color.White,
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Text("+$additionalImages", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(formatHistoryDate(occurrence.datum), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                                Text(
                                    occurrence.durchschnitt()?.let { "${formatRating(it)} / 10" } ?: "Nicht bewertet",
                                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HistoryRatingChip(firstRaterName, occurrence.bewertung?.werte?.getOrNull(0), MaterialTheme.colorScheme.primary)
                            HistoryRatingChip(secondRaterName, occurrence.bewertung?.werte?.getOrNull(1), MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
            }
        }
        if (history.size > 5) item {
            TextButton(onClick = { historyExpanded = !historyExpanded }, modifier = Modifier.fillMaxWidth()) {
                Text(if (historyExpanded) "Weniger anzeigen" else "Alle ${history.size} Einträge anzeigen")
            }
        }
    } }
    galleryImageUrl?.let { imageUrl -> MealImageGallery(meal, imageUrl) { galleryImageUrl = null } }
}

@Composable
private fun MealStat(label: String, value: String, accent: Color = MaterialTheme.colorScheme.primary, modifier: Modifier = Modifier) = Surface(
    modifier = modifier,
    color = MaterialTheme.colorScheme.surfaceVariant,
    shape = MaterialTheme.shapes.small,
) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accent)
    }
}

private fun formatRating(value: Double?): String = value?.let { String.format(Locale.GERMANY, "%.1f", it) } ?: "–"

private fun formatHistoryDate(value: String): String = runCatching {
    LocalDate.parse(value).format(DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN))
}.getOrDefault(value)

@Composable
private fun HistoryRatingChip(name: String, rating: Double?, accent: Color) = Surface(
    color = accent.copy(alpha = 0.14f),
    contentColor = accent,
    shape = MaterialTheme.shapes.small,
) {
    Text(
        "$name: ${formatRating(rating)}",
        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.labelLarge,
    )
}
