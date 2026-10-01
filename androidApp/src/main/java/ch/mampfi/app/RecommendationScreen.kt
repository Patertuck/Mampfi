@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package ch.mampfi.app

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import ch.mampfi.app.data.Mahlzeit
import ch.mampfi.app.data.Tag
import coil3.compose.AsyncImage
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.launch

@Composable
internal fun RecommendationScreen(
    meals: List<Mahlzeit>,
    targetDate: LocalDate,
    back: () -> Unit,
    schedule: (String) -> Unit,
) {
    var filters by remember { mutableStateOf(RecommendationFilters()) }
    var filterSheetVisible by remember { mutableStateOf(false) }
    var skippedIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var shuffleSeed by rememberSaveable { mutableIntStateOf(Random.nextInt()) }
    val today = LocalDate.now()
    val deck = remember(meals, targetDate, today, filters, shuffleSeed) {
        recommendationDeck(meals, targetDate, today, filters, Random(shuffleSeed))
    }
    val candidate = deck.firstOrNull { it.meal.id !in skippedIds }
    val scope = rememberCoroutineScope()
    val screenWidthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    var cardOffset by remember(candidate?.meal?.id) { mutableFloatStateOf(0f) }
    var deciding by remember { mutableStateOf(false) }

    fun decide(accepted: Boolean) {
        val current = candidate ?: return
        if (deciding) return
        deciding = true
        scope.launch {
            animate(cardOffset, if (accepted) screenWidthPx * 1.25f else -screenWidthPx * 1.25f) { value, _ -> cardOffset = value }
            if (accepted) schedule(current.meal.id) else skippedIds = skippedIds + current.meal.id
            cardOffset = 0f
            deciding = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Was kochen wir?", fontWeight = FontWeight.Bold)
                        Text(
                            targetDate.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = { IconButton(back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück") } },
                actions = {
                    BadgedBox(badge = { if (filters.activeCount > 0) Badge { Text(filters.activeCount.toString()) } }) {
                        IconButton({ filterSheetVisible = true }) { Icon(Icons.Outlined.FilterAlt, "Empfehlungen filtern") }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (candidate == null) {
                RecommendationDeckEmpty(
                    hasCandidates = deck.isNotEmpty(),
                    hasFilters = filters.activeCount > 0,
                    restart = { skippedIds = emptyList(); shuffleSeed = Random.nextInt() },
                    clearFilters = { filters = RecommendationFilters(); skippedIds = emptyList() },
                    close = back,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(
                    "${skippedIds.size + 1} von ${deck.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                RecommendationCard(
                    candidate = candidate,
                    cardOffset = cardOffset,
                    onDrag = { cardOffset += it },
                    onDragEnd = {
                        when {
                            cardOffset <= -screenWidthPx * 0.22f -> decide(false)
                            cardOffset >= screenWidthPx * 0.22f -> decide(true)
                            else -> cardOffset = 0f
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledIconButton(
                        onClick = { decide(false) },
                        enabled = !deciding,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                        modifier = Modifier.size(60.dp),
                    ) { Icon(Icons.Outlined.Close, "Nicht heute", Modifier.size(30.dp)) }
                    IconButton(
                        onClick = { if (skippedIds.isNotEmpty()) skippedIds = skippedIds.dropLast(1) },
                        enabled = skippedIds.isNotEmpty() && !deciding,
                        modifier = Modifier.size(48.dp),
                    ) { Icon(Icons.AutoMirrored.Outlined.Undo, "Letzte Auswahl rückgängig") }
                    FilledIconButton(
                        onClick = { decide(true) },
                        enabled = !deciding,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                        modifier = Modifier.size(60.dp),
                    ) { Icon(Icons.Outlined.Check, "Mahlzeit auswählen", Modifier.size(30.dp)) }
                }
            }
        }
    }

    if (filterSheetVisible) RecommendationFilterSheet(
        filters = filters,
        update = { filters = it; filterSheetVisible = false },
        dismiss = { filterSheetVisible = false },
    )
}

@Composable
private fun RecommendationCard(
    candidate: MealRecommendation,
    cardOffset: Float,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) = Card(
    modifier = modifier
        .fillMaxWidth()
        .offset { IntOffset(cardOffset.roundToInt(), 0) }
        .graphicsLayer { rotationZ = cardOffset / 45f },
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().weight(1.15f)) {
            if (candidate.photos.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondaryContainer),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Outlined.RestaurantMenu, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.height(12.dp))
                    Text(candidate.meal.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            } else {
                key(candidate.meal.id) {
                    val pagerState = rememberPagerState(pageCount = { candidate.photos.size })
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        AsyncImage(candidate.photos[page].url, "${candidate.meal.name}, Bild ${page + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    Surface(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                        color = Color.Black.copy(alpha = 0.68f),
                        contentColor = Color.White,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            "${pagerState.currentPage + 1} / ${candidate.photos.size} · ${formatImageDate(candidate.photos[pagerState.currentPage].datum)}",
                            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
            if (abs(cardOffset) > 20f) Surface(
                modifier = Modifier.align(if (cardOffset > 0) Alignment.TopStart else Alignment.TopEnd).padding(18.dp),
                color = if (cardOffset > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(if (cardOffset > 0) "JA" else "WEITER", Modifier.padding(horizontal = 14.dp, vertical = 8.dp), color = Color.White, fontWeight = FontWeight.Black)
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .weight(0.85f)
                .pointerInput(candidate.meal.id) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, amount -> change.consume(); onDrag(amount) },
                        onDragCancel = onDragEnd,
                        onDragEnd = onDragEnd,
                    )
                }
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(candidate.meal.name, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                DietMarker(candidate.meal, Modifier.padding(start = 8.dp))
            }
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                Text(candidate.reason, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.labelLarge)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                RecommendationFact(Icons.Outlined.StarOutline, candidate.averageRating?.let { String.format(Locale.GERMANY, "%.1f / 10", it) } ?: "Unbewertet")
                RecommendationFact(Icons.Outlined.History, candidate.lastCooked.format(DateTimeFormatter.ofPattern("dd.MM.yy")))
                RecommendationFact(Icons.Outlined.Restaurant, "${candidate.cookCount}×")
            }
            val tags = candidate.meal.tags.mapNotNull { name -> Tag.entries.find { it.name == name }?.label }.joinToString(" · ")
            if (tags.isNotEmpty()) Text(tags, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RecommendationFact(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(icon, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.width(4.dp))
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun RecommendationDeckEmpty(
    hasCandidates: Boolean,
    hasFilters: Boolean,
    restart: () -> Unit,
    clearFilters: () -> Unit,
    close: () -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
    Icon(Icons.Outlined.RestaurantMenu, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(16.dp))
    Text(if (hasCandidates) "Alles durchgesehen" else "Keine passenden Mahlzeiten", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(if (hasCandidates) "Noch nichts dabei? Mischt die Vorschläge neu." else "Ändert die Filter oder fügt Kochhistorie hinzu.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(20.dp))
    if (hasCandidates) Button(restart) { Icon(Icons.Outlined.Shuffle, null); Spacer(Modifier.width(6.dp)); Text("Neu mischen") }
    if (!hasCandidates && hasFilters) OutlinedButton(clearFilters) { Text("Filter zurücksetzen") }
    TextButton(close) { Text("Zurück zum Kalender") }
}

@Composable
private fun RecommendationFilterSheet(filters: RecommendationFilters, update: (RecommendationFilters) -> Unit, dismiss: () -> Unit) {
    var editing by remember(filters) { mutableStateOf(filters) }
    ModalBottomSheet(onDismissRequest = dismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Empfehlungen filtern", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            FilterToggle("Vegetarisch", editing.vegetarian) { editing = editing.copy(vegetarian = it, vegan = if (it) false else editing.vegan) }
            FilterToggle("Vegan", editing.vegan) { editing = editing.copy(vegan = it, vegetarian = if (it) false else editing.vegetarian) }
            FilterToggle("Dessert", editing.dessert) { editing = editing.copy(dessert = it) }
            FilterToggle("Nicht aufwändig", editing.notElaborate) { editing = editing.copy(notElaborate = it) }
            Button({ update(editing) }, Modifier.fillMaxWidth()) { Text("Anwenden") }
            TextButton({ update(RecommendationFilters()) }, Modifier.fillMaxWidth()) { Text("Alle Filter löschen") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FilterToggle(label: String, checked: Boolean, update: (Boolean) -> Unit) = Row(
    Modifier.fillMaxWidth().padding(vertical = 2.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(label, Modifier.weight(1f))
    Switch(checked, update)
}
