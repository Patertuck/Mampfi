@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package ch.mampfi.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.LocalDate
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
    val topLevelRoutes = remember { setOf("kalender", "ideen", "uebersicht", "einstellungen") }
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
            if (currentRoute in topLevelRoutes) NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
                listOf(
                    Triple("kalender", "Kalender", Icons.Outlined.CalendarMonth),
                    Triple("ideen", "Ideen", Icons.Outlined.Lightbulb),
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
                awayEntries = vm.awayEntries.collectAsState().value,
                open = { nav.navigate("bearbeiten/$it") },
                recommend = { nav.navigate("empfehlung/$it") },
                edit = { meal, date -> meal.eintraege.firstOrNull { it.datum == date.toString() }?.let { occurrence -> nav.navigate("bearbeiten/$date?meal=${meal.id}&entry=${occurrence.id}") } },
                createAway = vm::createAwayEntry,
                updateAway = vm::updateAwayEntry,
                deleteAway = vm::deleteAwayEntry,
            ) }
            composable("ideen") { IdeasScreen(
                ideas = vm.meals.collectAsState().value.filter { it.istIdee },
                add = { nav.navigate("idee") },
                edit = { nav.navigate("idee?meal=${it.id}") },
            ) }
            composable("uebersicht") { OverviewScreen(
                meals = vm.meals.collectAsState().value.filter { it.eintraege.isNotEmpty() },
                open = { meal -> nav.navigate("mahlzeit/${meal.id}") },
                setIdea = { meal, isIdea -> vm.setIdea(meal, isIdea) },
            ) }
            composable("einstellungen") { EndpointSetupScreen(endpointStore, configured = true) { nav.popBackStack() } }
            composable("idee?meal={meal}") { entry -> IdeaEditScreen(
                vm = vm,
                mealId = entry.arguments?.getString("meal"),
                schedule = { mealId -> nav.navigate("bearbeiten/${LocalDate.now()}?meal=$mealId") { popUpTo("ideen") } },
                done = { nav.popBackStack() },
            ) }
            composable("mahlzeit/{meal}") { entry -> MealDetailScreen(
                vm = vm,
                mealId = entry.arguments?.getString("meal")!!,
                firstRaterName = appSettings.firstRaterName,
                secondRaterName = appSettings.secondRaterName,
                back = { nav.popBackStack() },
                edit = { nav.navigate("mahlzeit-bearbeiten/$it") },
                schedule = { nav.navigate("bearbeiten/${LocalDate.now()}?meal=$it") },
            ) }
            composable("mahlzeit-bearbeiten/{meal}") { entry -> SharedMealEditScreen(
                vm = vm,
                mealId = entry.arguments?.getString("meal")!!,
                done = { nav.popBackStack() },
            ) }
            composable("empfehlung/{date}") { entry ->
                val date = LocalDate.parse(entry.arguments!!.getString("date")!!)
                RecommendationScreen(
                    meals = vm.meals.collectAsState().value,
                    targetDate = date,
                    back = { nav.popBackStack() },
                    schedule = { mealId ->
                        nav.popBackStack()
                        nav.navigate("bearbeiten/$date?meal=$mealId")
                    },
                )
            }
            composable("bearbeiten/{date}?meal={meal}&entry={entry}") { entry -> EditScreen(vm, LocalDate.parse(entry.arguments!!.getString("date")!!), entry.arguments?.getString("meal"), entry.arguments?.getString("entry"), appSettings.firstRaterName, appSettings.secondRaterName, vm.awayEntries.collectAsState().value) { nav.popBackStack() } }
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
