@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package ch.mampfi.app

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ch.mampfi.app.data.*
import coil3.compose.AsyncImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
internal fun SharedMealEditScreen(vm: MealViewModel, mealId: String, done: () -> Unit) {
    val meal = vm.meals.collectAsState().value.find { it.id == mealId }
    if (meal == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    var name by remember(meal.id) { mutableStateOf(meal.name) }
    var link by remember(meal.id) { mutableStateOf(meal.rezeptLink.orEmpty()) }
    var note by remember(meal.id) { mutableStateOf(meal.notiz.orEmpty()) }
    var tags by remember(meal.id) { mutableStateOf(meal.tags.mapNotNull { runCatching { Tag.valueOf(it) }.getOrNull() }.toSet().normalizedDietTags()) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var validationRequested by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val initialTags = remember(meal.id) { meal.tags.mapNotNull { runCatching { Tag.valueOf(it) }.getOrNull() }.toSet().normalizedDietTags() }
    val validation = validateMealForm(name)
    val dirty = name != meal.name || link != meal.rezeptLink.orEmpty() || note != meal.notiz.orEmpty() || tags != initialTags
    fun requestBack() { if (dirty) confirmDiscard = true else done() }
    BackHandler(enabled = dirty) { confirmDiscard = true }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mahlzeit bearbeiten", maxLines = 1) },
                navigationIcon = { IconButton(onClick = ::requestBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Zurück") } },
            )
        },
        bottomBar = {
            EditorBottomBar {
                Button(
                    onClick = {
                        validationRequested = true
                        if (!validation.isValid) return@Button
                        saving = true
                        vm.updateMeal(
                            meal.copy(
                                name = name.trim(),
                                rezeptLink = link.trim().ifBlank { null },
                                tags = tags.normalizedDietTags().map { it.name },
                                notiz = note.trim().ifBlank { null },
                            ),
                        ) { success ->
                            saving = false
                            if (success) done()
                        }
                    },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                ) { SaveButtonContent(saving) }
            }
        },
    ) { scaffoldPadding -> LazyColumn(
        modifier = Modifier.fillMaxSize().padding(scaffoldPadding).imePadding(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            FormSection("Mahlzeit") {
                OutlinedTextField(
                    value = name,
                    onValueChange = { value -> name = if ('\n' in value || '\r' in value) normalizePastedMealName(value) else value },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    isError = validationRequested && validation.nameError != null,
                    supportingText = if (validationRequested && validation.nameError != null) {{ Text(validation.nameError!!) }} else null,
                )
                RecipeLinkField(link, true) { link = it }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Notiz (optional)") },
                    placeholder = { Text("z. B. Änderungen am Rezept") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }
        }
        item {
            FormSection("Eigenschaften") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tag.entries.forEach { tag -> FilterChip(tag in tags, { tags = tags.toggleMealTag(tag) }, { Text(tag.label) }) }
                }
            }
        }
    } }
    ConfirmDiscardChangesDialog(confirmDiscard, { confirmDiscard = false }, done)
}


private data class PendingEntryImage(val upload: PendingImageUpload, val preview: Any)

internal fun LocalDate.toDatePickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

internal fun datePickerMillisToLocalDate(value: Long): LocalDate = Instant.ofEpochMilli(value).atZone(ZoneOffset.UTC).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MealDateSelector(selectedDate: LocalDate, blockedDates: Set<LocalDate> = emptySet(), onDateSelected: (LocalDate) -> Unit) {
    var showPicker by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selectedDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
            onValueChange = {},
            label = { Text("Datum") },
            trailingIcon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        Box(
            Modifier
                .matchParentSize()
                .clickable(onClickLabel = "Datum auswählen") { showPicker = true },
        )
    }

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.toDatePickerMillis(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = datePickerMillisToLocalDate(utcTimeMillis) !in blockedDates
            },
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { onDateSelected(datePickerMillisToLocalDate(it)) }
                        showPicker = false
                    },
                    enabled = pickerState.selectedDateMillis != null,
                ) { Text("Übernehmen") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Abbrechen") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
internal fun EditScreen(vm: MealViewModel, date: LocalDate, mealId: String?, entryId: String?, firstRaterName: String, secondRaterName: String, awayEntries: List<AuswaertsEintrag>, done: () -> Unit) {
    val meals by vm.meals.collectAsState(); val selectedMeal = meals.find { it.id == mealId }; val existingEntry = selectedMeal?.eintraege?.find { it.id == entryId }; var chosen by remember { mutableStateOf<Mahlzeit?>(null) }
    var name by remember(selectedMeal) { mutableStateOf(selectedMeal?.name ?: "") }; var link by remember(selectedMeal) { mutableStateOf(selectedMeal?.rezeptLink ?: "") }
    var note by remember(selectedMeal) { mutableStateOf(selectedMeal?.notiz.orEmpty()) }
    var tags by remember(selectedMeal) { mutableStateOf(selectedMeal?.tags?.mapNotNull { runCatching { Tag.valueOf(it) }.getOrNull() }?.toSet()?.normalizedDietTags() ?: emptySet()) }
    val existingRating = existingEntry?.bewertung; var ratingOne by remember(existingEntry) { mutableStateOf(existingRating?.werte?.getOrNull(0)?.toString().orEmpty()) }; var ratingTwo by remember(existingEntry) { mutableStateOf(existingRating?.werte?.getOrNull(1)?.toString().orEmpty()) }
    var selectedDateEpochDay by rememberSaveable(date) { mutableLongStateOf(date.toEpochDay()) }; var expanded by remember { mutableStateOf(false) }; var confirmDelete by remember { mutableStateOf(false) }; var pendingImage by remember { mutableStateOf<PendingEntryImage?>(null) }
    var galleryImageUrl by remember { mutableStateOf<String?>(null) }; var confirmDiscard by remember { mutableStateOf(false) }
    var validationRequested by remember { mutableStateOf(false) }; var saving by remember { mutableStateOf(false) }
    val context = LocalContext.current; val selectedDate = LocalDate.ofEpochDay(selectedDateEpochDay)
    val blockedDates = awayEntries.mapNotNull { runCatching { LocalDate.parse(it.datum) }.getOrNull() }.toSet()
    val dateBlocked = selectedDate in blockedDates
    val validation = validateMealForm(name, ratingOne, ratingTwo)
    val initialTags = remember(selectedMeal) { selectedMeal?.tags?.mapNotNull { runCatching { Tag.valueOf(it) }.getOrNull() }?.toSet()?.normalizedDietTags() ?: emptySet() }
    val dirty = name != selectedMeal?.name.orEmpty() || link != selectedMeal?.rezeptLink.orEmpty() ||
        note != selectedMeal?.notiz.orEmpty() || tags != initialTags || selectedDate != date ||
        ratingOne != existingRating?.werte?.getOrNull(0)?.toString().orEmpty() ||
        ratingTwo != existingRating?.werte?.getOrNull(1)?.toString().orEmpty() || chosen != null || pendingImage != null
    fun requestBack() { if (dirty) confirmDiscard = true else done() }
    BackHandler(enabled = dirty) { confirmDiscard = true }
    fun selectedImage(stream: () -> java.io.InputStream, filename: String, preview: Any) {
        val meal = selectedMeal
        val entry = existingEntry
        if (meal != null && entry != null) vm.addImage(meal.id, entry.id, filename, stream)
        else pendingImage = PendingEntryImage(PendingImageUpload(filename, stream), preview)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { selectedImage({ context.contentResolver.openInputStream(it)!! }, "galerie.jpg", it) } }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? -> bitmap?.let { b -> val bytes = ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray(); selectedImage({ ByteArrayInputStream(bytes) }, "kamera.jpg", b) } }
    fun save() {
        validationRequested = true
        if (!validation.isValid || dateBlocked) return
        val base = selectedMeal ?: chosen ?: Mahlzeit(name = name.trim())
        val meal = base.copy(name = name.trim(), rezeptLink = link.trim().ifBlank { null }, tags = tags.normalizedDietTags().map { it.name }, notiz = note.trim().ifBlank { null })
        val rating = validation.ratings?.let(::MahlzeitBewertung)
        val occurrence = existingEntry?.copy(datum = selectedDate.toString(), bewertung = rating) ?: MahlzeitEintrag(datum = selectedDate.toString(), bewertung = rating)
        saving = true
        val completed: (Boolean) -> Unit = { success -> saving = false; if (success) done() }
        when {
            existingEntry != null -> vm.updateEntry(meal, occurrence, completed)
            selectedMeal != null || chosen != null -> vm.createEntry(meal, occurrence, pendingImage?.upload, completed)
            else -> vm.createMeal(meal, occurrence, pendingImage?.upload, completed)
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(when { existingEntry != null -> "Eintrag bearbeiten"; selectedMeal?.istIdee == true -> "Mahlzeit planen"; else -> "Neues Essen" }, maxLines = 1) },
                navigationIcon = { IconButton(onClick = ::requestBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Zurück") } },
            )
        },
        bottomBar = {
            EditorBottomBar {
                Button(onClick = ::save, enabled = !saving && !dateBlocked, modifier = Modifier.fillMaxWidth()) {
                    SaveButtonContent(saving)
                }
            }
        },
    ) { scaffoldPadding -> LazyColumn(
        modifier = Modifier.fillMaxSize().padding(scaffoldPadding).imePadding(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large) { Column(Modifier.padding(20.dp)) { Text(when { existingEntry != null -> "Eintrag bearbeiten"; selectedMeal?.istIdee == true -> "Mahlzeit planen"; else -> "Neues Essen" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN)), color = MaterialTheme.colorScheme.onPrimaryContainer) } } }
        item { FormSection("Mahlzeit") { OutlinedTextField(name, { value -> name = if ('\n' in value || '\r' in value) normalizePastedMealName(value) else value; expanded = name.isNotBlank() && existingEntry == null }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), isError = validationRequested && validation.nameError != null, supportingText = if (validationRequested && validation.nameError != null) {{ Text(validation.nameError!!) }} else null); if (expanded) meals.filter { it.name.contains(name, true) }.take(5).forEach { meal -> Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { chosen = meal; name = meal.name; link = meal.rezeptLink.orEmpty(); note = meal.notiz.orEmpty(); tags = meal.tags.mapNotNull { runCatching { Tag.valueOf(it) }.getOrNull() }.toSet().normalizedDietTags(); expanded = false }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { meal.letztesBild()?.let { AsyncImage(it, null, Modifier.size(42.dp).clip(MaterialTheme.shapes.small)) }; Column(Modifier.padding(start = 10.dp)) { Text(meal.name, fontWeight = FontWeight.Bold); Text(meal.durchschnitt()?.let { String.format(Locale.GERMANY, "%.1f / 10", it) } ?: "Noch nicht bewertet", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }; RecipeLinkField(link, selectedMeal != null || chosen != null) { link = it }; OutlinedTextField(note, { note = it }, label = { Text("Notiz (optional)") }, placeholder = { Text("z. B. Änderungen am Rezept") }, modifier = Modifier.fillMaxWidth(), minLines = 3, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)) } }
        item { FormSection("Eigenschaften") { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Tag.entries.forEach { tag -> FilterChip(tag in tags, { tags = tags.toggleMealTag(tag) }, { Text(tag.label) }) } } } }
        item { FormSection("Bewertung") {
            Text("Wenn ihr das Essen bewertet, gebt beide Bewertungen ein.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            RaterScoreField(ratingOne, { ratingOne = it }, firstRaterName, MaterialTheme.colorScheme.primary, if (validationRequested) validation.firstRatingError else null)
            RaterScoreField(ratingTwo, { ratingTwo = it }, secondRaterName, MaterialTheme.colorScheme.tertiary, if (validationRequested) validation.secondRatingError else null)
        } }
        item { FormSection("Termin") {
            MealDateSelector(selectedDate, blockedDates) { selectedDateEpochDay = it.toEpochDay() }
            if (dateBlocked) Text("An diesem Tag wird auswärts gegessen. Bitte wähle ein anderes Datum.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        } }
        item { FormSection("Bild") {
            existingEntry?.takeIf { it.bilder.isNotEmpty() }?.let { entry ->
                ExistingEntryImagePager(entry) { galleryImageUrl = it }
            }
            pendingImage?.let { image ->
                AsyncImage(image.preview, "Ausgewähltes Bild", Modifier.fillMaxWidth().height(180.dp).clip(MaterialTheme.shapes.small), contentScale = ContentScale.Crop)
                Text("Das Bild wird beim Speichern hochgeladen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ gallery.launch("image/*") }, Modifier.weight(1f)) { Icon(Icons.Outlined.Image, null); Spacer(Modifier.width(6.dp)); Text("Galerie") }
                OutlinedButton({ camera.launch(null) }, Modifier.weight(1f)) { Icon(Icons.Outlined.PhotoCamera, null); Spacer(Modifier.width(6.dp)); Text("Kamera") }
            }
        } }
        if (existingEntry != null) item { Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) { TextButton({ confirmDelete = true }, Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Outlined.DeleteOutline, null); Spacer(Modifier.width(4.dp)); Text("Eintrag löschen") } } }
    } }
    if (confirmDelete && selectedMeal != null && existingEntry != null) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Eintrag löschen?") }, text = { Text("Nur dieser Termin und seine Bilder werden dauerhaft gelöscht. Beim letzten Eintrag wird auch die Mahlzeit entfernt.") }, confirmButton = { TextButton({ vm.deleteEntry(selectedMeal.id, existingEntry.id); done() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Löschen") } }, dismissButton = { TextButton({ confirmDelete = false }) { Text("Abbrechen") } })
    galleryImageUrl?.let { imageUrl ->
        if (selectedMeal != null && existingEntry != null) {
            MealImageGallery(selectedMeal.copy(eintraege = listOf(existingEntry)), imageUrl) { galleryImageUrl = null }
        }
    }
    ConfirmDiscardChangesDialog(confirmDiscard, { confirmDiscard = false }, done)
}

@Composable
private fun ExistingEntryImagePager(entry: MahlzeitEintrag, openGallery: (String) -> Unit) {
    val pagerState = rememberPagerState(pageCount = { entry.bilder.size })
    Box(Modifier.fillMaxWidth().height(180.dp).clip(MaterialTheme.shapes.small)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val image = entry.bilder[page]
            AsyncImage(
                model = image.url,
                contentDescription = "Bild ${page + 1} ansehen",
                modifier = Modifier.fillMaxSize().clickable { openGallery(image.url) },
                contentScale = ContentScale.Crop,
            )
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
            color = Color.Black.copy(alpha = 0.68f),
            contentColor = Color.White,
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                "${pagerState.currentPage + 1} / ${entry.bilder.size}",
                Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun RaterScoreField(value: String, update: (String) -> Unit, name: String, accent: Color, error: String?) {
    OutlinedTextField(
        value = value,
        onValueChange = update,
        label = { Text("$name (1,00–10,00)") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = error != null,
        supportingText = if (error != null) {{ Text(error) }} else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accent,
            unfocusedBorderColor = accent.copy(alpha = 0.7f),
            focusedLabelColor = accent,
            unfocusedLabelColor = accent,
            cursorColor = accent,
        ),
    )
}
