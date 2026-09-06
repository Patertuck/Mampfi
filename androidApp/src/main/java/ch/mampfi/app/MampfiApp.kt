@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package ch.mampfi.app

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Spa
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
                edit = { meal, date -> nav.navigate("bearbeiten/$date?meal=${meal.id}") },
            ) }
            composable("uebersicht") { OverviewScreen(vm.meals.collectAsState().value) { meal -> nav.navigate("bearbeiten/${meal.letzterTermin() ?: LocalDate.now()}?meal=${meal.id}") } }
            composable("einstellungen") { EndpointSetupScreen(endpointStore, configured = true) { nav.popBackStack() } }
            composable("bearbeiten/{date}?meal={meal}") { entry -> EditScreen(vm, LocalDate.parse(entry.arguments!!.getString("date")!!), entry.arguments?.getString("meal")) { nav.popBackStack() } }
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
    val weekState = rememberWeekCalendarState(
        startDate = currentMonth.minusMonths(24).atDay(1),
        endDate = currentMonth.plusMonths(24).atEndOfMonth(),
        firstVisibleWeekDate = LocalDate.now(),
        firstDayOfWeek = java.time.DayOfWeek.MONDAY,
    )
    var calendarView by rememberSaveable { mutableStateOf("WEEK") }
    var selectedWeekDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var receivedInitialWeek by remember { mutableStateOf(false) }
    var planInitialised by rememberSaveable { mutableStateOf(false) }
    val planListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
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
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
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
                weekHeader = { week -> CalendarWeekHeader(week.days.first().date, week.days.last().date) },
                dayContent = { day -> WeekCalendarCell(day.date, meals.count { day.date.toString() in it.termine }, day.date == selectedDate) { selectedWeekDate = day.date.toString() } },
            )
            Spacer(Modifier.height(12.dp))
            WeekAgenda(
                date = selectedDate,
                meals = meals.filter { selectedDate.toString() in it.termine },
                plan = { open(selectedDate) },
                edit = { meal -> edit(meal, selectedDate) },
                modifier = Modifier.weight(1f),
            )
        } else {
            PlanSchedule(planItems, planListState, open, edit, Modifier.weight(1f))
        }
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
private fun WeekAgenda(date: LocalDate, meals: List<Mahlzeit>, plan: () -> Unit, edit: (Mahlzeit) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text(date.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(if (meals.isEmpty()) "Noch nichts geplant" else "${meals.size} ${if (meals.size == 1) "Mahlzeit" else "Mahlzeiten"} geplant", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            if (meals.isEmpty()) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text("Zeit für etwas Leckeres.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Plane eine Mahlzeit für diesen Tag.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(meals, key = { it.id }) { meal -> WeekAgendaMealCard(meal) { edit(meal) } }
                }
            }
            Button(onClick = plan, modifier = Modifier.fillMaxWidth()) { Text("Mahlzeit planen") }
        }
    }
}

@Composable
private fun WeekAgendaMealCard(meal: Mahlzeit, click: () -> Unit) = Card(
    modifier = Modifier.fillMaxWidth().clickable(onClick = click),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
) {
    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        meal.letztesBild()?.let { AsyncImage(it, null, Modifier.size(64.dp).clip(MaterialTheme.shapes.small)) }
        Column(Modifier.padding(start = if (meal.letztesBild() == null) 0.dp else 12.dp).weight(1f)) {
            Text(meal.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            meal.tags.takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" · ") { tag -> Tag.entries.find { it.name == tag }?.label ?: tag }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        DietMarker(meal, Modifier.padding(end = 8.dp))
        meal.durchschnitt()?.let { Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) { Text(String.format(Locale.GERMANY, "%.1f", it), Modifier.padding(horizontal = 8.dp, vertical = 5.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer) } }
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
private fun PlanSchedule(items: List<PlanItem>, state: androidx.compose.foundation.lazy.LazyListState, open: (LocalDate) -> Unit, edit: (Mahlzeit, LocalDate) -> Unit, modifier: Modifier = Modifier) {
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
                    is PlanItem.Day -> PlanDayRow(item.date, item.meals, edit)
                }
            }
        }
    }
}

@Composable
private fun PlanDayRow(date: LocalDate, meals: List<Mahlzeit>, edit: (Mahlzeit, LocalDate) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.width(54.dp).padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.GERMAN), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = if (date == LocalDate.now()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            meals.forEach { meal -> WeekAgendaMealCard(meal) { edit(meal, date) } }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(meal.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                DietMarker(meal, Modifier.padding(start = 8.dp))
            }
            Spacer(Modifier.height(8.dp))
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) { Text(meal.durchschnitt()?.let { String.format(Locale.GERMANY, "%.1f / 10", it) } ?: "Noch nicht bewertet", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer) }
        }
    }
}

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
