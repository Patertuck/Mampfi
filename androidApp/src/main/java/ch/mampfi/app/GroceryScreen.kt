@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package ch.mampfi.app

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import ch.mampfi.app.data.*
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.Locale

private val categoryOrder = GroceryCategory.entries.withIndex().associate { it.value to it.index }

@Composable
internal fun GroceryScreen(vm: GroceryViewModel) {
    val allItems by vm.items.collectAsState()
    val suggestions by vm.suggestions.collectAsState()
    val active = remember(allItems) { allItems.filter { it.completedAt == null }.sortedWith(compareBy({ categoryOrder[it.category] }, { it.product.name.lowercase() })) }
    val history = remember(allItems) { allItems.filter { it.completedAt != null }.sortedByDescending { it.completedAt } }
    var query by rememberSaveable { mutableStateOf("") }
    var historyVisible by rememberSaveable { mutableStateOf(false) }
    var draft by remember { mutableStateOf<GrocerySuggestion?>(null) }
    var unknownBarcode by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<GroceryItem?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val activity = LocalContext.current as Activity
    val scanner = remember(activity) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_8, Barcode.FORMAT_EAN_13, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(activity, options)
    }
    val scanBarcode: () -> Unit = {
        scanner.startScan()
            .addOnSuccessListener { scanned ->
                val barcode = scanned.rawValue?.filter(Char::isDigit).orEmpty()
                if (barcode.isBlank()) vm.showMessage("Der Barcode konnte nicht gelesen werden.")
                else vm.lookupBarcode(barcode) { result ->
                    when (result) {
                        is GroceryBarcodeResult.Found -> draft = result.suggestion
                        GroceryBarcodeResult.NotFound -> unknownBarcode = barcode
                        GroceryBarcodeResult.Unavailable -> vm.showMessage("Produktdaten sind gerade nicht erreichbar.")
                    }
                }
            }
            .addOnFailureListener { error ->
                if ((error as? ApiException)?.statusCode != CommonStatusCodes.CANCELED) {
                    vm.showMessage("Scanner konnte nicht gestartet werden.")
                }
            }
        Unit
    }

    LaunchedEffect(Unit) { vm.message.collect { snackbar.showSnackbar(it) } }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (historyVisible) "Einkaufsverlauf" else "Einkaufsliste", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton({ historyVisible = !historyVisible }) {
                        Icon(if (historyVisible) Icons.Outlined.ShoppingCart else Icons.Outlined.History, if (historyVisible) "Liste" else "Verlauf")
                    }
                }
            }
            if (historyVisible) {
                if (history.isEmpty()) item { EmptyGroceries("Noch keine Einkäufe im Verlauf.") }
                history.groupBy { historyDate(it.completedAt!!) }.forEach { (date, datedItems) ->
                    item { Text(date, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
                    items(datedItems, key = { it.id }) { item -> HistoryRow(item) { vm.restore(item) } }
                }
            } else {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it; vm.search(it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Was brauchst du?") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        trailingIcon = {
                            Row {
                                if (query.isNotEmpty()) IconButton({ query = ""; vm.clearSearch() }) { Icon(Icons.Outlined.Close, "Leeren") }
                                IconButton(onClick = scanBarcode) { Icon(Icons.Outlined.QrCodeScanner, "Barcode scannen") }
                            }
                        },
                        singleLine = true,
                    )
                }
                if (query.trim().length >= 2) {
                    item {
                        SuggestionRow(
                            GrocerySuggestion(
                                GroceryProductRef(GrocerySource.MANUAL, manualKey(query), query.trim()),
                                GroceryCategory.OTHER,
                            ),
                            manual = true,
                        ) { draft = it }
                    }
                    items(suggestions, key = { it.product.stableKey }) { suggestion -> SuggestionRow(suggestion, false) { draft = it } }
                    item {
                        TextButton(onClick = { uriHandler.openUri("https://world.openfoodfacts.org") }) {
                            Text("Produktdaten: Open Food Facts")
                        }
                    }
                    item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                }
                if (active.isEmpty() && query.isBlank()) item { EmptyGroceries("Die Einkaufsliste ist leer.") }
                active.groupBy { it.category }.forEach { (category, grouped) ->
                    item { Text(category.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
                    items(grouped, key = { it.id }) { item ->
                        GroceryRow(
                            item = item,
                            complete = {
                                vm.complete(item)
                                scope.launch {
                                    if (snackbar.showSnackbar("${item.product.name} gekauft", "Rückgängig", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) vm.restore(item)
                                }
                            },
                            edit = { editing = item },
                            delete = { vm.delete(item) },
                        )
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(12.dp))
    }

    unknownBarcode?.let { barcode ->
        ManualBarcodeDialog(
            barcode = barcode,
            dismiss = { unknownBarcode = null },
        ) { name ->
            draft = GrocerySuggestion(
                GroceryProductRef(
                    source = GrocerySource.MANUAL,
                    stableKey = "manual-barcode:$barcode",
                    name = name,
                    barcode = barcode,
                ),
                GroceryCategory.OTHER,
            )
            unknownBarcode = null
        }
    }

    draft?.let { suggestion ->
        GroceryAmountDialog(
            title = suggestion.product.name,
            initialCategory = suggestion.category,
            initialUnit = GroceryUnit.PIECE,
            initialQuantity = "1",
            allowedUnits = GroceryUnit.entries,
            confirmLabel = "Hinzufügen",
            dismiss = { draft = null },
        ) { quantity, unit, category ->
            vm.add(suggestion.copy(category = category), quantity, unit)
            query = ""; vm.clearSearch(); draft = null
        }
    }
    editing?.let { item ->
        GroceryAmountDialog(
            title = item.product.name,
            initialCategory = item.category,
            initialUnit = item.preferredUnit,
            initialQuantity = quantityInput(item),
            allowedUnits = GroceryUnit.entries.filter { it.dimension() == item.dimension },
            confirmLabel = "Speichern",
            dismiss = { editing = null },
        ) { quantity, unit, category ->
            vm.update(item.copy(baseQuantity = quantity, preferredUnit = unit, category = category))
            editing = null
        }
    }
}

@Composable private fun ManualBarcodeDialog(barcode: String, dismiss: () -> Unit, confirm: (String) -> Unit) {
    var name by remember(barcode) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Produkt nicht gefunden") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Barcode $barcode ist noch nicht in Open Food Facts. Gib dem Produkt einen Namen.")
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Produktname") },
                    singleLine = true,
                )
            }
        },
        confirmButton = { TextButton({ confirm(name.trim()) }, enabled = name.isNotBlank()) { Text("Weiter") } },
        dismissButton = { TextButton(dismiss) { Text("Abbrechen") } },
    )
}

@Composable private fun SuggestionRow(suggestion: GrocerySuggestion, manual: Boolean, select: (GrocerySuggestion) -> Unit) {
    GroceryProductRow(
        product = suggestion.product,
        modifier = Modifier.fillMaxWidth().clickable { select(suggestion) },
        headline = if (manual) "„${suggestion.product.name}“ manuell hinzufügen" else suggestion.product.name,
        details = listOfNotNull(suggestion.product.brand, suggestion.product.packageSize, suggestion.category.label),
        manual = manual,
    )
}

@Composable private fun GroceryRow(item: GroceryItem, complete: () -> Unit, edit: () -> Unit, delete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    GroceryProductRow(
        product = item.product,
        modifier = Modifier.fillMaxWidth().clickable(onClick = complete),
        headline = item.product.name,
        details = listOfNotNull(item.product.brand, item.product.packageSize, item.formattedQuantity(), item.category.label),
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(false, onCheckedChange = { complete() })
                Box {
                    IconButton({ menu = true }) { Icon(Icons.Outlined.MoreVert, "Aktionen") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("Bearbeiten") }, { menu = false; edit() }, leadingIcon = { Icon(Icons.Outlined.Edit, null) })
                        DropdownMenuItem({ Text("Löschen") }, { menu = false; delete() }, leadingIcon = { Icon(Icons.Outlined.Delete, null) })
                    }
                }
            }
        },
    )
}

@Composable private fun GroceryProductRow(
    product: GroceryProductRef,
    headline: String,
    details: List<String>,
    modifier: Modifier = Modifier,
    manual: Boolean = false,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    ListItem(
        modifier = modifier,
        headlineContent = { Text(headline, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            val detail = details.filter { it.isNotBlank() }.joinToString(" · ")
            if (detail.isNotBlank()) Text(detail, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = {
            if (!manual && product.imageUrl != null) AsyncImage(
                model = product.imageUrl,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
            ) else Icon(if (manual) Icons.Outlined.Add else Icons.Outlined.Restaurant, null)
        },
        trailingContent = trailingContent,
    )
}

@Composable private fun HistoryRow(item: GroceryItem, restore: () -> Unit) {
    ListItem(
        headlineContent = { Text(item.product.name) },
        supportingContent = { Text("${item.formattedQuantity()} · ${item.category.label}") },
        trailingContent = { IconButton(restore) { Icon(Icons.Outlined.Restore, "Wiederherstellen") } },
    )
}

@Composable private fun GroceryAmountDialog(
    title: String,
    initialCategory: GroceryCategory,
    initialUnit: GroceryUnit,
    initialQuantity: String,
    allowedUnits: List<GroceryUnit>,
    confirmLabel: String,
    dismiss: () -> Unit,
    confirm: (Long, GroceryUnit, GroceryCategory) -> Unit,
) {
    var amount by remember(title, initialQuantity) { mutableStateOf(initialQuantity) }
    var unit by remember(title, initialUnit) { mutableStateOf(initialUnit) }
    var category by remember(title, initialCategory) { mutableStateOf(initialCategory) }
    var unitMenu by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }
    val base = quantityToBase(amount, unit)
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(amount, { amount = it }, label = { Text("Menge") }, modifier = Modifier.weight(1f), singleLine = true, isError = amount.isNotBlank() && base == null)
                    ExposedDropdownMenuBox(unitMenu, { unitMenu = it }, Modifier.width(120.dp)) {
                        OutlinedTextField(unit.label, {}, readOnly = true, label = { Text("Einheit") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(unitMenu) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                        ExposedDropdownMenu(unitMenu, { unitMenu = false }) { allowedUnits.forEach { candidate -> DropdownMenuItem({ Text(candidate.label) }, { unit = candidate; unitMenu = false }) } }
                    }
                }
                ExposedDropdownMenuBox(categoryMenu, { categoryMenu = it }) {
                    OutlinedTextField(category.label, {}, readOnly = true, label = { Text("Kategorie") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryMenu) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(categoryMenu, { categoryMenu = false }) { GroceryCategory.entries.forEach { candidate -> DropdownMenuItem({ Text(candidate.label) }, { category = candidate; categoryMenu = false }) } }
                }
            }
        },
        confirmButton = { TextButton({ base?.let { confirm(it, unit, category) } }, enabled = base != null) { Text(confirmLabel) } },
        dismissButton = { TextButton(dismiss) { Text("Abbrechen") } },
    )
}

@Composable private fun EmptyGroceries(text: String) {
    Text(text, modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun manualKey(name: String) = "manual:" + name.trim().lowercase().replace(Regex("[^a-z0-9äöüéèà]+"), "-").trim('-').ifBlank { UUID.randomUUID().toString() }
private fun quantityInput(item: GroceryItem) = when (item.preferredUnit) {
    GroceryUnit.KG, GroceryUnit.L -> (item.baseQuantity / 1000.0).toString().replace('.', ',')
    else -> item.baseQuantity.toString()
}
private fun historyDate(value: String): String = Instant.parse(value).atZone(ZoneId.of("Europe/Zurich")).toLocalDate()
    .format(DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN))
