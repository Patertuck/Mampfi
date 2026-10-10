@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package ch.mampfi.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import ch.mampfi.app.data.*
import com.kizitonwose.calendar.compose.WeekCalendar
import com.kizitonwose.calendar.compose.weekcalendar.rememberWeekCalendarState
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
internal fun CalendarScreen(
    meals: List<Mahlzeit>, awayEntries: List<AuswaertsEintrag>, open: (LocalDate) -> Unit,
    recommend: (LocalDate) -> Unit,
    edit: (Mahlzeit, LocalDate) -> Unit,
    moveMeal: (Mahlzeit, MahlzeitEintrag, LocalDate) -> Unit,
    moveAway: (AuswaertsEintrag, LocalDate) -> Unit,
    createAway: (AuswaertsEintrag, () -> Unit) -> Unit,
    updateAway: (AuswaertsEintrag, () -> Unit) -> Unit,
    deleteAway: (String, () -> Unit) -> Unit,
    isRefreshing: Boolean,
    refresh: () -> Unit,
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
    var showWeekPicker by rememberSaveable { mutableStateOf(false) }
    var planInitialised by rememberSaveable { mutableStateOf(false) }
    var galleryMeal by remember { mutableStateOf<Mahlzeit?>(null) }
    var galleryImageUrl by remember { mutableStateOf<String?>(null) }
    var actionDate by remember { mutableStateOf<LocalDate?>(null) }
    var editingAway by remember { mutableStateOf<AuswaertsEintrag?>(null) }
    var creatingAway by remember { mutableStateOf(false) }
    val planListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val swipeThreshold = with(LocalDensity.current) { 64.dp.toPx() }
    val calendarHeight = if (LocalDensity.current.fontScale > 1.2f) 190.dp else 158.dp
    fun navigateToWeek(date: LocalDate) {
        scope.launch {
            weekState.animateScrollToWeek(date)
            selectedWeekDate = date.toString()
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
                    adjacentWeekDate(weekState.firstVisibleWeek.days.first().date, weekOffset, calendarStartDate, calendarEndDate)?.let(::navigateToWeek)
                }
                dragDistance = 0f
            },
        )
    } else Modifier
    val calendarContent: @Composable () -> Unit = {
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
                    navigateToWeek(today)
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
                modifier = Modifier.height(calendarHeight),
                state = weekState,
                userScrollEnabled = false,
                weekHeader = { week ->
                    val weekStart = week.days.first().date
                    CalendarWeekHeader(
                        start = weekStart,
                        end = week.days.last().date,
                        previousEnabled = adjacentWeekDate(weekStart, -1, calendarStartDate, calendarEndDate) != null,
                        nextEnabled = adjacentWeekDate(weekStart, 1, calendarStartDate, calendarEndDate) != null,
                        previous = { adjacentWeekDate(weekStart, -1, calendarStartDate, calendarEndDate)?.let(::navigateToWeek) },
                        next = { adjacentWeekDate(weekStart, 1, calendarStartDate, calendarEndDate)?.let(::navigateToWeek) },
                        chooseDate = { showWeekPicker = true },
                    )
                },
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
                FloatingActionButton(
                    onClick = { actionDate = selectedDate },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Planen")
                }
            }
        } else {
            Box(Modifier.weight(1f)) {
                PlanSchedule(
                    items = planItems,
                    state = planListState,
                    today = today,
                    meals = meals,
                    awayEntries = awayEntries,
                    edit = edit,
                    editAway = { editingAway = it; creatingAway = false },
                    moveMeal = moveMeal,
                    moveAway = moveAway,
                    openGallery = { meal, imageUrl -> galleryMeal = meal; galleryImageUrl = imageUrl },
                    modifier = Modifier.fillMaxSize(),
                )
                FloatingActionButton(
                    onClick = { actionDate = today },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Planen")
                }
            }
        }
      }
    }
    if (calendarView == "WEEK") {
        PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = refresh, modifier = Modifier.fillMaxSize()) {
            calendarContent()
        }
    } else {
        calendarContent()
    }
    if (showWeekPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.toDatePickerMillis(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    datePickerMillisToLocalDate(utcTimeMillis) in calendarStartDate..calendarEndDate
            },
        )
        DatePickerDialog(
            onDismissRequest = { showWeekPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let(::datePickerMillisToLocalDate)?.let(::navigateToWeek)
                    showWeekPicker = false
                }) { Text("Anzeigen") }
            },
            dismissButton = { TextButton(onClick = { showWeekPicker = false }) { Text("Abbrechen") } },
        ) { DatePicker(state = pickerState) }
    }
    galleryMeal?.let { meal -> MealImageGallery(meal, galleryImageUrl) { galleryMeal = null; galleryImageUrl = null } }
    actionDate?.let { date ->
        ModalBottomSheet(
            onDismissRequest = { actionDate = null },
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Was möchtet ihr planen?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    date.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                PlanningActionItem(
                    title = "Mahlzeit planen",
                    icon = Icons.Outlined.RestaurantMenu,
                    tint = MaterialTheme.colorScheme.primary,
                ) { actionDate = null; open(date) }
                PlanningActionItem(
                    title = "Empfehlung finden",
                    icon = Icons.Outlined.AutoAwesome,
                    tint = MaterialTheme.colorScheme.secondary,
                ) { actionDate = null; recommend(date) }
                if (awayEntries.none { it.datum == date.toString() }) {
                    PlanningActionItem(
                        title = "Auswärts essen",
                        icon = Icons.Outlined.Restaurant,
                        tint = MaterialTheme.colorScheme.tertiary,
                    ) {
                        actionDate = null
                        editingAway = AuswaertsEintrag(datum = date.toString())
                        creatingAway = true
                    }
                }
            }
        }
    }
    editingAway?.let { entry ->
        AwayEntryDialog(
            entry = entry,
            isNew = creatingAway,
            blockedDates = awayEntries.filterNot { it.id == entry.id }.mapNotNull { runCatching { LocalDate.parse(it.datum) }.getOrNull() }.toSet(),
            dismiss = { editingAway = null },
            save = { updated ->
                val done = { editingAway = null }
                if (creatingAway) createAway(updated, done) else updateAway(updated, done)
            },
            delete = if (creatingAway) null else {{ deleteAway(entry.id) { editingAway = null } }},
        )
    }
}

@Composable
private fun PlanningActionItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    click: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = click),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = tint.copy(alpha = 0.14f), contentColor = tint) {
                Icon(icon, contentDescription = null, modifier = Modifier.padding(10.dp).size(24.dp))
            }
            Text(title, Modifier.weight(1f).padding(horizontal = 14.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CalendarWeekHeader(
    start: LocalDate,
    end: LocalDate,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    previous: () -> Unit,
    next: () -> Unit,
    chooseDate: () -> Unit,
) {
    Column(Modifier.padding(bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = previous, enabled = previousEnabled) {
                Icon(Icons.Outlined.ChevronLeft, contentDescription = "Vorherige Woche")
            }
            TextButton(onClick = chooseDate, modifier = Modifier.weight(1f)) {
                Text(
                    "${start.format(DateTimeFormatter.ofPattern("d. MMMM", Locale.GERMAN))} – ${end.format(DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMAN))}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
            IconButton(onClick = next, enabled = nextEnabled) {
                Icon(Icons.Outlined.ChevronRight, contentDescription = "Nächste Woche")
            }
        }
        Row(Modifier.fillMaxWidth()) { listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").forEach { day -> Text(day, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary) } }
    }
}

internal fun adjacentWeekDate(currentWeekStart: LocalDate, offset: Long, minimum: LocalDate, maximum: LocalDate): LocalDate? =
    currentWeekStart.plusWeeks(offset).takeIf { it in minimum..maximum }

@Composable
private fun WeekCalendarCell(date: LocalDate, mealCount: Int, hasAwayEntry: Boolean, selected: Boolean, today: Boolean, select: () -> Unit) {
    val stateDescription = buildList {
        add(date.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)))
        if (hasAwayEntry) add("Auswärts essen")
        when {
            mealCount == 1 -> add("Eine Mahlzeit geplant")
            mealCount > 1 -> add("$mealCount Mahlzeiten geplant")
            !hasAwayEntry -> add("Nichts geplant")
        }
        if (today) add("Heute")
        if (selected) add("Ausgewählt")
    }.joinToString(", ")
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 82.dp).padding(horizontal = 2.dp, vertical = 3.dp).clip(MaterialTheme.shapes.medium).clickable(onClick = select).semantics { contentDescription = stateDescription },
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (hasAwayEntry) Icon(Icons.Outlined.Restaurant, "Auswärts essen", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                if (mealCount > 0) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.extraSmall) {
                        Text(mealCount.toString(), Modifier.padding(horizontal = 6.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
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
            val summary = buildList {
                if (awayEntry != null) add("Auswärts essen")
                if (meals.isNotEmpty()) add("${meals.size} ${if (meals.size == 1) "Mahlzeit" else "Mahlzeiten"} geplant")
            }.joinToString(" · ").ifEmpty { "Noch nichts geplant" }
            Text(summary, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            if (awayEntry == null && meals.isEmpty()) {
                Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    MampfiEmptyMascot(Modifier.size(96.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Zeit für etwas Leckeres.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Plane eine Mahlzeit für diesen Tag.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 72.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    awayEntry?.let { entry -> item(key = "away-${entry.id}") { AwayEntryCard(entry) { editAway(entry) } } }
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
private fun WeekAgendaMealCard(meal: Mahlzeit, occurrence: MahlzeitEintrag, click: () -> Unit, modifier: Modifier = Modifier, openGallery: (String) -> Unit) = Card(
    modifier = modifier.fillMaxWidth().clickable(onClick = click),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
) {
    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        val imageUrl = occurrence.letztesBild()
        MealThumbnail(
            imageUrl = imageUrl,
            contentDescription = imageUrl?.let { "Bild von ${meal.name} ansehen" },
            modifier = Modifier.size(64.dp),
            openGallery = imageUrl?.let { { openGallery(it) } },
        )
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
private fun AwayEntryCard(entry: AuswaertsEintrag, modifier: Modifier = Modifier, click: () -> Unit) = Card(
    modifier = modifier.fillMaxWidth().clickable(onClick = click),
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
    blockedDates: Set<LocalDate>,
    dismiss: () -> Unit,
    save: (AuswaertsEintrag) -> Unit,
    delete: (() -> Unit)?,
) {
    var selectedDateEpochDay by rememberSaveable(entry.id) { mutableLongStateOf(LocalDate.parse(entry.datum).toEpochDay()) }
    var note by rememberSaveable(entry.id) { mutableStateOf(entry.notiz.orEmpty()) }
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
            save(updated)
        }) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Abbrechen") } },
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
    val visibleWindow = (0L..7L).map(today::plusDays)
    val dates = mealsByDate.keys + awayByDate.keys + visibleWindow
    val currentMonth = YearMonth.from(today)
    val datesByMonth = dates.groupBy { YearMonth.from(it) }
    return datesByMonth.keys.sorted().flatMap { month ->
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
private fun PlanSchedule(
    items: List<PlanItem>,
    state: androidx.compose.foundation.lazy.LazyListState,
    today: LocalDate,
    meals: List<Mahlzeit>,
    awayEntries: List<AuswaertsEintrag>,
    edit: (Mahlzeit, LocalDate) -> Unit,
    editAway: (AuswaertsEintrag) -> Unit,
    moveMeal: (Mahlzeit, MahlzeitEintrag, LocalDate) -> Unit,
    moveAway: (AuswaertsEintrag, LocalDate) -> Unit,
    openGallery: (Mahlzeit, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf<PlanDragItem?>(null) }
    var pointerInWindow by remember { mutableStateOf<Offset?>(null) }
    var listBounds by remember { mutableStateOf(Rect.Zero) }
    val dayBounds = remember { mutableStateMapOf<LocalDate, Rect>() }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val edgeSize = with(density) { 56.dp.toPx() }
    val scrollStep = with(density) { 18.dp.toPx() }
    fun dateAt(pointer: Offset): LocalDate? {
        val visibleKeys = state.layoutInfo.visibleItemsInfo.map { it.key }.toSet()
        return dayBounds.entries.firstOrNull { (date, bounds) -> "day-$date" in visibleKeys && pointer in bounds }?.key
    }
    val hoveredDate = pointerInWindow?.let(::dateAt)

    fun finishDrag() {
        val dragged = dragging
        val target = pointerInWindow?.let(::dateAt)
        dragging = null
        pointerInWindow = null
        if (dragged == null || target == null || target == dragged.sourceDate) return
        val error = planDropError(dragged, target, meals, awayEntries)
        if (error != null) {
            scope.launch { snackbar.showSnackbar(error) }
            return
        }
        when (dragged) {
            is PlanDragItem.Meal -> moveMeal(dragged.meal, dragged.entry, target)
            is PlanDragItem.Away -> moveAway(dragged.entry, target)
        }
    }

    LaunchedEffect(dragging) {
        while (dragging != null) {
            pointerInWindow?.y?.let { y ->
                when {
                    y < listBounds.top + edgeSize -> state.scrollBy(-scrollStep)
                    y > listBounds.bottom - edgeSize -> state.scrollBy(scrollStep)
                }
            }
            delay(32)
        }
    }

    Box(modifier.onGloballyPositioned { listBounds = it.boundsInWindow() }) {
        LazyColumn(state = state, modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 88.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { item -> when (item) { is PlanItem.Month -> "month-${item.yearMonth}"; is PlanItem.TodayMarker -> "today-${item.date}"; is PlanItem.Day -> "day-${item.date}" } }) { item ->
                when (item) {
                    is PlanItem.Month -> Text(item.yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN)), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
                    is PlanItem.TodayMarker -> TodayMarkerRow(item.date)
                    is PlanItem.Day -> PlanDayRow(
                        date = item.date,
                        meals = item.meals,
                        awayEntry = item.awayEntry,
                        today = item.date == today,
                        dragging = dragging,
                        dropTarget = item.date == hoveredDate,
                        edit = edit,
                        editAway = editAway,
                        openGallery = openGallery,
                        registerBounds = { bounds -> dayBounds[item.date] = bounds },
                        startDrag = { dragItem, position ->
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            dragging = dragItem
                            pointerInWindow = position
                        },
                        dragBy = { amount -> pointerInWindow = pointerInWindow?.plus(amount) },
                        finishDrag = ::finishDrag,
                        cancelDrag = { dragging = null; pointerInWindow = null },
                    )
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 76.dp))
        dragging?.let { dragItem ->
            pointerInWindow?.let { pointer ->
                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(
                        (pointer.x - listBounds.left + with(density) { 12.dp.toPx() }).roundToInt(),
                        (pointer.y - listBounds.top + with(density) { 12.dp.toPx() }).roundToInt(),
                    ),
                    properties = PopupProperties(focusable = false, clippingEnabled = false),
                ) {
                    Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 8.dp, shadowElevation = 8.dp) {
                        Row(Modifier.widthIn(max = 260.dp).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (dragItem is PlanDragItem.Away) Icons.Outlined.Restaurant else Icons.Outlined.RestaurantMenu, null)
                            Spacer(Modifier.width(8.dp))
                            Text(dragItem.label, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

internal sealed interface PlanDragItem {
    val sourceDate: LocalDate
    val label: String

    data class Meal(val meal: Mahlzeit, val entry: MahlzeitEintrag) : PlanDragItem {
        override val sourceDate: LocalDate = LocalDate.parse(entry.datum)
        override val label: String = meal.name
    }

    data class Away(val entry: AuswaertsEintrag) : PlanDragItem {
        override val sourceDate: LocalDate = LocalDate.parse(entry.datum)
        override val label: String = "Auswärts essen"
    }
}

internal fun planDropError(item: PlanDragItem, target: LocalDate, meals: List<Mahlzeit>, awayEntries: List<AuswaertsEintrag>): String? = when (item) {
    is PlanDragItem.Meal -> if (meals.firstOrNull { it.id == item.meal.id }?.eintraege.orEmpty().any { it.id != item.entry.id && it.datum == target.toString() }) {
        "Diese Mahlzeit ist am Zieltag bereits geplant."
    } else null
    is PlanDragItem.Away -> if (awayEntries.any { it.id != item.entry.id && it.datum == target.toString() }) {
        "Am Zieltag gibt es bereits einen Auswärts-Eintrag."
    } else null
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
private fun PlanDayRow(
    date: LocalDate,
    meals: List<Mahlzeit>,
    awayEntry: AuswaertsEintrag?,
    today: Boolean,
    dragging: PlanDragItem?,
    dropTarget: Boolean,
    edit: (Mahlzeit, LocalDate) -> Unit,
    editAway: (AuswaertsEintrag) -> Unit,
    openGallery: (Mahlzeit, String) -> Unit,
    registerBounds: (Rect) -> Unit,
    startDrag: (PlanDragItem, Offset) -> Unit,
    dragBy: (Offset) -> Unit,
    finishDrag: () -> Unit,
    cancelDrag: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().onGloballyPositioned { registerBounds(it.boundsInWindow()) },
        color = if (dropTarget) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        shape = MaterialTheme.shapes.medium,
        border = if (dropTarget) BorderStroke(1.dp, MaterialTheme.colorScheme.secondary) else null,
    ) {
      Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.width(54.dp).padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.GERMAN), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = if (today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
        Column(Modifier.weight(1f).heightIn(min = 64.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (awayEntry == null && meals.isEmpty()) {
                Box(Modifier.fillMaxWidth().heightIn(min = 64.dp), contentAlignment = Alignment.CenterStart) {
                    Text("Noch nichts geplant", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            }
            awayEntry?.let { entry ->
                var cardBounds by remember(entry.id) { mutableStateOf(Rect.Zero) }
                val item = remember(entry) { PlanDragItem.Away(entry) }
                val dragModifier = Modifier
                    .alpha(if ((dragging as? PlanDragItem.Away)?.entry?.id == entry.id) 0.45f else 1f)
                    .onGloballyPositioned { cardBounds = it.boundsInWindow() }
                    .pointerInput(item) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { startDrag(item, cardBounds.topLeft + it) },
                            onDrag = { change, amount -> change.consume(); dragBy(amount) },
                            onDragEnd = finishDrag,
                            onDragCancel = cancelDrag,
                        )
                    }
                AwayEntryCard(entry, dragModifier) { editAway(entry) }
            }
            meals.forEach { meal ->
                meal.eintraege.firstOrNull { it.datum == date.toString() }?.let { occurrence ->
                    var cardBounds by remember(occurrence.id) { mutableStateOf(Rect.Zero) }
                    val item = remember(meal, occurrence) { PlanDragItem.Meal(meal, occurrence) }
                    val dragModifier = Modifier
                        .alpha(if ((dragging as? PlanDragItem.Meal)?.entry?.id == occurrence.id) 0.45f else 1f)
                        .onGloballyPositioned { cardBounds = it.boundsInWindow() }
                        .pointerInput(item) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { startDrag(item, cardBounds.topLeft + it) },
                                onDrag = { change, amount -> change.consume(); dragBy(amount) },
                                onDragEnd = finishDrag,
                                onDragCancel = cancelDrag,
                            )
                        }
                    WeekAgendaMealCard(meal, occurrence, { edit(meal, date) }, dragModifier) { imageUrl -> openGallery(meal, imageUrl) }
                }
            }
        }
      }
    }
}
