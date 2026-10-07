@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package ch.mampfi.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate

@Composable
fun MampfiApp(vm: MealViewModel, connectedViaTailscale: Boolean = false, endpointStore: EndpointSettingsStore, themeMode: ThemeMode = ThemeMode.DARK, updateVm: UpdateViewModel = viewModel()) = MampfiTheme(themeMode) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val updateState by updateVm.state.collectAsState()
    var dismissedUpdateVersion by remember { mutableStateOf<String?>(null) }
    val currentRoute = nav.currentBackStackEntryAsState().value?.destination?.route
    val topLevelRoutes = remember { setOf("kalender", "ideen", "uebersicht", "einstellungen") }
    val appSettings by endpointStore.settings.collectAsState(initial = EndpointSettings())
    val syncStatus by vm.syncStatus.collectAsState()
    DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> { vm.startForegroundRefresh(); updateVm.onResume() }
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
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                UpdateBanner(
                    state = updateState,
                    dismissedVersion = dismissedUpdateVersion,
                    dismiss = { dismissedUpdateVersion = it },
                    download = updateVm::download,
                    install = updateVm::install,
                )
                SyncStatusBar(syncStatus, vm::refresh)
            }
        },
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
                isRefreshing = syncStatus.isRefreshing,
                refresh = vm::refresh,
            ) }
            composable("ideen") { IdeasScreen(
                ideas = vm.meals.collectAsState().value.filter { it.istIdee },
                add = { nav.navigate("idee") },
                edit = { nav.navigate("idee?meal=${it.id}") },
                isRefreshing = syncStatus.isRefreshing,
                refresh = vm::refresh,
            ) }
            composable("uebersicht") { OverviewScreen(
                meals = vm.meals.collectAsState().value.filter { it.eintraege.isNotEmpty() },
                open = { meal -> nav.navigate("mahlzeit/${meal.id}") },
                setIdea = { meal, isIdea -> vm.setIdea(meal, isIdea) },
                isRefreshing = syncStatus.isRefreshing,
                refresh = vm::refresh,
            ) }
            composable("einstellungen") { EndpointSetupScreen(
                store = endpointStore,
                configured = true,
                updateState = updateState,
                checkForUpdate = updateVm::checkForUpdate,
                downloadUpdate = updateVm::download,
                installUpdate = updateVm::install,
            ) { nav.popBackStack() } }
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
}

@Composable
private fun UpdateBanner(state: UpdateState, dismissedVersion: String?, dismiss: (String) -> Unit, download: () -> Unit, install: () -> Unit) {
    val update = when (state) {
        is UpdateState.Available -> state.update
        is UpdateState.Downloading -> state.update
        is UpdateState.Ready -> state.update
        is UpdateState.Failed -> state.update
        else -> null
    } ?: return
    if (dismissedVersion == update.version && state !is UpdateState.Downloading && state !is UpdateState.Ready) return
    Surface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(Icons.Outlined.SystemUpdate, contentDescription = null)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(
                        when (state) {
                            is UpdateState.Available -> "Mampfi ${update.version} ist verfügbar"
                            is UpdateState.Downloading -> "Update wird heruntergeladen"
                            is UpdateState.Ready -> "Update ist bereit"
                            is UpdateState.Failed -> state.message
                            else -> ""
                        },
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    )
                    if (update.changelog.isNotBlank() && state is UpdateState.Available) Text(update.changelog, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
                when (state) {
                    is UpdateState.Available, is UpdateState.Failed -> IconButton(download) { Icon(Icons.Outlined.Download, "Herunterladen") }
                    is UpdateState.Ready -> TextButton(install) { Text("Installieren") }
                    else -> Unit
                }
                if (state is UpdateState.Available || state is UpdateState.Failed) IconButton({ dismiss(update.version) }) { Icon(Icons.Outlined.Close, "Später") }
            }
            if (state is UpdateState.Downloading) {
                if (state.progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                else LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth())
                state.progress?.let { Text("$it %", style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

@Composable
private fun SyncStatusBar(status: SyncStatus, retry: () -> Unit) {
    Column {
        if (status.isOffline) Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.CloudOff, contentDescription = null, modifier = Modifier.size(20.dp))
                Text("Offline · Lokale Daten", Modifier.weight(1f).padding(horizontal = 10.dp), style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = retry, enabled = !status.isRefreshing) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Erneut versuchen")
                }
            }
        }
        if (status.isRefreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}
