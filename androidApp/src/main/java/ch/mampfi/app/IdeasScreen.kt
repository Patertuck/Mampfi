@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package ch.mampfi.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.mampfi.app.data.*
import java.util.Locale

@Composable
internal fun IdeasScreen(ideas: List<Mahlzeit>, add: () -> Unit, edit: (Mahlzeit) -> Unit, isRefreshing: Boolean, refresh: () -> Unit) {
    var selectedTagNames by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var query by rememberSaveable { mutableStateOf("") }
    var filterSheetVisible by rememberSaveable { mutableStateOf(false) }
    val selected = selectedTagNames.mapNotNull { name -> Tag.entries.find { it.name == name } }.toSet()
    val filtered = filterMeals(ideas, query, selected).sortedBy { it.name.lowercase(Locale.GERMAN) }
    val gridState = rememberLazyGridState()
    fun updateSelected(tags: Set<Tag>) { selectedTagNames = tags.map { it.name }.sorted() }
    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = refresh, modifier = Modifier.fillMaxSize()) {
      Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Ideen", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Sammelt Gerichte, die ihr später einplanen möchtet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ideen durchsuchen") },
                placeholder = { Text("Nach Namen suchen") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {{ IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Clear, contentDescription = "Suche löschen") } }} else null,
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            FilterChip(
                selected = selected.isNotEmpty(),
                onClick = { filterSheetVisible = true },
                label = { Text(if (selected.isEmpty()) "Filtern" else "Filter (${selected.size})") },
                leadingIcon = { Icon(Icons.Outlined.FilterAlt, contentDescription = null) },
            )
            if (selected.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    selected.forEach { tag ->
                        InputChip(
                            selected = true,
                            onClick = { updateSelected(selected - tag) },
                            label = { Text(tag.label) },
                            trailingIcon = { Icon(Icons.Outlined.Clear, contentDescription = "${tag.label} entfernen", Modifier.size(18.dp)) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            if (filtered.isEmpty()) {
                Surface(Modifier.fillMaxWidth().weight(1f), color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        if (ideas.isEmpty()) MampfiEmptyMascot(Modifier.size(96.dp))
                        else Icon(Icons.Outlined.SearchOff, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(12.dp))
                        Text(if (ideas.isEmpty()) "Noch keine Ideen" else "Keine passenden Ideen", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text(if (ideas.isEmpty()) "Speichert euer nächstes Wunschgericht." else "Passt eure Suche oder Filter an.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (ideas.isNotEmpty()) TextButton(onClick = { query = ""; updateSelected(emptySet()) }) { Text("Suche und Filter zurücksetzen") }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(164.dp),
                    state = gridState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(filtered, key = { it.id }) { idea -> IdeaCard(idea) { edit(idea) } }
                }
            }
        }
        FloatingActionButton(onClick = add, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Outlined.Add, contentDescription = "Idee hinzufügen")
        }
      }
    }
    if (filterSheetVisible) IdeasFilterSheet(
        selected = selected,
        apply = { updateSelected(it); filterSheetVisible = false },
        dismiss = { filterSheetVisible = false },
    )
}

@Composable
private fun IdeasFilterSheet(selected: Set<Tag>, apply: (Set<Tag>) -> Unit, dismiss: () -> Unit) {
    var editing by remember(selected) { mutableStateOf(selected) }
    MampfiFilterDialog(
        title = "Ideen filtern",
        dismiss = dismiss,
        content = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Tag.entries.forEach { tag -> FilterChip(tag in editing, { editing = editing.toggle(tag) }, { Text(tag.label) }) }
            }
        },
        actions = {
            TextButton(onClick = { apply(emptySet()) }, modifier = Modifier.weight(1f)) { Text("Zurücksetzen") }
            Button(onClick = { apply(editing) }, modifier = Modifier.weight(1f)) { Text("Anwenden") }
        },
    )
}

@Composable
private fun IdeaCard(idea: Mahlzeit, click: () -> Unit) = Card(
    modifier = Modifier.fillMaxWidth().animateContentSize().clickable(onClick = click),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
) {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(idea.name, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            DietMarker(idea, Modifier.padding(start = 8.dp))
        }
        val labels = idea.tags
            .filterNot { it == Tag.VEGETARISCH.name && Tag.VEGAN.name in idea.tags }
            .map { value -> Tag.entries.find { it.name == value }?.label ?: value }
        if (labels.isNotEmpty()) Text(labels.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        idea.notiz?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(5.dp))
            Text(if (idea.rezeptLink.isNullOrBlank()) "Kein Rezept-Link" else "Rezept-Link vorhanden", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


@Composable
internal fun IdeaEditScreen(
    vm: MealViewModel,
    mealId: String?,
    initialDraft: SharedMealDraft? = null,
    schedule: (String) -> Unit,
    done: () -> Unit,
) {
    val meals by vm.meals.collectAsState()
    val selectedIdea = meals.find { it.id == mealId && it.istIdee }
    if (mealId != null && selectedIdea == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val isNew = mealId == null
    var name by remember(selectedIdea, initialDraft) { mutableStateOf(selectedIdea?.name ?: initialDraft?.name.orEmpty()) }
    var link by remember(selectedIdea, initialDraft) { mutableStateOf(selectedIdea?.rezeptLink ?: initialDraft?.link.orEmpty()) }
    var note by remember(selectedIdea) { mutableStateOf(selectedIdea?.notiz.orEmpty()) }
    var tags by remember(selectedIdea) {
        mutableStateOf(selectedIdea?.tags?.mapNotNull { runCatching { Tag.valueOf(it) }.getOrNull() }?.toSet()?.normalizedDietTags() ?: emptySet())
    }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var validationRequested by remember { mutableStateOf(false) }
    var savingAction by remember { mutableStateOf<IdeaSaveAction?>(null) }
    val initialTags = remember(selectedIdea) {
        selectedIdea?.tags?.mapNotNull { runCatching { Tag.valueOf(it) }.getOrNull() }?.toSet()?.normalizedDietTags() ?: emptySet()
    }
    val dirty = name != selectedIdea?.name.orEmpty() || link != selectedIdea?.rezeptLink.orEmpty() ||
        note != selectedIdea?.notiz.orEmpty() || tags != initialTags
    val validation = validateMealForm(name)
    fun requestBack() { if (dirty) confirmDiscard = true else done() }
    BackHandler(enabled = dirty) { confirmDiscard = true }
    fun ideaOrNull(): Mahlzeit? {
        if (name.isBlank()) return null
        return (selectedIdea ?: Mahlzeit(name = name.trim())).copy(
            name = name.trim(),
            rezeptLink = link.trim().ifBlank { null },
            notiz = note.trim().ifBlank { null },
            tags = tags.normalizedDietTags().map { it.name },
            istIdee = true,
        )
    }
    fun save(action: IdeaSaveAction) {
        validationRequested = true
        if (!validation.isValid) return
        val idea = ideaOrNull() ?: return
        savingAction = action
        vm.saveIdea(idea, isNew) { success ->
            savingAction = null
            if (success) {
                if (action == IdeaSaveAction.SCHEDULE) schedule(idea.id) else done()
            }
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Neue Idee" else "Idee bearbeiten", maxLines = 1) },
                navigationIcon = { IconButton(onClick = ::requestBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Zurück") } },
            )
        },
        bottomBar = {
            EditorBottomBar {
                OutlinedButton(
                    onClick = { save(IdeaSaveAction.SCHEDULE) },
                    enabled = savingAction == null,
                    modifier = Modifier.weight(1f),
                ) {
                    if (savingAction == IdeaSaveAction.SCHEDULE) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (savingAction == IdeaSaveAction.SCHEDULE) "Speichert …" else "Einplanen")
                }
                Button(
                    onClick = { save(IdeaSaveAction.SAVE) },
                    enabled = savingAction == null,
                    modifier = Modifier.weight(1f),
                ) { SaveButtonContent(savingAction == IdeaSaveAction.SAVE) }
            }
        },
    ) { scaffoldPadding -> LazyColumn(
        modifier = Modifier.fillMaxSize().padding(scaffoldPadding).imePadding(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(20.dp)) {
                    Text(if (isNew) "Neue Idee" else "Idee bearbeiten", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Ohne Termin speichern und später einplanen.", color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
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
                RecipeLinkField(link, selectedIdea != null) { link = it }
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
        if (!isNew) item {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                TextButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Aus Ideen entfernen")
                }
            }
        }
    } }
    if (confirmDelete && selectedIdea != null) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Aus Ideen entfernen?") },
        text = { Text(if (selectedIdea.eintraege.isEmpty()) "Diese noch nie gekochte Idee wird dauerhaft gelöscht." else "Die Mahlzeit bleibt mit ihrer Kochhistorie in der Übersicht erhalten.") },
        confirmButton = { TextButton({
            if (selectedIdea.eintraege.isEmpty()) vm.deleteIdea(selectedIdea.id) { done() }
            else vm.setIdea(selectedIdea, false) { done() }
        }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Entfernen") } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Abbrechen") } },
    )
    ConfirmDiscardChangesDialog(confirmDiscard, { confirmDiscard = false }, done)
}

private enum class IdeaSaveAction { SAVE, SCHEDULE }
