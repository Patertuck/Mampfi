@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package ch.mampfi.app

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.*
import ch.mampfi.app.data.*
import coil3.compose.AsyncImage
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable fun MampfiApp(vm: MealViewModel) {
    val nav = rememberNavController(); val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.message.collect { snackbar.showSnackbar(it) } }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, bottomBar = { NavigationBar {
        listOf("kalender" to "Kalender", "uebersicht" to "Übersicht").forEach { (route, label) ->
            NavigationBarItem(selected = nav.currentBackStackEntryAsState().value?.destination?.route == route, onClick = { nav.navigate(route) { launchSingleTop = true } }, icon = { Text(if (route == "kalender") "□" else "☷") }, label = { Text(label) })
        }
    } }) { padding -> NavHost(nav, "kalender", Modifier.padding(padding)) {
        composable("kalender") { CalendarScreen(vm.meals.collectAsState().value, { nav.navigate("bearbeiten/$it") }) }
        composable("uebersicht") { OverviewScreen(vm.meals.collectAsState().value, { meal -> nav.navigate("bearbeiten/${meal.letzterTermin() ?: LocalDate.now()}?meal=${meal.id}") }) }
        composable("bearbeiten/{date}?meal={meal}") { entry -> EditScreen(vm, LocalDate.parse(entry.arguments!!.getString("date")!!), entry.arguments?.getString("meal"), { nav.popBackStack() }) }
    } }
}

@Composable private fun CalendarScreen(meals: List<Mahlzeit>, open: (LocalDate) -> Unit) {
    val month = remember { YearMonth.now() }; val state = rememberCalendarState(startMonth = month.minusMonths(24), endMonth = month.plusMonths(24), firstVisibleMonth = month, firstDayOfWeek = java.time.DayOfWeek.MONDAY)
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Mahlzeitenkalender", style = MaterialTheme.typography.headlineSmall)
        HorizontalCalendar(state = state, dayContent = { day ->
            CalendarCell(day, meals.filter { day.date.toString() in it.termine }, open)
        })
    }
}
@Composable private fun CalendarCell(day: CalendarDay, meals: List<Mahlzeit>, open: (LocalDate) -> Unit) {
    Column(Modifier.heightIn(min = 74.dp).padding(3.dp).clip(MaterialTheme.shapes.small).clickable { open(day.date) }.background(if (day.position == DayPosition.MonthDate) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant).padding(4.dp)) {
        Text(day.date.dayOfMonth.toString(), style = MaterialTheme.typography.labelMedium)
        meals.take(2).forEach { Text(it.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
        if (meals.size > 2) Text("+${meals.size - 2}", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable private fun OverviewScreen(meals: List<Mahlzeit>, edit: (Mahlzeit) -> Unit) {
    var selected by remember { mutableStateOf(setOf<Tag>()) }; var criterion by remember { mutableStateOf("Zuletzt gekocht") }; var ascending by remember { mutableStateOf(false) }; var menu by remember { mutableStateOf(false) }
    val filtered = meals.filter { m -> selected.all(m::hatTag) }.sortedWith(compareBy<Mahlzeit> { when (criterion) { "Bewertung" -> it.durchschnitt() ?: -1.0; "Am häufigsten gekocht" -> it.termine.size; else -> it.letzterTermin() ?: "" } }.let { if (ascending) it else it.reversed() })
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Übersicht", style = MaterialTheme.typography.headlineSmall); FlowRow { Tag.entries.forEach { tag -> FilterChip(selected = tag in selected, onClick = { selected = selected.toggle(tag) }, label = { Text(tag.label) }) } }
        Row(verticalAlignment = Alignment.CenterVertically) { Box { TextButton({ menu = true }) { Text("$criterion ${if (ascending) "↑" else "↓"}") }; DropdownMenu(menu, { menu = false }) { listOf("Zuletzt gekocht", "Bewertung", "Am häufigsten gekocht").forEach { DropdownMenuItem({ Text(it) }, { criterion = it; menu = false }) } } }; TextButton({ ascending = !ascending }) { Text(if (ascending) "Aufsteigend" else "Absteigend") } }
        if (filtered.isEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Keine Mahlzeiten gefunden.") } else LazyVerticalGrid(GridCells.Adaptive(150.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(filtered, key = { it.id }) { MealCard(it) { edit(it) } } }
    }
}
private fun Set<Tag>.toggle(item: Tag) = if (item in this) this - item else this + item
@Composable private fun MealCard(meal: Mahlzeit, click: () -> Unit) = Card(Modifier.fillMaxWidth().clickable(onClick = click)) { Column(Modifier.padding(8.dp)) { meal.letztesBild()?.let { AsyncImage(it, null, Modifier.fillMaxWidth().height(96.dp)) }; Text(meal.name, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(meal.durchschnitt()?.let { String.format(Locale.GERMANY, "%.2f/10", it) } ?: "Noch nicht bewertet") } }

@Composable private fun EditScreen(vm: MealViewModel, date: LocalDate, id: String?, done: () -> Unit) {
    val meals by vm.meals.collectAsState(); val existing = meals.find { it.id == id }; var chosen by remember { mutableStateOf<Mahlzeit?>(null) }; var name by remember(existing) { mutableStateOf(existing?.name ?: "") }; var link by remember(existing) { mutableStateOf(existing?.rezeptLink ?: "") }; var tags by remember(existing) { mutableStateOf(existing?.tags?.mapNotNull { runCatching { Tag.valueOf(it) }.getOrNull() }?.toSet() ?: emptySet()) }; val existingRating = existing?.bewertungen?.lastOrNull { it.datum == date.toString() }; var ratingOne by remember(existing, date) { mutableStateOf(existingRating?.werte?.getOrNull(0)?.toString().orEmpty()) }; var ratingTwo by remember(existing, date) { mutableStateOf(existingRating?.werte?.getOrNull(1)?.toString().orEmpty()) }; var dateText by remember { mutableStateOf(date.toString()) }; var expanded by remember { mutableStateOf(false) }; var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val selectedDate = runCatching { LocalDate.parse(dateText) }.getOrNull()
    fun uploaded(stream: () -> java.io.InputStream, filename: String) { val meal = existing ?: return; val validDate = selectedDate ?: return; vm.addImage(meal, validDate, filename, stream) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { uploaded({ context.contentResolver.openInputStream(it)!! }, "galerie.jpg") } }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? -> bitmap?.let { b -> val bytes = ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray(); uploaded({ ByteArrayInputStream(bytes) }, "kamera.jpg") } }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text(if (existing == null) "Mahlzeit hinzufügen" else "Mahlzeit bearbeiten", style = MaterialTheme.typography.headlineSmall) }
        item { OutlinedTextField(name, { name = it; expanded = it.isNotBlank() && existing == null }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth()) }
        if (expanded) item {
            Card { Column {
                meals.filter { it.name.contains(name, true) }.take(5).forEach { meal ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            chosen = meal; name = meal.name; link = meal.rezeptLink.orEmpty()
                            tags = meal.tags.mapNotNull { runCatching { Tag.valueOf(it) }.getOrNull() }.toSet()
                            expanded = false
                        }.padding(8.dp), verticalAlignment = Alignment.CenterVertically
                    ) {
                        meal.letztesBild()?.let { AsyncImage(it, null, Modifier.size(40.dp)) }
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(meal.name)
                            Text(meal.durchschnitt()?.let { String.format(Locale.GERMANY, "%.2f", it) } ?: "Noch nicht bewertet")
                        }
                    }
                }
            } }
        }
        item { OutlinedTextField(link, { link = it }, label = { Text("Rezept-Link") }, modifier = Modifier.fillMaxWidth()) }
        item { Text("Tags"); FlowRow { Tag.entries.forEach { tag -> FilterChip(tag in tags, { tags = tags.toggle(tag) }, { Text(tag.label) }) } } }
        item { OutlinedTextField(ratingOne, { ratingOne = it }, label = { Text("Bewertung Person 1 (1,00–10,00)") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(ratingTwo, { ratingTwo = it }, label = { Text("Bewertung Person 2 (1,00–10,00)") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(dateText, { dateText = it }, label = { Text("Termin (JJJJ-MM-TT)") }, isError = selectedDate == null, modifier = Modifier.fillMaxWidth()) }
        item { Row { Button({
            val base = existing ?: chosen ?: Mahlzeit(name = name.trim())
            val ratingTexts = listOf(ratingOne, ratingTwo)
            val ratingsProvided = ratingTexts.any { it.isNotBlank() }
            val validRatings = ratingTexts.map { it.replace(',', '.').toDoubleOrNull()?.takeIf { value -> value in 1.0..10.0 } }
            val validDate = selectedDate ?: return@Button
            if (name.isBlank() || (ratingsProvided && validRatings.any { it == null })) return@Button
            val movedDates = if (existing != null) base.termine.filterNot { it == date.toString() } else base.termine
            val updatedRatings = if (!ratingsProvided) base.bewertungen else base.bewertungen.filterNot { it.datum == validDate.toString() } + MahlzeitBewertung(validRatings.filterNotNull(), validDate.toString())
            vm.save(base.copy(name = name.trim(), rezeptLink = link.trim().ifBlank { null }, tags = tags.map { it.name }, termine = (movedDates + validDate.toString()).distinct(), bewertungen = updatedRatings)); done()
        }) { Text("Speichern") }; Spacer(Modifier.width(8.dp)); if (existing != null) OutlinedButton({ gallery.launch("image/*") }) { Text("Bild wählen") }; if (existing != null) OutlinedButton({ camera.launch(null) }) { Text("Kamera") } } }
        if (existing != null) item { Row { TextButton({ vm.removeDate(existing.id, date); done() }) { Text("Termin entfernen") }; TextButton({ confirmDelete = true }) { Text("Mahlzeit löschen") } } }
    }
    if (confirmDelete && existing != null) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Mahlzeit löschen?") },
        text = { Text("Die Mahlzeit und ihre Bilder werden dauerhaft gelöscht.") },
        confirmButton = { TextButton(onClick = { vm.deleteMeal(existing.id); done() }) { Text("Löschen") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
    )
}
