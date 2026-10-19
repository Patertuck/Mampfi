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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ShoppingCart
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
import java.util.UUID
import java.math.BigDecimal

@Composable
internal fun MealDetailScreen(
    vm: MealViewModel,
    groceryVm: GroceryViewModel,
    mealId: String,
    firstRaterName: String,
    secondRaterName: String,
    back: () -> Unit,
    edit: (String) -> Unit,
    schedule: (String) -> Unit,
) {
    val meal = vm.meals.collectAsState().value.find { it.id == mealId }
    var galleryImageUrl by remember { mutableStateOf<String?>(null) }
    var ingredientEditorVisible by remember { mutableStateOf(false) }
    var groceryReviewVisible by remember { mutableStateOf(false) }
    var reloadConfirmationVisible by remember { mutableStateOf(false) }
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
            FormSection("Zutaten") {
                RecipeIngredientSummary(meal)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ ingredientEditorVisible = true }, Modifier.weight(1f)) { Icon(Icons.Outlined.Edit, null); Spacer(Modifier.width(6.dp)); Text("Bearbeiten") }
                    OutlinedButton({ if (meal.ingredients.isEmpty()) vm.extractIngredients(meal) else reloadConfirmationVisible = true }, enabled = recipeUrl != null, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Laden") }
                }
                Button({ groceryReviewVisible = true }, enabled = meal.ingredients.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.ShoppingCart, null); Spacer(Modifier.width(6.dp)); Text("Zum Einkauf")
                }
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
    if (ingredientEditorVisible) IngredientEditorDialog(meal, { ingredientEditorVisible = false }) { updated ->
        vm.updateMeal(updated) { if (it) ingredientEditorVisible = false }
    }
    if (groceryReviewVisible) GroceryReviewDialog(meal, { groceryReviewVisible = false }) { selections ->
        groceryVm.addRecipeIngredients(selections) { if (it) groceryReviewVisible = false }
    }
    if (reloadConfirmationVisible) AlertDialog(
        onDismissRequest = { reloadConfirmationVisible = false },
        title = { Text("Zutaten ersetzen?") },
        text = { Text("Die gespeicherten Zutaten und deine Änderungen werden durch die neu geladenen Angaben ersetzt.") },
        confirmButton = { TextButton({ reloadConfirmationVisible = false; vm.extractIngredients(meal) }) { Text("Ersetzen") } },
        dismissButton = { TextButton({ reloadConfirmationVisible = false }) { Text("Abbrechen") } },
    )
}

@Composable
internal fun RecipeIngredientSummary(meal: Mahlzeit) {
    if (meal.ingredients.isEmpty()) Text("Noch keine Zutaten erfasst.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    meal.recipeServings?.let { Text("Grundmenge für $it Personen", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
    meal.ingredients.forEach { ingredient ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(ingredient.displayAmount(), Modifier.widthIn(min = 72.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(ingredient.name, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun IngredientEditorDialog(meal: Mahlzeit, dismiss: () -> Unit, save: (Mahlzeit) -> Unit) {
    var servings by remember(meal.id, meal.recipeServings) { mutableStateOf(meal.recipeServings?.toString().orEmpty()) }
    var ingredients by remember(meal.id, meal.ingredients) { mutableStateOf(meal.ingredients) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Zutaten bearbeiten") },
        text = {
            Column(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(servings, { servings = it.filter(Char::isDigit) }, label = { Text("Personen") }, singleLine = true)
                LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(ingredients, key = { it.id }) { ingredient ->
                        IngredientEditRow(
                            ingredient,
                            change = { changed -> ingredients = ingredients.map { if (it.id == changed.id) changed else it } },
                            delete = { ingredients = ingredients.filterNot { it.id == ingredient.id } },
                        )
                    }
                }
                TextButton({ ingredients = ingredients + RecipeIngredient(name = "") }) { Icon(Icons.Outlined.Add, null); Text("Zutat hinzufügen") }
            }
        },
        confirmButton = {
            TextButton({ save(meal.copy(recipeServings = servings.toIntOrNull(), ingredients = ingredients.filter { it.name.isNotBlank() })) }, enabled = servings.toIntOrNull()?.let { it > 0 } == true || ingredients.isEmpty()) { Text("Speichern") }
        },
        dismissButton = { TextButton(dismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun IngredientEditRow(ingredient: RecipeIngredient, change: (RecipeIngredient) -> Unit, delete: () -> Unit) {
    var unitMenu by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(ingredient.amount.orEmpty(), { change(ingredient.copy(amount = it.ifBlank { null })) }, label = { Text("Menge") }, modifier = Modifier.width(88.dp), singleLine = true)
            OutlinedTextField(ingredient.name, { change(ingredient.copy(name = it)) }, label = { Text("Zutat") }, modifier = Modifier.weight(1f), singleLine = true)
            IconButton(delete) { Icon(Icons.Outlined.Delete, "Löschen") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box {
                OutlinedButton({ unitMenu = true }) { Text(ingredient.unitLabel()) }
                DropdownMenu(unitMenu, { unitMenu = false }) { GroceryUnit.entries.forEach { unit ->
                    DropdownMenuItem({ Text(unit.label) }, { change(ingredient.copy(unit = unit, customUnit = null)); unitMenu = false })
                } }
            }
            Box {
                OutlinedButton({ categoryMenu = true }) { Text(ingredient.category.label) }
                DropdownMenu(categoryMenu, { categoryMenu = false }) { GroceryCategory.entries.forEach { category ->
                    DropdownMenuItem({ Text(category.label) }, { change(ingredient.copy(category = category)); categoryMenu = false })
                } }
            }
        }
        if (ingredient.unit == GroceryUnit.CUSTOM) OutlinedTextField(
            ingredient.customUnit.orEmpty(), { change(ingredient.copy(customUnit = it.ifBlank { null })) },
            label = { Text("Eigene Einheit") }, singleLine = true,
        )
    }
}

@Composable
internal fun GroceryReviewDialog(meal: Mahlzeit, dismiss: () -> Unit, add: (List<RecipeGrocerySelection>) -> Unit) {
    val baseServings = meal.recipeServings
    var targetServings by remember(meal.id) { mutableStateOf(baseServings?.toString().orEmpty()) }
    var selected by remember(meal.id) { mutableStateOf(meal.ingredients.map { it.id }.toSet()) }
    var overrides by remember(meal.id, targetServings) { mutableStateOf(emptyMap<String, String>()) }
    val factor = if (baseServings != null && baseServings > 0) targetServings.toBigDecimalOrNull()?.divide(baseServings.toBigDecimal(), 6, java.math.RoundingMode.HALF_UP) else null
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Zum Einkauf hinzufügen") },
        text = {
            Column(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(targetServings, { targetServings = it.filter(Char::isDigit) }, label = { Text("Personen") }, singleLine = true, isError = factor == null)
                if (baseServings == null) Text("Lege zuerst in 'Bearbeiten' fest, für wie viele Personen die Grundmengen gelten.", color = MaterialTheme.colorScheme.error)
                LazyColumn(Modifier.weight(1f, fill = false)) { items(meal.ingredients, key = { it.id }) { ingredient ->
                    val scaled = ingredient.amount?.decimalOrNull()?.let { amount -> factor?.let { amount.multiply(it).stripTrailingZeros().toPlainString() } }
                    val shown = overrides[ingredient.id] ?: scaled.orEmpty()
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(ingredient.id in selected, { checked -> selected = if (checked) selected + ingredient.id else selected - ingredient.id })
                        OutlinedTextField(shown, { value -> overrides = overrides + (ingredient.id to value) }, modifier = Modifier.width(82.dp), label = { Text(ingredient.unitLabel()) }, singleLine = true, enabled = ingredient.amount != null)
                        Text(ingredient.name, Modifier.weight(1f).padding(start = 8.dp), maxLines = 2)
                    }
                } }
            }
        },
        confirmButton = {
            TextButton({ add(meal.ingredients.filter { it.id in selected }.mapNotNull { it.toGrocerySelection(overrides[it.id] ?: it.amount?.decimalOrNull()?.let { amount -> factor?.let(amount::multiply)?.stripTrailingZeros()?.toPlainString() }) }) }, enabled = factor != null && selected.isNotEmpty()) { Text("Hinzufügen") }
        },
        dismissButton = { TextButton(dismiss) { Text("Abbrechen") } },
    )
}

private fun RecipeIngredient.toGrocerySelection(value: String?): RecipeGrocerySelection? {
    val numeric = value?.replace(',', '.')?.toDoubleOrNull()
    if (amount != null && (numeric == null || numeric <= 0)) return null
    val multiplier = when (unit) { GroceryUnit.KG, GroceryUnit.L -> 1000.0; GroceryUnit.DL -> 100.0; else -> 1.0 }
    val key = name.trim().lowercase(Locale.GERMAN).replace(Regex("[^a-z0-9äöüéèà]+"), "-").trim('-')
    return RecipeGrocerySelection(
        GrocerySuggestion(GroceryProductRef(GrocerySource.RECIPE, "recipe:$key", name.trim()), category),
        (numeric ?: 0.0) * multiplier, unit, customUnit, amount != null,
    )
}

private fun RecipeIngredient.unitLabel() = if (unit == GroceryUnit.CUSTOM) customUnit ?: "Einheit" else unit.label
private fun RecipeIngredient.displayAmount() = amount?.let { "$it ${unitLabel()}" } ?: (customUnit ?: "nach Bedarf")
private fun String.decimalOrNull() = replace(',', '.').toBigDecimalOrNull()

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
