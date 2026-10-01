@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package ch.mampfi.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.mampfi.app.data.*
import coil3.compose.AsyncImage
import com.kizitonwose.calendar.compose.WeekCalendar
import com.kizitonwose.calendar.compose.weekcalendar.rememberWeekCalendarState
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
internal fun CalendarScreen(
    meals: List<Mahlzeit>, awayEntries: List<AuswaertsEintrag>, open: (LocalDate) -> Unit,
    recommend: (LocalDate) -> Unit,
    edit: (Mahlzeit, LocalDate) -> Unit,
    createAway: (AuswaertsEintrag, Boolean, () -> Unit) -> Unit,
    updateAway: (AuswaertsEintrag, Boolean, () -> Unit) -> Unit,
    deleteAway: (String, () -> Unit) -> Unit,
) {
    val today = LocalDate.now()
    val currentMonth = YearMonth.from(today)
    val calendarStartDate = remember(currentMonth) { currentMonth.minusMonths(24).atDay(1) }
    val calendarEndDate = remember(currentMonth) { currentMonth.plusMonths(24).atEndOfMonth() }
    val weekState = rememberWeekCalendarState(
        startDate = calendarStartDate,
        endDate = calendarEndDate,
        firstVisibleWeekDate = today,
        firstDayOfWeek = java.time.DayOfWeek.MONDAY,
    )
    var calendarView by rememberSaveable { mutableStateOf("WEEK") }
    var selectedWeekDate by rememberSaveable { mutableStateOf(today.toString()) }
    var receivedInitialWeek by remember { mutableStateOf(false) }
    var planInitialised by rememberSaveable { mutableStateOf(false) }
    var galleryMeal by remember { mutableStateOf<Mahlzeit?>(null) }
    var galleryImageUrl by remember { mutableStateOf<String?>(null) }
    var actionDate by remember { mutableStateOf<LocalDate?>(null) }
    var editingAway by remember { mutableStateOf<AuswaertsEintrag?>(null) }
    var creatingAway by remember { mutableStateOf(false) }
    val planListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val swipeThreshold = with(LocalDensity.current) { 64.dp.toPx() }
    LaunchedEffect(weekState) {
        snapshotFlow { weekState.firstVisibleWeek.days.first().date }
            .distinctUntilChanged()
            .collect { monday ->
                if (receivedInitialWeek) selectedWeekDate = monday.toString() else receivedInitialWeek = true
            }
    }
    val selectedDate = LocalDate.parse(selectedWeekDate)
    val planItems = remember(meals, awayEntries, today) { scheduledPlanItems(meals, awayEntries, today) }
    val todayMarkerIndex = planItems.indexOfFirst { it is PlanItem.TodayMarker }.coerceAtLeast(0)
    LaunchedEffect(calendarView, planItems) {
        if (calendarView == "PLAN" && !planInitialised) {
            planListState.scrollToItem(todayMarkerIndex)
            planInitialised = true
        }
    }
    val weekSwipeModifier = if (calendarView == "WEEK") Modifier.pointerInput(weekState, swipeThreshold) {
        var dragDistance = 0f
        detectHorizontalDragGestures(
            onDragStart = { dragDistance = 0f },
            onHorizontalDrag = { change, amount ->
                change.consume()
                dragDistance += amount
            },
            onDragCancel = { dragDistance = 0f },
            onDragEnd = {
                val weekOffset = when {
                    dragDistance <= -swipeThreshold -> 1L
                    dragDistance >= swipeThreshold -> -1L
                    else -> 0L
                }
                if (weekOffset != 0L) {
                    val target = weekState.firstVisibleWeek.days.first().date.plusWeeks(weekOffset)
                    if (target in calendarStartDate..calendarEndDate) scope.launch { weekState.animateScrollToWeek(target) }
                }
                dragDistance = 0f
            },
        )
    } else Modifier
    Column(Modifier.fillMaxSize().then(weekSwipeModifier).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                SegmentedButton(
                    selected = calendarView == "WEEK",
                    onClick = {
                        if (calendarView != "WEEK") {
                            calendarView = "WEEK"
                            scope.launch { weekState.scrollToWeek(selectedDate) }
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    label = { Text("Woche") },
                )
                SegmentedButton(
                    selected = calendarView == "PLAN",
                    onClick = { calendarView = "PLAN" },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    label = { Text("Plan") },
                )
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = {
                if (calendarView == "WEEK") {
                    selectedWeekDate = today.toString()
                    scope.launch { weekState.animateScrollToWeek(today) }
                } else {
                    scope.launch { planListState.animateScrollToItem(todayMarkerIndex) }
                }
            }) {
                Icon(Icons.Outlined.Today, contentDescription = "Zu heute springen")
            }
        }
        Spacer(Modifier.height(16.dp))
        if (calendarView == "WEEK") {
            WeekCalendar(
                modifier = Modifier.height(158.dp),
                state = weekState,
                userScrollEnabled = false,
                weekHeader = { week -> CalendarWeekHeader(week.days.first().date, week.days.last().date) },
                dayContent = { day -> WeekCalendarCell(day.date, meals.count { day.date.toString() in it.termine }, awayEntries.any { it.datum == day.date.toString() }, day.date == selectedDate, day.date == today) { selectedWeekDate = day.date.toString() } },
            )
            Spacer(Modifier.height(12.dp))
            Box(Modifier.weight(1f)) {
                WeekAgenda(
                    date = selectedDate,
                    meals = meals.filter { selectedDate.toString() in it.termine },
                    awayEntry = awayEntries.firstOrNull { it.datum == selectedDate.toString() },
                    edit = { meal -> edit(meal, selectedDate) },
                    editAway = { editingAway = it; creatingAway = false },
                    openGallery = { meal, imageUrl -> galleryMeal = meal; galleryImageUrl = imageUrl },
                    modifier = Modifier.fillMaxSize(),
                )
                if (awayEntries.none { it.datum == selectedDate.toString() }) FloatingActionButton(
                    onClick = { actionDate = selectedDate }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                ) { Icon(Icons.Outlined.Add, contentDescription = "Eintrag hinzufügen") }
            }
        } else {
            Box(Modifier.weight(1f)) {
                PlanSchedule(planItems, planListState, today, edit, { editingAway = it; creatingAway = false }, { meal, imageUrl -> galleryMeal = meal; galleryImageUrl = imageUrl }, Modifier.fillMaxSize())
                if (awayEntries.none { it.datum == today.toString() }) FloatingActionButton(
                    onClick = { actionDate = today }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                ) { Icon(Icons.Outlined.Add, contentDescription = "Eintrag hinzufügen") }
            }
        }
    }
    galleryMeal?.let { meal -> MealImageGallery(meal, galleryImageUrl) { galleryMeal = null; galleryImageUrl = null } }
    actionDate?.let { date ->
        ModalBottomSheet(onDismissRequest = { actionDate = null }) {
            Text("Was möchtet ihr planen?", Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            ListItem(
                headlineContent = { Text("Mahlzeit planen") }, leadingContent = { Icon(Icons.Outlined.Add, null) },
                modifier = Modifier.clickable { actionDate = null; open(date) },
            )
            ListItem(
                headlineContent = { Text("Empfehlung finden") },
                supportingContent = { Text("Durch bereits gekochte Mahlzeiten stöbern") },
                leadingContent = { Icon(Icons.Outlined.Restaurant, null) },
                modifier = Modifier.clickable { actionDate = null; recommend(date) },
            )
            ListItem(
                headlineContent = { Text("Auswärts essen") },
                leadingContent = { Icon(Icons.Outlined.Restaurant, null) },
                modifier = Modifier.clickable { actionDate = null; editingAway = AuswaertsEintrag(datum = date.toString()); creatingAway = true },
            )
            Spacer(Modifier.height(24.dp))
        }
    }
    editingAway?.let { entry ->
        AwayEntryDialog(
            entry = entry,
            isNew = creatingAway,
            occupiedMealDates = meals.flatMap { it.termine }.toSet(),
            blockedDates = awayEntries.filterNot { it.id == entry.id }.mapNotNull { runCatching { LocalDate.parse(it.datum) }.getOrNull() }.toSet(),
            dismiss = { editingAway = null },
            save = { updated, replace ->
                val done = { editingAway = null }
                if (creatingAway) createAway(updated, replace, done) else updateAway(updated, replace, done)
            },
            delete = if (creatingAway) null else {{ deleteAway(entry.id) { editingAway = null } }},
        )
    }
}

@Composable
private fun CalendarWeekHeader(start: LocalDate, end: LocalDate) {
    Column(Modifier.padding(bottom = 8.dp)) {
        Text("${start.format(DateTimeFormatter.ofPattern("d. MMMM", Locale.GERMAN))} – ${end.format(DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMAN))}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) { listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").forEach { day -> Text(day, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary) } }
    }
}

@Composable
private fun WeekCalendarCell(date: LocalDate, mealCount: Int, blocked: Boolean, selected: Boolean, today: Boolean, select: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(82.dp).padding(horizontal = 2.dp, vertical = 3.dp).clip(MaterialTheme.shapes.medium).clickable(onClick = select),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = if (today || selected) BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary) else null,
    ) {
        Column(Modifier.padding(vertical = 7.dp, horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.GERMAN), style = MaterialTheme.typography.labelSmall, color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(
                color = if (today) MaterialTheme.colorScheme.primary else Color.Transparent,
                contentColor = if (today) MaterialTheme.colorScheme.onPrimary else LocalContentColor.current,
                shape = CircleShape,
            ) {
                Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                    Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            if (blocked) {
                Icon(Icons.Outlined.Restaurant, "Auswärts essen", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
            } else if (mealCount > 0) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.extraSmall) {
                    Text(mealCount.toString(), Modifier.padding(horizontal = 6.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
    }
}

@Composable
private fun WeekAgenda(date: LocalDate, meals: List<Mahlzeit>, awayEntry: AuswaertsEintrag?, edit: (Mahlzeit) -> Unit, editAway: (AuswaertsEintrag) -> Unit, openGallery: (Mahlzeit, String) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text(date.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)), modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(if (awayEntry != null) "Auswärts essen" else if (meals.isEmpty()) "Noch nichts geplant" else "${meals.size} ${if (meals.size == 1) "Mahlzeit" else "Mahlzeiten"} geplant", modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            if (awayEntry != null) {
                AwayEntryCard(awayEntry) { editAway(awayEntry) }
            } else if (meals.isEmpty()) {
                Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text("Zeit für etwas Leckeres.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Plane eine Mahlzeit für diesen Tag.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 72.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(meals, key = { it.id }) { meal ->
                        meal.eintraege.firstOrNull { it.datum == date.toString() }?.let { occurrence ->
                            WeekAgendaMealCard(meal, occurrence, { edit(meal) }) { imageUrl -> openGallery(meal, imageUrl) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekAgendaMealCard(meal: Mahlzeit, occurrence: MahlzeitEintrag, click: () -> Unit, openGallery: (String) -> Unit) = Card(
    modifier = Modifier.fillMaxWidth().clickable(onClick = click),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
) {
    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        val imageUrl = occurrence.letztesBild()
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            if (imageUrl != null) {
                AsyncImage(
                    imageUrl,
                    "Bild von ${meal.name} ansehen",
                    Modifier.fillMaxSize().clip(MaterialTheme.shapes.small).clickable { openGallery(imageUrl) },
                    contentScale = ContentScale.Crop,
                )
            } else {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.small) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(meal.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            meal.notiz?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            meal.tags.filterNot { it == Tag.VEGETARISCH.name || it == Tag.VEGAN.name }.takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" · ") { tag -> Tag.entries.find { it.name == tag }?.label ?: tag }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        val rating = occurrence.durchschnitt()
        Box(Modifier.width(52.dp), contentAlignment = Alignment.Center) {
            Surface(
                color = if (rating == null) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    rating?.let { String.format(Locale.GERMANY, "%.1f", it) } ?: "–",
                    Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (rating == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) { DietMarker(meal) }
    }
}

@Composable
private fun AwayEntryCard(entry: AuswaertsEintrag, click: () -> Unit) = Card(
    modifier = Modifier.fillMaxWidth().clickable(onClick = click),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Restaurant, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text("Auswärts essen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            entry.notiz?.let { Text(it, color = MaterialTheme.colorScheme.onSecondaryContainer) }
        }
        Icon(Icons.Outlined.Edit, contentDescription = "Auswärts-Eintrag bearbeiten")
    }
}

@Composable
private fun AwayEntryDialog(
    entry: AuswaertsEintrag,
    isNew: Boolean,
    occupiedMealDates: Set<String>,
    blockedDates: Set<LocalDate>,
    dismiss: () -> Unit,
    save: (AuswaertsEintrag, Boolean) -> Unit,
    delete: (() -> Unit)?,
) {
    var selectedDateEpochDay by rememberSaveable(entry.id) { mutableLongStateOf(LocalDate.parse(entry.datum).toEpochDay()) }
    var note by rememberSaveable(entry.id) { mutableStateOf(entry.notiz.orEmpty()) }
    var confirmReplace by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val selectedDate = LocalDate.ofEpochDay(selectedDateEpochDay)
    AlertDialog(
        onDismissRequest = dismiss,
        icon = { Icon(Icons.Outlined.Restaurant, null) },
        title = { Text(if (isNew) "Auswärts essen" else "Auswärts-Eintrag bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                MealDateSelector(selectedDate, blockedDates) { selectedDateEpochDay = it.toEpochDay() }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Notiz (optional)") },
                    placeholder = { Text("z. B. Restaurant oder bei Anna") },
                    singleLine = true,
                )
                if (delete != null) TextButton(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Icon(Icons.Outlined.DeleteOutline, null); Spacer(Modifier.width(4.dp)); Text("Eintrag löschen") }
            }
        },
        confirmButton = { Button(onClick = {
            val updated = entry.copy(datum = selectedDate.toString(), notiz = note.trim().ifBlank { null })
            if (updated.datum in occupiedMealDates) confirmReplace = true else save(updated, false)
        }) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Abbrechen") } },
    )
    if (confirmReplace) AlertDialog(
        onDismissRequest = { confirmReplace = false },
        title = { Text("Mahlzeiten ersetzen?") },
        text = { Text("Alle für diesen Tag geplanten Mahlzeiten werden entfernt und der Tag wird als „Auswärts essen“ markiert.") },
        confirmButton = { TextButton(onClick = {
            confirmReplace = false
            save(entry.copy(datum = selectedDate.toString(), notiz = note.trim().ifBlank { null }), true)
        }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Ersetzen") } },
        dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text("Abbrechen") } },
    )
    if (confirmDelete && delete != null) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Auswärts-Eintrag löschen?") },
        text = { Text("Danach können für diesen Tag wieder Mahlzeiten geplant werden.") },
        confirmButton = { TextButton(onClick = delete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Löschen") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
    )
}

internal sealed interface PlanItem {
    data class Month(val yearMonth: YearMonth) : PlanItem
    data class TodayMarker(val date: LocalDate) : PlanItem
    data class Day(val date: LocalDate, val meals: List<Mahlzeit>, val awayEntry: AuswaertsEintrag?) : PlanItem
}

internal fun scheduledPlanItems(meals: List<Mahlzeit>, awayEntries: List<AuswaertsEintrag>, today: LocalDate): List<PlanItem> {
    val mealsByDate = meals.flatMap { meal ->
        meal.termine.mapNotNull { date -> runCatching { LocalDate.parse(date) }.getOrNull()?.let { it to meal } }
    }.groupBy({ it.first }, { it.second })
    val awayByDate = awayEntries.mapNotNull { entry -> runCatching { LocalDate.parse(entry.datum) }.getOrNull()?.let { it to entry } }.toMap()
    val dates = mealsByDate.keys + awayByDate.keys
    val currentMonth = YearMonth.from(today)
    val datesByMonth = dates.groupBy { YearMonth.from(it) }
    return (datesByMonth.keys + currentMonth).sorted().flatMap { month ->
        buildList {
            add(PlanItem.Month(month))
            var markerAdded = false
            datesByMonth[month].orEmpty().sorted().forEach { date ->
                if (month == currentMonth && !markerAdded && !date.isBefore(today)) {
                    add(PlanItem.TodayMarker(today))
                    markerAdded = true
                }
                add(PlanItem.Day(date, mealsByDate[date].orEmpty(), awayByDate[date]))
            }
            if (month == currentMonth && !markerAdded) add(PlanItem.TodayMarker(today))
        }
    }
}

@Composable
private fun PlanSchedule(items: List<PlanItem>, state: androidx.compose.foundation.lazy.LazyListState, today: LocalDate, edit: (Mahlzeit, LocalDate) -> Unit, editAway: (AuswaertsEintrag) -> Unit, openGallery: (Mahlzeit, String) -> Unit, modifier: Modifier = Modifier) {
    if (items.isEmpty()) {
        Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Text("Noch nichts geplant", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Plane deine erste Mahlzeit für heute.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
            }
        }
    } else {
        LazyColumn(state = state, modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { item -> when (item) { is PlanItem.Month -> "month-${item.yearMonth}"; is PlanItem.TodayMarker -> "today-${item.date}"; is PlanItem.Day -> "day-${item.date}" } }) { item ->
                when (item) {
                    is PlanItem.Month -> Text(item.yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN)), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
                    is PlanItem.TodayMarker -> TodayMarkerRow(item.date)
                    is PlanItem.Day -> PlanDayRow(item.date, item.meals, item.awayEntry, item.date == today, edit, editAway, openGallery)
                }
            }
            if (items.none { it is PlanItem.Day }) item(key = "empty-plan") {
                PlanEmptyState(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun TodayMarkerRow(date: LocalDate) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
        Text(
            "Heute · ${date.format(DateTimeFormatter.ofPattern("d. MMMM", Locale.GERMAN))}",
            Modifier.padding(horizontal = 12.dp),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun PlanEmptyState(modifier: Modifier = Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(horizontal = 28.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text("Noch nichts geplant", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Plane deine erste Mahlzeit für heute.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlanDayRow(date: LocalDate, meals: List<Mahlzeit>, awayEntry: AuswaertsEintrag?, today: Boolean, edit: (Mahlzeit, LocalDate) -> Unit, editAway: (AuswaertsEintrag) -> Unit, openGallery: (Mahlzeit, String) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.width(54.dp).padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.GERMAN), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = if (today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            awayEntry?.let { AwayEntryCard(it) { editAway(it) } }
            meals.forEach { meal ->
                meal.eintraege.firstOrNull { it.datum == date.toString() }?.let { occurrence ->
                    WeekAgendaMealCard(meal, occurrence, { edit(meal, date) }) { imageUrl -> openGallery(meal, imageUrl) }
                }
            }
        }
    }
}
