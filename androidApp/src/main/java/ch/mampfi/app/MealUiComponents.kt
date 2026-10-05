@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package ch.mampfi.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ch.mampfi.app.data.*
import coil3.compose.AsyncImage
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
internal fun MealImageGallery(meal: Mahlzeit, initialImageUrl: String? = null, dismiss: () -> Unit) {
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

internal fun formatImageDate(value: String): String = runCatching {
    LocalDate.parse(value).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
}.getOrDefault(value)

@Composable
internal fun DietMarker(meal: Mahlzeit, modifier: Modifier = Modifier) {
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
internal fun MealThumbnail(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    openGallery: (() -> Unit)? = null,
) {
    Box(
        modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(enabled = imageUrl != null && openGallery != null) { openGallery?.invoke() },
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl != null) {
            AsyncImage(imageUrl, contentDescription, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Image(
                painter = painterResource(R.drawable.mampfi_splash_mascot),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(0.72f),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
internal fun MampfiEmptyMascot(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.mampfi_splash_mascot),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}

@Composable
internal fun RecipeLinkField(link: String, editingExistingMeal: Boolean, update: (String) -> Unit) {
    var editing by remember(editingExistingMeal) { mutableStateOf(!editingExistingMeal) }
    val uriHandler = LocalUriHandler.current
    val webUrl = normalizedWebUrlOrNull(link)
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
                        .clickable(enabled = webUrl != null) {
                            webUrl?.let { runCatching { uriHandler.openUri(it) } }
                        }
                        .padding(start = 16.dp, top = 9.dp, bottom = 9.dp),
                ) {
                    Text("Rezept-Link", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = link.ifBlank { "Kein Rezept-Link" },
                        color = if (webUrl == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        textDecoration = if (webUrl == null) null else TextDecoration.Underline,
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


@Composable
internal fun FormSection(title: String, trailing: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) = Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            trailing?.invoke()
        }
        content()
    }
}

@Composable
internal fun ConfirmDiscardChangesDialog(
    visible: Boolean,
    keepEditing: () -> Unit,
    discard: () -> Unit,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = keepEditing,
        title = { Text("Änderungen verwerfen?") },
        text = { Text("Eure nicht gespeicherten Änderungen gehen verloren.") },
        confirmButton = {
            TextButton(
                onClick = discard,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Verwerfen") }
        },
        dismissButton = { TextButton(onClick = keepEditing) { Text("Weiter bearbeiten") } },
    )
}

@Composable
internal fun EditorBottomBar(content: @Composable RowScope.() -> Unit) {
    Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
internal fun SaveButtonContent(saving: Boolean, label: String = "Speichern") {
    if (saving) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
    }
    Text(if (saving) "Wird gespeichert …" else label)
}

@Composable
internal fun MampfiFilterDialog(
    title: String,
    dismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = MaterialTheme.shapes.large,
                shadowElevation = 12.dp,
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        IconButton(onClick = dismiss) { Icon(Icons.Outlined.Close, contentDescription = "Schließen") }
                    }
                    content()
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
                }
            }
        }
    }
}
