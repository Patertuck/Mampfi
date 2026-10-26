@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package ch.mampfi.app

import android.app.Activity
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

data class GroceryImageUpload(val filename: String, val openStream: () -> java.io.InputStream)

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
    val activity = LocalContext.current as Activity
    val lifecycleOwner = LocalLifecycleOwner.current
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

    DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> vm.startSync()
                Lifecycle.Event.ON_PAUSE -> vm.stopSync()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) vm.startSync()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            vm.stopSync()
        }
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
            product = suggestion.product,
            initialCategory = suggestion.category,
            initialUnit = GroceryUnit.PIECE,
            initialQuantity = "1",
            allowedUnits = GroceryUnit.entries,
            confirmLabel = "Hinzufügen",
            dismiss = { draft = null },
        ) { quantity, unit, category, _ ->
            vm.add(suggestion.copy(category = category), quantity, unit)
            query = ""; vm.clearSearch(); draft = null
        }
    }
    editing?.let { item ->
        GroceryAmountDialog(
            product = item.product,
            initialCategory = item.category,
            initialUnit = item.preferredUnit,
            initialQuantity = quantityInput(item),
            allowedUnits = GroceryUnit.entries.filter { it.dimension() == item.dimension },
            confirmLabel = "Speichern",
            dismiss = { editing = null },
        ) { quantity, unit, category, image ->
            vm.update(item.copy(baseQuantity = quantity, preferredUnit = unit, category = category), image)
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
    product: GroceryProductRef,
    initialCategory: GroceryCategory,
    initialUnit: GroceryUnit,
    initialQuantity: String,
    allowedUnits: List<GroceryUnit>,
    confirmLabel: String,
    dismiss: () -> Unit,
    confirm: (Double, GroceryUnit, GroceryCategory, GroceryImageUpload?) -> Unit,
) {
    var amount by remember(product.stableKey, initialQuantity) { mutableStateOf(initialQuantity) }
    var unit by remember(product.stableKey, initialUnit) { mutableStateOf(initialUnit) }
    var category by remember(product.stableKey, initialCategory) { mutableStateOf(initialCategory) }
    var unitMenu by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }
    var selectedImage by remember { mutableStateOf<GroceryImageUpload?>(null) }
    var selectedPreview by remember { mutableStateOf<Any?>(null) }
    val context = LocalContext.current
    fun chooseImage(stream: () -> java.io.InputStream, filename: String, preview: Any) {
        selectedImage = GroceryImageUpload(filename, stream)
        selectedPreview = preview
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { chooseImage({ context.contentResolver.openInputStream(it)!! }, "galerie.jpg", it) }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        bitmap?.let { captured ->
            val bytes = ByteArrayOutputStream().also { captured.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
            chooseImage({ ByteArrayInputStream(bytes) }, "kamera.jpg", captured)
        }
    }
    val base = quantityToBase(amount, unit)
    MampfiDialog(
        title = if (confirmLabel == "Speichern") "Artikel bearbeiten" else "Artikel hinzufügen",
        subtitle = product.name,
        icon = if (confirmLabel == "Speichern") Icons.Outlined.Edit else Icons.Outlined.AddShoppingCart,
        dismiss = dismiss,
        content = {
            Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (selectedPreview != null) AsyncImage(selectedPreview, "Ausgewähltes Bild", Modifier.size(52.dp))
                    else if (product.imageUrl != null) AsyncImage(product.imageUrl, null, Modifier.size(52.dp))
                    else Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                        Icon(Icons.Outlined.ShoppingBasket, null, Modifier.padding(12.dp).size(28.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(product.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        listOfNotNull(product.brand, product.packageSize).takeIf { it.isNotEmpty() }?.let {
                            Text(it.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (confirmLabel == "Speichern") {
                Text("Bild", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ gallery.launch("image/*") }, Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Image, null); Spacer(Modifier.width(6.dp)); Text(if (product.imageUrl == null) "Galerie" else "Ersetzen")
                    }
                    OutlinedButton({ camera.launch(null) }, Modifier.weight(1f)) {
                        Icon(Icons.Outlined.PhotoCamera, null); Spacer(Modifier.width(6.dp)); Text("Kamera")
                    }
                }
            }
            Text("Menge", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    amount,
                    { amount = it },
                    label = { Text("Anzahl oder Menge") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    isError = amount.isNotBlank() && base == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = if (amount.isNotBlank() && base == null) {{ Text("Bitte eine positive Zahl eingeben") }} else null,
                )
                ExposedDropdownMenuBox(unitMenu, { unitMenu = it }, Modifier.width(122.dp)) {
                    OutlinedTextField(
                        unit.label, {}, readOnly = true, label = { Text("Einheit") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(unitMenu) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(unitMenu, { unitMenu = false }) {
                        allowedUnits.forEach { candidate -> DropdownMenuItem({ Text(candidate.label) }, { unit = candidate; unitMenu = false }) }
                    }
                }
            }
            Text("Sortierung", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            ExposedDropdownMenuBox(categoryMenu, { categoryMenu = it }) {
                OutlinedTextField(
                    category.label, {}, readOnly = true, label = { Text("Kategorie") },
                    leadingIcon = { Icon(Icons.Outlined.Category, null) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryMenu) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                )
                ExposedDropdownMenu(categoryMenu, { categoryMenu = false }) {
                    GroceryCategory.entries.forEach { candidate ->
                        DropdownMenuItem(
                            text = { Text(candidate.label) },
                            onClick = { category = candidate; categoryMenu = false },
                            trailingIcon = if (candidate == category) {{ Icon(Icons.Outlined.Check, null) }} else null,
                        )
                    }
                }
            }
        },
        actions = {
            TextButton(dismiss, Modifier.weight(1f)) { Text("Abbrechen") }
            Button({ base?.let { confirm(it, unit, category, selectedImage) } }, enabled = base != null, modifier = Modifier.weight(1f)) {
                Icon(if (confirmLabel == "Speichern") Icons.Outlined.Save else Icons.Outlined.Add, null)
                Spacer(Modifier.width(6.dp))
                Text(confirmLabel)
            }
        },
    )
}

@Composable private fun EmptyGroceries(text: String) {
    Text(text, modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun manualKey(name: String) = "manual:" + name.trim().lowercase().replace(Regex("[^a-z0-9äöüéèà]+"), "-").trim('-').ifBlank { UUID.randomUUID().toString() }
private fun quantityInput(item: GroceryItem) = when (item.preferredUnit) {
    GroceryUnit.KG, GroceryUnit.L -> (item.baseQuantity / 1000.0).toString().replace('.', ',')
    GroceryUnit.DL -> (item.baseQuantity / 100.0).toString().replace('.', ',')
    else -> item.baseQuantity.toString()
}
private fun historyDate(value: String): String = Instant.parse(value).atZone(ZoneId.of("Europe/Zurich")).toLocalDate()
    .format(DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN))
