@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package ch.mampfi.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ch.mampfi.app.data.*

internal data class ManualRecipeValidation(
    val servingsError: String? = null,
    val ingredientsError: String? = null,
    val stepsError: String? = null,
    val invalidAmountIds: Set<String> = emptySet(),
    val invalidCustomUnitIds: Set<String> = emptySet(),
) {
    val isValid get() = servingsError == null && ingredientsError == null && stepsError == null &&
        invalidAmountIds.isEmpty() && invalidCustomUnitIds.isEmpty()
}

internal fun validateManualRecipe(
    servings: String,
    ingredients: List<RecipeIngredient>,
    steps: List<RecipeStep>,
): ManualRecipeValidation {
    val namedIngredients = ingredients.filter { it.name.isNotBlank() }
    return ManualRecipeValidation(
        servingsError = if (servings.toIntOrNull()?.let { it > 0 } == true) null else "Bitte gib eine positive Personenzahl ein.",
        ingredientsError = if (namedIngredients.isNotEmpty()) null else "Füge mindestens eine Zutat hinzu.",
        stepsError = if (steps.any { it.text.isNotBlank() }) null else "Füge mindestens einen Zubereitungsschritt hinzu.",
        invalidAmountIds = namedIngredients.filter { ingredient ->
            val amount = ingredient.amount?.trim().orEmpty()
            amount.isNotEmpty() && amount.replace(',', '.').toDoubleOrNull()?.let { it > 0 } != true
        }.mapTo(mutableSetOf()) { it.id },
        invalidCustomUnitIds = namedIngredients.filter {
            it.unit == GroceryUnit.CUSTOM && it.customUnit.isNullOrBlank()
        }.mapTo(mutableSetOf()) { it.id },
    )
}

internal fun normalizedManualRecipe(
    meal: Mahlzeit,
    servings: String,
    ingredients: List<RecipeIngredient>,
    steps: List<RecipeStep>,
) = meal.copy(
    rezeptLink = null,
    recipeServings = servings.toInt(),
    ingredients = ingredients.mapNotNull { ingredient ->
        ingredient.copy(
            name = ingredient.name.trim(),
            amount = ingredient.amount?.trim()?.replace(',', '.')?.ifBlank { null },
            customUnit = ingredient.customUnit?.trim()?.ifBlank { null },
        ).takeIf { it.name.isNotBlank() }
    },
    recipeSteps = steps.mapNotNull { step -> step.copy(text = step.text.trim()).takeIf { it.text.isNotBlank() } },
)

@Composable
internal fun RecipeChoiceEditor(
    link: String,
    editingExistingMeal: Boolean,
    recipeServings: Int?,
    ingredients: List<RecipeIngredient>,
    steps: List<RecipeStep>,
    updateLink: (String) -> Unit,
    editManualRecipe: () -> Unit,
    clearManualRecipe: () -> Unit,
) {
    val hasManualRecipe = link.isBlank() && (recipeServings != null || ingredients.isNotEmpty() || steps.isNotEmpty())
    var pendingSwitch by remember { mutableStateOf<RecipeSwitch?>(null) }
    if (hasManualRecipe) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
            shape = MaterialTheme.shapes.small,
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Outlined.MenuBook, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text("Manuelles Rezept", fontWeight = FontWeight.Bold)
                        Text(
                            listOfNotNull(
                                recipeServings?.let { "für $it Personen" },
                                "${ingredients.count { it.name.isNotBlank() }} Zutaten",
                                "${steps.count { it.text.isNotBlank() }} Schritte",
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(editManualRecipe) { Icon(Icons.Outlined.Edit, "Manuelles Rezept bearbeiten") }
                }
                TextButton({ pendingSwitch = RecipeSwitch.TO_LINK }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Link, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Stattdessen Rezept-Link verwenden")
                }
            }
        }
    } else {
        RecipeLinkField(link, editingExistingMeal) { updateLink(it) }
        OutlinedButton(
            onClick = { if (link.isBlank()) editManualRecipe() else pendingSwitch = RecipeSwitch.TO_MANUAL },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.AutoMirrored.Outlined.MenuBook, null)
            Spacer(Modifier.width(6.dp))
            Text("Manuelles Rezept erstellen")
        }
    }
    pendingSwitch?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingSwitch = null },
            title = { Text("Rezept ersetzen?") },
            text = {
                Text(
                    if (target == RecipeSwitch.TO_MANUAL)
                        "Der Rezept-Link und bereits importierte Zutaten werden entfernt."
                    else "Das manuelle Rezept mit Zutaten und Schritten wird entfernt.",
                )
            },
            confirmButton = {
                TextButton({
                    pendingSwitch = null
                    if (target == RecipeSwitch.TO_MANUAL) {
                        updateLink("")
                        clearManualRecipe()
                        editManualRecipe()
                    } else {
                        clearManualRecipe()
                        updateLink("")
                    }
                }) { Text("Ersetzen") }
            },
            dismissButton = { TextButton({ pendingSwitch = null }) { Text("Abbrechen") } },
        )
    }
}

private enum class RecipeSwitch { TO_LINK, TO_MANUAL }

@Composable
internal fun ManualRecipeEditorDialog(meal: Mahlzeit, dismiss: () -> Unit, save: (Mahlzeit) -> Unit) {
    var servings by remember(meal.id, meal.recipeServings) { mutableStateOf(meal.recipeServings?.toString().orEmpty()) }
    var ingredients by remember(meal.id, meal.ingredients) { mutableStateOf(meal.ingredients) }
    var steps by remember(meal.id, meal.recipeSteps) { mutableStateOf(meal.recipeSteps) }
    var validationRequested by remember { mutableStateOf(false) }
    val validation = validateManualRecipe(servings, ingredients, steps)
    MampfiDialog(
        title = "Manuelles Rezept",
        subtitle = meal.name.takeIf { it.isNotBlank() },
        icon = Icons.AutoMirrored.Outlined.MenuBook,
        dismiss = dismiss,
        expanded = true,
        content = {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    OutlinedTextField(
                        servings,
                        { servings = it.filter(Char::isDigit) },
                        label = { Text("Personen") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = validationRequested && validation.servingsError != null,
                        supportingText = if (validationRequested && validation.servingsError != null) {{ Text(validation.servingsError!!) }} else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                item {
                    Text("Zutaten", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    if (validationRequested && validation.ingredientsError != null) Text(validation.ingredientsError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                itemsIndexed(ingredients, key = { _, ingredient -> ingredient.id }) { _, ingredient ->
                    RecipeIngredientEditRow(
                        ingredient = ingredient,
                        change = { changed -> ingredients = ingredients.map { if (it.id == changed.id) changed else it } },
                        delete = { ingredients = ingredients.filterNot { it.id == ingredient.id } },
                        amountError = validationRequested && ingredient.id in validation.invalidAmountIds,
                        customUnitError = validationRequested && ingredient.id in validation.invalidCustomUnitIds,
                    )
                }
                item {
                    TextButton({ ingredients = ingredients + RecipeIngredient(name = "") }) {
                        Icon(Icons.Outlined.Add, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Zutat hinzufügen")
                    }
                }
                item {
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text("Zubereitung", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    if (validationRequested && validation.stepsError != null) Text(validation.stepsError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                itemsIndexed(steps, key = { _, step -> step.id }) { index, step ->
                    RecipeStepEditRow(
                        index = index,
                        step = step,
                        canMoveUp = index > 0,
                        canMoveDown = index < steps.lastIndex,
                        change = { changed -> steps = steps.map { if (it.id == changed.id) changed else it } },
                        moveUp = { steps = steps.toMutableList().apply { add(index - 1, removeAt(index)) } },
                        moveDown = { steps = steps.toMutableList().apply { add(index + 1, removeAt(index)) } },
                        delete = { steps = steps.filterNot { it.id == step.id } },
                    )
                }
                item {
                    TextButton({ steps = steps + RecipeStep(text = "") }) {
                        Icon(Icons.Outlined.Add, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Schritt hinzufügen")
                    }
                }
            }
        },
        actions = {
            TextButton(dismiss, Modifier.weight(1f)) { Text("Abbrechen") }
            Button({
                validationRequested = true
                if (validation.isValid) save(normalizedManualRecipe(meal, servings, ingredients, steps))
            }, Modifier.weight(1f)) {
                Icon(Icons.Outlined.Save, null)
                Spacer(Modifier.width(6.dp))
                Text("Speichern")
            }
        },
    )
}

@Composable
internal fun RecipeIngredientEditRow(
    ingredient: RecipeIngredient,
    change: (RecipeIngredient) -> Unit,
    delete: () -> Unit,
    amountError: Boolean = false,
    customUnitError: Boolean = false,
) {
    var unitMenu by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), shape = MaterialTheme.shapes.small) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    ingredient.amount.orEmpty(),
                    { change(ingredient.copy(amount = it.ifBlank { null })) },
                    label = { Text("Menge") },
                    modifier = Modifier.width(92.dp),
                    singleLine = true,
                    isError = amountError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    ingredient.name,
                    { change(ingredient.copy(name = it)) },
                    label = { Text("Zutat") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                IconButton(delete) { Icon(Icons.Outlined.Delete, "Zutat löschen") }
            }
            if (amountError) Text("Bitte eine positive Zahl eingeben.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(unitMenu, { unitMenu = it }, Modifier.weight(1f)) {
                    OutlinedTextField(
                        ingredient.unitLabel(), {}, readOnly = true, label = { Text("Einheit") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(unitMenu) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(unitMenu, { unitMenu = false }) { GroceryUnit.entries.forEach { unit ->
                        DropdownMenuItem({ Text(unit.label) }, { change(ingredient.copy(unit = unit, customUnit = null)); unitMenu = false })
                    } }
                }
                ExposedDropdownMenuBox(categoryMenu, { categoryMenu = it }, Modifier.weight(1f)) {
                    OutlinedTextField(
                        ingredient.category.label, {}, readOnly = true, label = { Text("Kategorie") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryMenu) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(categoryMenu, { categoryMenu = false }) { GroceryCategory.entries.forEach { category ->
                        DropdownMenuItem({ Text(category.label) }, { change(ingredient.copy(category = category)); categoryMenu = false })
                    } }
                }
            }
            if (ingredient.unit == GroceryUnit.CUSTOM) OutlinedTextField(
                ingredient.customUnit.orEmpty(),
                { change(ingredient.copy(customUnit = it.ifBlank { null })) },
                label = { Text("Eigene Einheit") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = customUnitError,
                supportingText = if (customUnitError) {{ Text("Bitte gib die Einheit an.") }} else null,
            )
        }
    }
}

@Composable
private fun RecipeStepEditRow(
    index: Int,
    step: RecipeStep,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    change: (RecipeStep) -> Unit,
    moveUp: () -> Unit,
    moveDown: () -> Unit,
    delete: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), shape = MaterialTheme.shapes.small) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Schritt ${index + 1}", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                IconButton(moveUp, enabled = canMoveUp) { Icon(Icons.Outlined.KeyboardArrowUp, "Schritt nach oben") }
                IconButton(moveDown, enabled = canMoveDown) { Icon(Icons.Outlined.KeyboardArrowDown, "Schritt nach unten") }
                IconButton(delete) { Icon(Icons.Outlined.Delete, "Schritt löschen") }
            }
            OutlinedTextField(
                step.text,
                { change(step.copy(text = it)) },
                label = { Text("Anleitung") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        }
    }
}

internal fun RecipeIngredient.unitLabel() = if (unit == GroceryUnit.CUSTOM) customUnit ?: "Einheit" else unit.label
