@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package ch.mampfi.app

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.compose.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ch.mampfi.app.data.*
import coil3.compose.AsyncImage
import com.kizitonwose.calendar.compose.WeekCalendar
import com.kizitonwose.calendar.compose.weekcalendar.rememberWeekCalendarState
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MampfiApp(vm: MealViewModel, connectedViaTailscale: Boolean = false, endpointStore: EndpointSettingsStore) = MampfiTheme {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val updater = remember(context) { AppUpdater(context) }
    val updateScope = rememberCoroutineScope()
    var availableUpdate by remember { mutableStateOf<AvailableUpdate?>(null) }
    var updateDownloadProgress by remember { mutableStateOf<Int?>(null) }
    var updateDownloadError by remember { mutableStateOf<String?>(null) }
    val currentRoute = nav.currentBackStackEntryAsState().value?.destination?.route
    val appSettings by endpointStore.settings.collectAsState(initial = EndpointSettings())
    DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> vm.startForegroundRefresh()
                Lifecycle.Event.ON_PAUSE -> vm.stopForegroundRefresh()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) vm.startForegroundRefresh()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            vm.stopForegroundRefresh()
        }
    }
    LaunchedEffect(Unit) { vm.message.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(connectedViaTailscale) {
        if (connectedViaTailscale) snackbar.showSnackbar("Verbunden über Tailscale")
    }
    LaunchedEffect(Unit) {
        if (BuildConfig.UPDATE_METADATA_URL.isNotBlank()) {
            availableUpdate = withContext(Dispatchers.IO) { updater.checkForUpdate(BuildConfig.UPDATE_METADATA_URL) }
        }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
                listOf(
                    Triple("kalender", "Kalender", Icons.Outlined.CalendarMonth),
                    Triple("uebersicht", "Übersicht", Icons.Outlined.ViewList),
                    Triple("einstellungen", "Einstellungen", Icons.Outlined.Settings),
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
            composable("kalender") { CalendarScreen(
                meals = vm.meals.collectAsState().value,
                open = { nav.navigate("bearbeiten/$it") },
                edit = { meal, date -> meal.eintraege.firstOrNull { it.datum == date.toString() }?.let { occurrence -> nav.navigate("bearbeiten/$date?meal=${meal.id}&entry=${occurrence.id}") } },
            ) }
            composable("uebersicht") { OverviewScreen(vm.meals.collectAsState().value) { meal ->
                val latest = meal.eintraege.maxByOrNull { it.datum }
                nav.navigate("bearbeiten/${latest?.datum ?: LocalDate.now()}?meal=${meal.id}${latest?.let { "&entry=${it.id}" }.orEmpty()}")
            } }
            composable("einstellungen") { EndpointSetupScreen(endpointStore, configured = true) { nav.popBackStack() } }
            composable("bearbeiten/{date}?meal={meal}&entry={entry}") { entry -> EditScreen(vm, LocalDate.parse(entry.arguments!!.getString("date")!!), entry.arguments?.getString("meal"), entry.arguments?.getString("entry"), appSettings.firstRaterName, appSettings.secondRaterName) { nav.popBackStack() } }
        }
    }
    availableUpdate?.let { update ->
        UpdateAvailableDialog(
            version = update.version,
            progress = updateDownloadProgress,
            dismiss = { if (updateDownloadProgress == null) availableUpdate = null },
            download = {
                updateDownloadProgress = 0
                updateScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            updater.downloadApk(update.apkUrl, "mampfi-${update.version.filter { it.isLetterOrDigit() || it == '.' }}") { progress ->
                                updateDownloadProgress = progress
                            }
                        }
                    } catch (_: Exception) {
                        updateDownloadProgress = null
                        updateDownloadError = "Update konnte nicht heruntergeladen werden."
                    }
                }
            },
        )
    }
    updateDownloadError?.let { error ->
        LaunchedEffect(error) {
            snackbar.showSnackbar(error)
            updateDownloadError = null
        }
    }
}

@Composable
private fun CalendarScreen(meals: List<Mahlzeit>, open: (LocalDate) -> Unit, edit: (Mahlzeit, LocalDate) -> Unit) {
    val currentMonth = remember { YearMonth.now() }
    val calendarStartDate = remember(currentMonth) { currentMonth.minusMonths(24).atDay(1) }
    val calendarEndDate = remember(currentMonth) { currentMonth.plusMonths(24).atEndOfMonth() }
    val weekState = rememberWeekCalendarState(
        startDate = calendarStartDate,
        endDate = calendarEndDate,
        firstVisibleWeekDate = LocalDate.now(),
        firstDayOfWeek = java.time.DayOfWeek.MONDAY,
    )
    var calendarView by rememberSaveable { mutableStateOf("WEEK") }
    var selectedWeekDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var receivedInitialWeek by remember { mutableStateOf(false) }
    var planInitialised by rememberSaveable { mutableStateOf(false) }
    var galleryMeal by remember { mutableStateOf<Mahlzeit?>(null) }
    var galleryImageUrl by remember { mutableStateOf<String?>(null) }
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
    val planItems = remember(meals) { scheduledPlanItems(meals) }
    val firstFuturePlanIndex = if (planItems.isEmpty()) {
        0
    } else {
        val firstFutureDayIndex = planItems.indexOfFirst { it is PlanItem.Day && !it.date.isBefore(LocalDate.now()) }
            .let { if (it >= 0) it else planItems.indexOfLast { it is PlanItem.Day }.coerceAtLeast(0) }
        planItems.subList(0, firstFutureDayIndex + 1).indexOfLast { it is PlanItem.Month }.coerceAtLeast(0)
    }
    LaunchedEffect(calendarView, planItems) {
        if (calendarView == "PLAN" && !planInitialised && planItems.isNotEmpty()) {
            planListState.scrollToItem(firstFuturePlanIndex)
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
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
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
        Spacer(Modifier.height(16.dp))
        if (calendarView == "WEEK") {
            WeekCalendar(
                modifier = Modifier.height(158.dp),
                state = weekState,
                userScrollEnabled = false,
                weekHeader = { week -> CalendarWeekHeader(week.days.first().date, week.days.last().date) },
                dayContent = { day -> WeekCalendarCell(day.date, meals.count { day.date.toString() in it.termine }, day.date == selectedDate) { selectedWeekDate = day.date.toString() } },
            )
            Spacer(Modifier.height(12.dp))
            WeekAgenda(
                date = selectedDate,
                meals = meals.filter { selectedDate.toString() in it.termine },
                plan = { open(selectedDate) },
                edit = { meal -> edit(meal, selectedDate) },
                openGallery = { meal, imageUrl -> galleryMeal = meal; galleryImageUrl = imageUrl },
                modifier = Modifier.weight(1f),
            )
        } else {
            PlanSchedule(planItems, planListState, open, edit, { meal, imageUrl -> galleryMeal = meal; galleryImageUrl = imageUrl }, Modifier.weight(1f))
        }
    }
    galleryMeal?.let { meal -> MealImageGallery(meal, galleryImageUrl) { galleryMeal = null; galleryImageUrl = null } }
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
private fun WeekCalendarCell(date: LocalDate, mealCount: Int, selected: Boolean, select: () -> Unit) {
    val today = date == LocalDate.now()
    Surface(
        modifier = Modifier.fillMaxWidth().height(82.dp).padding(horizontal = 2.dp, vertical = 3.dp).clip(MaterialTheme.shapes.medium).clickable(onClick = select),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = if (today || selected) BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary) else null,
    ) {
        Column(Modifier.padding(vertical = 7.dp, horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.GERMAN), style = MaterialTheme.typography.labelSmall, color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (mealCount > 0) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.extraSmall) {
                    Text(mealCount.toString(), Modifier.padding(horizontal = 6.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
    }
}

@Composable
private fun WeekAgenda(date: LocalDate, meals: List<Mahlzeit>, plan: () -> Unit, edit: (Mahlzeit) -> Unit, openGallery: (Mahlzeit, String) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text(date.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)), modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(if (meals.isEmpty()) "Noch nichts geplant" else "${meals.size} ${if (meals.size == 1) "Mahlzeit" else "Mahlzeiten"} geplant", modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            if (meals.isEmpty()) {
                Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text("Zeit für etwas Leckeres.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Plane eine Mahlzeit für diesen Tag.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(meals, key = { it.id }) { meal ->
                        meal.eintraege.firstOrNull { it.datum == date.toString() }?.let { occurrence ->
                            WeekAgendaMealCard(meal, occurrence, { edit(meal) }) { imageUrl -> openGallery(meal, imageUrl) }
                        }
                    }
                }
            }
            Button(onClick = plan, modifier = Modifier.fillMaxWidth()) { Text("Mahlzeit planen") }
        }
    }
}

@Composable
private fun WeekAgendaMealCard(meal: Mahlzeit, occurrence: MahlzeitEintrag, click: () -> Unit, openGallery: (String) -> Unit) = Card(
    modifier = Modifier.fillMaxWidth().clickable(onClick = click),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
) {
    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        occurrence.letztesBild()?.let { imageUrl ->
            AsyncImage(
                imageUrl,
                "Bild von ${meal.name} ansehen",
                Modifier.size(64.dp).clip(MaterialTheme.shapes.small).clickable { openGallery(imageUrl) },
                contentScale = ContentScale.Crop,
            )
        }
        Column(Modifier.padding(start = if (occurrence.letztesBild() == null) 0.dp else 12.dp).weight(1f)) {
            Text(meal.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            meal.tags.filterNot { it == Tag.VEGETARISCH.name && Tag.VEGAN.name in meal.tags }.takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" · ") { tag -> Tag.entries.find { it.name == tag }?.label ?: tag }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        occurrence.durchschnitt()?.let { Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) { Text(String.format(Locale.GERMANY, "%.1f", it), Modifier.padding(horizontal = 8.dp, vertical = 5.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer) } }
        DietMarker(meal, Modifier.padding(start = 8.dp))
    }
}

private sealed interface PlanItem {
    data class Month(val yearMonth: YearMonth) : PlanItem
    data class Day(val date: LocalDate, val meals: List<Mahlzeit>) : PlanItem
}

private fun scheduledPlanItems(meals: List<Mahlzeit>): List<PlanItem> {
    val mealsByDate = meals.flatMap { meal ->
        meal.termine.mapNotNull { date -> runCatching { LocalDate.parse(date) }.getOrNull()?.let { it to meal } }
    }.groupBy({ it.first }, { it.second })
    return mealsByDate.entries.groupBy { YearMonth.from(it.key) }.toSortedMap().flatMap { (month, days) ->
        listOf(PlanItem.Month(month)) + days.sortedBy { it.key }.map { (date, scheduledMeals) -> PlanItem.Day(date, scheduledMeals) }
    }
}

@Composable
private fun PlanSchedule(items: List<PlanItem>, state: androidx.compose.foundation.lazy.LazyListState, open: (LocalDate) -> Unit, edit: (Mahlzeit, LocalDate) -> Unit, openGallery: (Mahlzeit, String) -> Unit, modifier: Modifier = Modifier) {
    if (items.isEmpty()) {
        Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Text("Noch nichts geplant", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Plane deine erste Mahlzeit für heute.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { open(LocalDate.now()) }) { Text("Mahlzeit planen") }
            }
        }
    } else {
        LazyColumn(state = state, modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { item -> when (item) { is PlanItem.Month -> "month-${item.yearMonth}"; is PlanItem.Day -> "day-${item.date}" } }) { item ->
                when (item) {
                    is PlanItem.Month -> Text(item.yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN)), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
                    is PlanItem.Day -> PlanDayRow(item.date, item.meals, edit, openGallery)
                }
            }
        }
    }
}

@Composable
private fun PlanDayRow(date: LocalDate, meals: List<Mahlzeit>, edit: (Mahlzeit, LocalDate) -> Unit, openGallery: (Mahlzeit, String) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.width(54.dp).padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.GERMAN), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = if (date == LocalDate.now()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            meals.forEach { meal ->
                meal.eintraege.firstOrNull { it.datum == date.toString() }?.let { occurrence ->
                    WeekAgendaMealCard(meal, occurrence, { edit(meal, date) }) { imageUrl -> openGallery(meal, imageUrl) }
                }
            }
        }
    }
}

@Composable
private fun OverviewScreen(meals: List<Mahlzeit>, edit: (Mahlzeit) -> Unit) {
    var selected by remember { mutableStateOf(setOf<Tag>()) }
    var query by rememberSaveable { mutableStateOf("") }
    var criterion by remember { mutableStateOf("Zuletzt gekocht") }
    var ascending by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var galleryMeal by remember { mutableStateOf<Mahlzeit?>(null) }
    val normalizedQuery = query.trim()
    val filtered = meals.filter { meal ->
        meal.name.contains(normalizedQuery, ignoreCase = true) && selected.all(meal::hatTag)
    }.sortedWith(compareBy<Mahlzeit> { when (criterion) { "Bewertung" -> it.durchschnitt() ?: -1.0; "Am häufigsten gekocht" -> it.termine.size; else -> it.letzterTermin() ?: "" } }.let { if (ascending) it else it.reversed() })
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Deine Mahlzeiten", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Finde schnell, worauf ihr Lust habt.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Mahlzeiten durchsuchen") },
            placeholder = { Text("Nach Namen suchen") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Outlined.Clear, contentDescription = "Suche löschen")
                    }
                }
            } else null,
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Tag.entries.forEach { tag -> FilterChip(tag in selected, { selected = selected.toggle(tag) }, { Text(tag.label) }) } }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box { AssistChip({ menu = true }, { Text(criterion) }); DropdownMenu(menu, { menu = false }) { listOf("Zuletzt gekocht", "Bewertung", "Am häufigsten gekocht").forEach { option -> DropdownMenuItem({ Text(option) }, { criterion = option; menu = false }) } } }
            AssistChip({ ascending = !ascending }, { Text(if (ascending) "Aufsteigend" else "Absteigend") })
        }
        Spacer(Modifier.height(14.dp))
        if (filtered.isEmpty()) Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(if (meals.isEmpty()) Icons.Outlined.CalendarMonth else Icons.Outlined.SearchOff, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Text(if (meals.isEmpty()) "Noch keine Mahlzeiten" else "Keine passenden Mahlzeiten", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(if (meals.isEmpty()) "Plane im Kalender euer erstes Essen." else "Passe deine Suche oder Filter an.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else LazyVerticalGrid(GridCells.Adaptive(164.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(filtered, key = { it.id }) { meal -> MealCard(meal, { edit(meal) }) { galleryMeal = meal } } }
    }
    galleryMeal?.let { meal -> MealImageGallery(meal) { galleryMeal = null } }
}

private fun Set<Tag>.toggle(item: Tag) = if (item in this) this - item else this + item

@Composable
private fun MealCard(meal: Mahlzeit, click: () -> Unit, openGallery: () -> Unit) = Card(Modifier.fillMaxWidth().clickable(onClick = click), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
    val lastCooked = meal.letzterTermin()?.let { date ->
        runCatching { LocalDate.parse(date).format(DateTimeFormatter.ofPattern("dd.MM.yy")) }.getOrNull()
    } ?: "–"
    Column {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(meal.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                DietMarker(meal, Modifier.padding(start = 8.dp))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                    Text(meal.durchschnitt()?.let { String.format(Locale.GERMANY, "%.1f / 10", it) } ?: "– / 10", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = "Zuletzt gekocht", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(lastCooked, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        meal.letztesBild()?.let { image ->
            AsyncImage(
                model = image,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clickable(onClick = openGallery),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun MealImageGallery(meal: Mahlzeit, initialImageUrl: String? = null, dismiss: () -> Unit) {
    val initialPage = meal.bilder.indexOfFirst { it.url == initialImageUrl }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { meal.bilder.size })
    Dialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val image = meal.bilder[page]
                Box(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    AsyncImage(
                        model = image.url,
                        contentDescription = "${meal.name}, Bild ${page + 1}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 16.dp),
                        color = Color.Black.copy(alpha = 0.72f),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text = "${formatImageDate(image.datum)} · ${page + 1} von ${meal.bilder.size}",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            IconButton(
                onClick = dismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp),
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "Galerie schließen", tint = Color.White)
            }
        }
    }
}

private fun formatImageDate(value: String): String = runCatching {
    LocalDate.parse(value).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
}.getOrDefault(value)

@Composable
private fun DietMarker(meal: Mahlzeit, modifier: Modifier = Modifier) {
    val tag = when {
        meal.hatTag(Tag.VEGAN) -> Tag.VEGAN
        meal.hatTag(Tag.VEGETARISCH) -> Tag.VEGETARISCH
        else -> return
    }
    val icon = if (tag == Tag.VEGAN) Icons.Outlined.Eco else Icons.Outlined.Spa
    val containerColor = if (tag == Tag.VEGAN) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val contentColor = if (tag == Tag.VEGAN) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(modifier = modifier, shape = CircleShape, color = containerColor) {
        Icon(
            imageVector = icon,
            contentDescription = tag.label,
            modifier = Modifier.padding(5.dp).size(18.dp),
            tint = contentColor,
        )
    }
}

@Composable
private fun RecipeLinkField(link: String, editingExistingMeal: Boolean, update: (String) -> Unit) {
    var editing by remember(editingExistingMeal) { mutableStateOf(!editingExistingMeal) }
    val uriHandler = LocalUriHandler.current
    if (editing) {
        OutlinedTextField(
            value = link,
            onValueChange = { value -> update(extractFirstWebUrl(value) ?: value) },
            label = { Text("Rezept-Link") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = if (editingExistingMeal) {
                {
                    IconButton(onClick = { editing = false }) {
                        Icon(Icons.Outlined.Check, contentDescription = "Link-Bearbeitung abschließen")
                    }
                }
            } else null,
        )
    } else {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraSmall,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = link.isNotBlank()) {
                            val url = link.trim().let { if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it" }
                            runCatching { uriHandler.openUri(url) }
                        }
                        .padding(start = 16.dp, top = 9.dp, bottom = 9.dp),
                ) {
                    Text("Rezept-Link", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = link.ifBlank { "Kein Rezept-Link" },
                        color = if (link.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        textDecoration = if (link.isBlank()) null else TextDecoration.Underline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { editing = true }) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Rezept-Link bearbeiten")
                }
            }
        }
    }
}

private data class PendingEntryImage(val upload: PendingImageUpload, val preview: Any)

@Composable
private fun EditScreen(vm: MealViewModel, date: LocalDate, mealId: String?, entryId: String?, firstRaterName: String, secondRaterName: String, done: () -> Unit) {
    val meals by vm.meals.collectAsState(); val selectedMeal = meals.find { it.id == mealId }; val existingEntry = selectedMeal?.eintraege?.find { it.id == entryId }; var chosen by remember { mutableStateOf<Mahlzeit?>(null) }
    var name by remember(selectedMeal) { mutableStateOf(selectedMeal?.name ?: "") }; var link by remember(selectedMeal) { mutableStateOf(selectedMeal?.rezeptLink ?: "") }
    var tags by remember(selectedMeal) { mutableStateOf(selectedMeal?.tags?.mapNotNull { runCatching { Tag.valueOf(it) }.getOrNull() }?.toSet()?.normalizedDietTags() ?: emptySet()) }
    val existingRating = existingEntry?.bewertung; var ratingOne by remember(existingEntry) { mutableStateOf(existingRating?.werte?.getOrNull(0)?.toString().orEmpty()) }; var ratingTwo by remember(existingEntry) { mutableStateOf(existingRating?.werte?.getOrNull(1)?.toString().orEmpty()) }
    var dateText by remember { mutableStateOf(date.toString()) }; var expanded by remember { mutableStateOf(false) }; var confirmDelete by remember { mutableStateOf(false) }; var pendingImage by remember { mutableStateOf<PendingEntryImage?>(null) }
    val context = LocalContext.current; val selectedDate = runCatching { LocalDate.parse(dateText) }.getOrNull()
    fun selectedImage(stream: () -> java.io.InputStream, filename: String, preview: Any) {
        val meal = selectedMeal
        val entry = existingEntry
        if (meal != null && entry != null) vm.addImage(meal.id, entry.id, filename, stream)
        else pendingImage = PendingEntryImage(PendingImageUpload(filename, stream), preview)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { selectedImage({ context.contentResolver.openInputStream(it)!! }, "galerie.jpg", it) } }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? -> bitmap?.let { b -> val bytes = ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray(); selectedImage({ ByteArrayInputStream(bytes) }, "kamera.jpg", b) } }
    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large) { Column(Modifier.padding(20.dp)) { Text(if (existingEntry == null) "Neues Essen" else "Eintrag bearbeiten", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(date.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN)), color = MaterialTheme.colorScheme.onPrimaryContainer) } } }
        item { FormSection("Mahlzeit") { OutlinedTextField(name, { value -> name = if ('\n' in value || '\r' in value) normalizePastedMealName(value) else value; expanded = name.isNotBlank() && existingEntry == null }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)); if (expanded) meals.filter { it.name.contains(name, true) }.take(5).forEach { meal -> Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { chosen = meal; name = meal.name; link = meal.rezeptLink.orEmpty(); tags = meal.tags.mapNotNull { runCatching { Tag.valueOf(it) }.getOrNull() }.toSet().normalizedDietTags(); expanded = false }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { meal.letztesBild()?.let { AsyncImage(it, null, Modifier.size(42.dp).clip(MaterialTheme.shapes.small)) }; Column(Modifier.padding(start = 10.dp)) { Text(meal.name, fontWeight = FontWeight.Bold); Text(meal.durchschnitt()?.let { String.format(Locale.GERMANY, "%.1f / 10", it) } ?: "Noch nicht bewertet", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }; RecipeLinkField(link, selectedMeal != null || chosen != null) { link = it } } }
        item { FormSection("Eigenschaften") { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Tag.entries.forEach { tag -> FilterChip(tag in tags, { tags = tags.toggleMealTag(tag) }, { Text(tag.label) }) } } } }
        item { FormSection("Bewertung") {
            Text("Wenn ihr das Essen bewertet, gebt beide Bewertungen ein.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            RaterScoreField(ratingOne, { ratingOne = it }, firstRaterName, FirstRaterColor)
            RaterScoreField(ratingTwo, { ratingTwo = it }, secondRaterName, SecondRaterColor)
        } }
        item { FormSection("Termin") { OutlinedTextField(dateText, { dateText = it }, label = { Text("Datum (JJJJ-MM-TT)") }, isError = selectedDate == null, modifier = Modifier.fillMaxWidth(), singleLine = true) } }
        item { FormSection("Bild") {
            pendingImage?.let { image ->
                AsyncImage(image.preview, "Ausgewähltes Bild", Modifier.fillMaxWidth().height(180.dp).clip(MaterialTheme.shapes.small), contentScale = ContentScale.Crop)
                Text("Das Bild wird beim Speichern hochgeladen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ gallery.launch("image/*") }, Modifier.weight(1f)) { Icon(Icons.Outlined.Image, null); Spacer(Modifier.width(6.dp)); Text("Galerie") }
                OutlinedButton({ camera.launch(null) }, Modifier.weight(1f)) { Icon(Icons.Outlined.PhotoCamera, null); Spacer(Modifier.width(6.dp)); Text("Kamera") }
            }
        } }
        item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { val ratingTexts = listOf(ratingOne, ratingTwo); val ratingsProvided = ratingTexts.any { it.isNotBlank() }; val validRatings = ratingTexts.map { it.replace(',', '.').toDoubleOrNull()?.takeIf { value -> value in 1.0..10.0 } }; val validDate = selectedDate ?: return@Button; if (name.isBlank() || (ratingsProvided && validRatings.any { it == null })) return@Button; val base = selectedMeal ?: chosen ?: Mahlzeit(name = name.trim()); val meal = base.copy(name = name.trim(), rezeptLink = link.trim().ifBlank { null }, tags = tags.normalizedDietTags().map { it.name }); val rating = if (ratingsProvided) MahlzeitBewertung(validRatings.filterNotNull()) else null; val occurrence = existingEntry?.copy(datum = validDate.toString(), bewertung = rating) ?: MahlzeitEintrag(datum = validDate.toString(), bewertung = rating); when { existingEntry != null -> vm.updateEntry(meal, occurrence); selectedMeal != null || chosen != null -> vm.createEntry(meal, occurrence, pendingImage?.upload); else -> vm.createMeal(meal, occurrence, pendingImage?.upload) }; done() }, modifier = Modifier.fillMaxWidth()) { Text("Speichern") }
        } }
        if (existingEntry != null) item { Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) { TextButton({ confirmDelete = true }, Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Outlined.DeleteOutline, null); Spacer(Modifier.width(4.dp)); Text("Eintrag löschen") } } }
    }
    if (confirmDelete && selectedMeal != null && existingEntry != null) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Eintrag löschen?") }, text = { Text("Nur dieser Termin und seine Bilder werden dauerhaft gelöscht. Beim letzten Eintrag wird auch die Mahlzeit entfernt.") }, confirmButton = { TextButton({ vm.deleteEntry(selectedMeal.id, existingEntry.id); done() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Löschen") } }, dismissButton = { TextButton({ confirmDelete = false }) { Text("Abbrechen") } })
}

@Composable
private fun RaterScoreField(value: String, update: (String) -> Unit, name: String, accent: Color) {
    OutlinedTextField(
        value = value,
        onValueChange = update,
        label = { Text("$name (1,00–10,00)") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accent,
            unfocusedBorderColor = accent.copy(alpha = 0.7f),
            focusedLabelColor = accent,
            unfocusedLabelColor = accent,
            cursorColor = accent,
        ),
    )
}

@Composable
private fun FormSection(title: String, content: @Composable ColumnScope.() -> Unit) = Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary); content() } }
