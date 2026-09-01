@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package ch.mampfi.app

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.*
import ch.mampfi.app.data.*
import coil3.compose.AsyncImage
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.WeekCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.compose.weekcalendar.rememberWeekCalendarState
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun MampfiApp(vm: MealViewModel) = MampfiTheme {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val currentRoute = nav.currentBackStackEntryAsState().value?.destination?.route
    LaunchedEffect(Unit) { vm.message.collect { snackbar.showSnackbar(it) } }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
                listOf(
                    Triple("kalender", "Kalender", Icons.Outlined.CalendarMonth),
                    Triple("uebersicht", "Übersicht", Icons.Outlined.ViewList),
                ).forEach { (route, label, icon) ->
                    NavigationBarItem(
                        selected = currentRoute == route,
                        onClick = { nav.navigate(route) { launchSingleTop = true; popUpTo("kalender") { saveState = true }; restoreState = true } },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, "kalender", Modifier.padding(padding)) {
            composable("kalender") { CalendarScreen(vm.meals.collectAsState().value) { nav.navigate("bearbeiten/$it") } }
            composable("uebersicht") { OverviewScreen(vm.meals.collectAsState().value) { meal -> nav.navigate("bearbeiten/${meal.letzterTermin() ?: LocalDate.now()}?meal=${meal.id}") } }
            composable("bearbeiten/{date}?meal={meal}") { entry -> EditScreen(vm, LocalDate.parse(entry.arguments!!.getString("date")!!), entry.arguments?.getString("meal")) { nav.popBackStack() } }
        }
    }
}

@Composable
private fun CalendarScreen(meals: List<Mahlzeit>, open: (LocalDate) -> Unit) {
    val currentMonth = remember { YearMonth.now() }
    val monthState = rememberCalendarState(currentMonth.minusMonths(24), currentMonth.plusMonths(24), currentMonth, java.time.DayOfWeek.MONDAY)
    val weekState = rememberWeekCalendarState(
        startDate = currentMonth.minusMonths(24).atDay(1),
        endDate = currentMonth.plusMonths(24).atEndOfMonth(),
        firstVisibleWeekDate = LocalDate.now(),
        firstDayOfWeek = java.time.DayOfWeek.MONDAY,
    )
    var showMonth by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !showMonth,
                onClick = {
                    if (showMonth) {
                        val visibleDate = monthState.firstVisibleMonth.yearMonth.atDay(1)
                        showMonth = false
                        scope.launch { weekState.scrollToWeek(visibleDate) }
                    }
                },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                label = { Text("Woche") },
            )
            SegmentedButton(
                selected = showMonth,
                onClick = {
                    if (!showMonth) {
                        val visibleDate = weekState.firstVisibleWeek.days.first().date
                        showMonth = true
                        scope.launch { monthState.scrollToMonth(YearMonth.from(visibleDate)) }
                    }
                },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                label = { Text("Monat") },
            )
        }
        Spacer(Modifier.height(16.dp))
        if (showMonth) {
            HorizontalCalendar(state = monthState, monthHeader = { month -> CalendarMonthHeader(month.yearMonth) }, dayContent = { day -> CalendarCell(day, meals.filter { day.date.toString() in it.termine }, open) })
        } else {
            WeekCalendar(
                state = weekState,
                weekHeader = { week -> CalendarWeekHeader(week.days.first().date, week.days.last().date) },
                dayContent = { day -> WeekCalendarCell(day.date, meals.filter { day.date.toString() in it.termine }, open) },
            )
        }
    }
}

@Composable
private fun CalendarMonthHeader(month: YearMonth) {
    Column(Modifier.padding(bottom = 8.dp)) {
        Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN)), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) { listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").forEach { day -> Text(day, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary) } }
    }
}

@Composable
private fun CalendarWeekHeader(start: LocalDate, end: LocalDate) {
    Column(Modifier.padding(bottom = 8.dp)) {
        Text("${start.format(DateTimeFormatter.ofPattern("d. MMMM", Locale.GERMAN))} – ${end.format(DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMAN))}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) { listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").forEach { day -> Text(day, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary) } }
    }
}

@Composable
private fun CalendarCell(day: CalendarDay, meals: List<Mahlzeit>, open: (LocalDate) -> Unit) {
    CalendarDayCell(day.date, day.position == DayPosition.MonthDate, meals, open)
}

@Composable
private fun WeekCalendarCell(date: LocalDate, meals: List<Mahlzeit>, open: (LocalDate) -> Unit) {
    CalendarDayCell(date, true, meals, open)
}

@Composable
private fun CalendarDayCell(date: LocalDate, inCurrentRange: Boolean, meals: List<Mahlzeit>, open: (LocalDate) -> Unit) {
    val today = date == LocalDate.now()
    val container = when { today -> MaterialTheme.colorScheme.primaryContainer; inCurrentRange -> MaterialTheme.colorScheme.surface; else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f) }
    Surface(Modifier.fillMaxWidth().height(88.dp).padding(3.dp).clip(MaterialTheme.shapes.medium).clickable { open(date) }, shape = MaterialTheme.shapes.medium, color = container, border = if (today) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 5.dp)) {
            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.labelMedium, fontWeight = if (today) FontWeight.Bold else FontWeight.Medium, color = if (inCurrentRange) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(3.dp))
            meals.take(2).forEach { meal ->
                Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.17f), shape = MaterialTheme.shapes.extraSmall) { Text(meal.name, Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 2.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.height(2.dp))
            }
            if (meals.size > 2) Text("+${meals.size - 2} weitere", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun OverviewScreen(meals: List<Mahlzeit>, edit: (Mahlzeit) -> Unit) {
    var selected by remember { mutableStateOf(setOf<Tag>()) }
    var criterion by remember { mutableStateOf("Zuletzt gekocht") }
    var ascending by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val filtered = meals.filter { selected.all(it::hatTag) }.sortedWith(compareBy<Mahlzeit> { when (criterion) { "Bewertung" -> it.durchschnitt() ?: -1.0; "Am häufigsten gekocht" -> it.termine.size; else -> it.letzterTermin() ?: "" } }.let { if (ascending) it else it.reversed() })
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Deine Mahlzeiten", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Finde schnell, worauf ihr Lust habt.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Tag.entries.forEach { tag -> FilterChip(tag in selected, { selected = selected.toggle(tag) }, { Text(tag.label) }) } }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box { AssistChip({ menu = true }, { Text(criterion) }); DropdownMenu(menu, { menu = false }) { listOf("Zuletzt gekocht", "Bewertung", "Am häufigsten gekocht").forEach { option -> DropdownMenuItem({ Text(option) }, { criterion = option; menu = false }) } } }
            AssistChip({ ascending = !ascending }, { Text(if (ascending) "Aufsteigend" else "Absteigend") })
        }
        Spacer(Modifier.height(14.dp))
        if (filtered.isEmpty()) Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp)); Text("Noch keine Mahlzeiten", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Plane im Kalender euer erstes Essen.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else LazyVerticalGrid(GridCells.Adaptive(164.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(filtered, key = { it.id }) { MealCard(it) { edit(it) } } }
    }
}

private fun Set<Tag>.toggle(item: Tag) = if (item in this) this - item else this + item

@Composable
private fun MealCard(meal: Mahlzeit, click: () -> Unit) = Card(Modifier.fillMaxWidth().clickable(onClick = click), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
    Column {
        meal.letztesBild()?.let { AsyncImage(it, null, Modifier.fillMaxWidth().height(104.dp)) }
        Column(Modifier.padding(12.dp)) {
            Text(meal.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) { Text(meal.durchschnitt()?.let { String.format(Locale.GERMANY, "%.1f / 10", it) } ?: "Noch nicht bewertet", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer) }
        }
    }
}

@Composable
private fun EditScreen(vm: MealViewModel, date: LocalDate, id: String?, done: () -> Unit) {
    val meals by vm.meals.collectAsState(); val existing = meals.find { it.id == id }; var chosen by remember { mutableStateOf<Mahlzeit?>(null) }
    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }; var link by remember(existing) { mutableStateOf(existing?.rezeptLink ?: "") }
    var tags by remember(existing) { mutableStateOf(existing?.tags?.mapNotNull { runCatching { Tag.valueOf(it) }.getOrNull() }?.toSet() ?: emptySet()) }
    val existingRating = existing?.bewertungen?.lastOrNull { it.datum == date.toString() }; var ratingOne by remember(existing, date) { mutableStateOf(existingRating?.werte?.getOrNull(0)?.toString().orEmpty()) }; var ratingTwo by remember(existing, date) { mutableStateOf(existingRating?.werte?.getOrNull(1)?.toString().orEmpty()) }
    var dateText by remember { mutableStateOf(date.toString()) }; var expanded by remember { mutableStateOf(false) }; var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current; val selectedDate = runCatching { LocalDate.parse(dateText) }.getOrNull()
    fun uploaded(stream: () -> java.io.InputStream, filename: String) { val meal = existing ?: return; val validDate = selectedDate ?: return; vm.addImage(meal, validDate, filename, stream) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { uploaded({ context.contentResolver.openInputStream(it)!! }, "galerie.jpg") } }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? -> bitmap?.let { b -> val bytes = ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray(); uploaded({ ByteArrayInputStream(bytes) }, "kamera.jpg") } }
    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large) { Column(Modifier.padding(20.dp)) { Text(if (existing == null) "Neues Essen" else "Mahlzeit bearbeiten", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(date.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN)), color = MaterialTheme.colorScheme.onPrimaryContainer) } } }
        item { FormSection("Mahlzeit") { OutlinedTextField(name, { name = it; expanded = it.isNotBlank() && existing == null }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true); if (expanded) meals.filter { it.name.contains(name, true) }.take(5).forEach { meal -> Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { chosen = meal; name = meal.name; link = meal.rezeptLink.orEmpty(); tags = meal.tags.mapNotNull { runCatching { Tag.valueOf(it) }.getOrNull() }.toSet(); expanded = false }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { meal.letztesBild()?.let { AsyncImage(it, null, Modifier.size(42.dp).clip(MaterialTheme.shapes.small)) }; Column(Modifier.padding(start = 10.dp)) { Text(meal.name, fontWeight = FontWeight.Bold); Text(meal.durchschnitt()?.let { String.format(Locale.GERMANY, "%.1f / 10", it) } ?: "Noch nicht bewertet", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }; OutlinedTextField(link, { link = it }, label = { Text("Rezept-Link") }, modifier = Modifier.fillMaxWidth(), singleLine = true) } }
        item { FormSection("Eigenschaften") { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Tag.entries.forEach { tag -> FilterChip(tag in tags, { tags = tags.toggle(tag) }, { Text(tag.label) }) } } } }
        item { FormSection("Bewertung") { Text("Wenn ihr das Essen bewertet, gebt beide Bewertungen ein.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); OutlinedTextField(ratingOne, { ratingOne = it }, label = { Text("Person 1 (1,00–10,00)") }, modifier = Modifier.fillMaxWidth(), singleLine = true); OutlinedTextField(ratingTwo, { ratingTwo = it }, label = { Text("Person 2 (1,00–10,00)") }, modifier = Modifier.fillMaxWidth(), singleLine = true) } }
        item { FormSection("Termin") { OutlinedTextField(dateText, { dateText = it }, label = { Text("Datum (JJJJ-MM-TT)") }, isError = selectedDate == null, modifier = Modifier.fillMaxWidth(), singleLine = true) } }
        item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { val base = existing ?: chosen ?: Mahlzeit(name = name.trim()); val ratingTexts = listOf(ratingOne, ratingTwo); val ratingsProvided = ratingTexts.any { it.isNotBlank() }; val validRatings = ratingTexts.map { it.replace(',', '.').toDoubleOrNull()?.takeIf { value -> value in 1.0..10.0 } }; val validDate = selectedDate ?: return@Button; if (name.isBlank() || (ratingsProvided && validRatings.any { it == null })) return@Button; val movedDates = if (existing != null) base.termine.filterNot { it == date.toString() } else base.termine; val updatedRatings = if (!ratingsProvided) base.bewertungen else base.bewertungen.filterNot { it.datum == validDate.toString() } + MahlzeitBewertung(validRatings.filterNotNull(), validDate.toString()); vm.save(base.copy(name = name.trim(), rezeptLink = link.trim().ifBlank { null }, tags = tags.map { it.name }, termine = (movedDates + validDate.toString()).distinct(), bewertungen = updatedRatings)); done() }, modifier = Modifier.fillMaxWidth()) { Text("Speichern") }
            if (existing != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton({ gallery.launch("image/*") }, Modifier.weight(1f)) { Icon(Icons.Outlined.Image, null); Spacer(Modifier.width(6.dp)); Text("Galerie") }; OutlinedButton({ camera.launch(null) }, Modifier.weight(1f)) { Icon(Icons.Outlined.PhotoCamera, null); Spacer(Modifier.width(6.dp)); Text("Kamera") } }
        } }
        if (existing != null) item { Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) { Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { TextButton({ vm.removeDate(existing.id, date); done() }) { Text("Termin entfernen") }; TextButton({ confirmDelete = true }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Outlined.DeleteOutline, null); Spacer(Modifier.width(4.dp)); Text("Löschen") } } } }
    }
    if (confirmDelete && existing != null) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Mahlzeit löschen?") }, text = { Text("Die Mahlzeit und ihre Bilder werden dauerhaft gelöscht.") }, confirmButton = { TextButton({ vm.deleteMeal(existing.id); done() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Löschen") } }, dismissButton = { TextButton({ confirmDelete = false }) { Text("Abbrechen") } })
}

@Composable
private fun FormSection(title: String, content: @Composable ColumnScope.() -> Unit) = Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary); content() } }
