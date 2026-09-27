package ch.mampfi.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.mampfi.app.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.io.InputStream

data class PendingImageUpload(val filename: String, val openStream: () -> InputStream)

class MealViewModel(private val repository: MealRepository) : ViewModel() {
    val meals = repository.meals.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val awayEntries = repository.awayEntries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _message = MutableSharedFlow<String>(); val message = _message.asSharedFlow()
    private var foregroundRefreshJob: Job? = null

    fun startForegroundRefresh() {
        if (foregroundRefreshJob != null) return
        foregroundRefreshJob = viewModelScope.launch {
            while (isActive) {
                repository.refresh()
                delay(15_000)
            }
        }
    }

    fun stopForegroundRefresh() {
        foregroundRefreshJob?.cancel()
        foregroundRefreshJob = null
    }
    fun refresh() = viewModelScope.launch { repository.refresh().onFailure { _message.emit("Aktualisierung fehlgeschlagen – lokale Daten werden angezeigt.") } }
    fun createMeal(meal: Mahlzeit, entry: MahlzeitEintrag, image: PendingImageUpload? = null) = viewModelScope.launch {
        saveWithOptionalImage(repository.createMeal(meal, entry), meal.id, entry.id, image)
    }
    fun saveIdea(meal: Mahlzeit, isNew: Boolean, onSuccess: () -> Unit) = viewModelScope.launch {
        val result = if (isNew) repository.createIdea(meal) else repository.updateIdea(meal)
        result
            .onSuccess { _message.emit("Idee gespeichert."); onSuccess() }
            .onFailure { _message.emit("Speichern fehlgeschlagen.") }
    }
    fun deleteIdea(mealId: String, onSuccess: () -> Unit) = viewModelScope.launch {
        repository.deleteIdea(mealId)
            .onSuccess { _message.emit("Idee gelöscht."); onSuccess() }
            .onFailure { _message.emit("Löschen fehlgeschlagen.") }
    }
    fun setIdea(meal: Mahlzeit, isIdea: Boolean, onSuccess: () -> Unit = {}) = viewModelScope.launch {
        repository.setIdea(meal, isIdea)
            .onSuccess { _message.emit(if (isIdea) "Zu Ideen hinzugefügt." else "Aus Ideen entfernt."); onSuccess() }
            .onFailure { _message.emit("Speichern fehlgeschlagen.") }
    }
    fun createEntry(meal: Mahlzeit, entry: MahlzeitEintrag, image: PendingImageUpload? = null) = viewModelScope.launch {
        saveWithOptionalImage(repository.createEntry(meal, entry), meal.id, entry.id, image)
    }
    fun updateEntry(meal: Mahlzeit, entry: MahlzeitEintrag) = viewModelScope.launch { repository.updateEntry(meal, entry).notifySave() }
    fun deleteEntry(mealId: String, entryId: String) = viewModelScope.launch { repository.deleteEntry(mealId, entryId).onSuccess { _message.emit("Eintrag gelöscht.") }.onFailure { _message.emit("Löschen fehlgeschlagen.") } }
    fun createAwayEntry(entry: AuswaertsEintrag, replaceMeals: Boolean, onSuccess: () -> Unit) = viewModelScope.launch {
        repository.createAwayEntry(entry, replaceMeals)
            .onSuccess { _message.emit("Auswärts-Eintrag gespeichert."); onSuccess() }
            .onFailure { _message.emit("Speichern fehlgeschlagen. Der Tag wurde möglicherweise inzwischen belegt.") }
    }
    fun updateAwayEntry(entry: AuswaertsEintrag, replaceMeals: Boolean, onSuccess: () -> Unit) = viewModelScope.launch {
        repository.updateAwayEntry(entry, replaceMeals)
            .onSuccess { _message.emit("Auswärts-Eintrag gespeichert."); onSuccess() }
            .onFailure { _message.emit("Speichern fehlgeschlagen. Der Tag wurde möglicherweise inzwischen belegt.") }
    }
    fun deleteAwayEntry(id: String, onSuccess: () -> Unit) = viewModelScope.launch {
        repository.deleteAwayEntry(id)
            .onSuccess { _message.emit("Auswärts-Eintrag gelöscht."); onSuccess() }
            .onFailure { _message.emit("Löschen fehlgeschlagen.") }
    }
    fun addImage(mealId: String, entryId: String, name: String, stream: () -> InputStream) = viewModelScope.launch {
        val input = runCatching(stream).getOrElse {
            _message.emit("Bild-Upload fehlgeschlagen.")
            return@launch
        }
        repository.upload(mealId, entryId, name, input).onFailure { _message.emit("Bild-Upload fehlgeschlagen.") }
    }
    private suspend fun saveWithOptionalImage(save: Result<Unit>, mealId: String, entryId: String, image: PendingImageUpload?) {
        if (save.isFailure) {
            _message.emit("Speichern fehlgeschlagen.")
            return
        }
        if (image == null) {
            _message.emit("Mahlzeit gespeichert.")
            return
        }
        val upload = runCatching(image.openStream).fold(
            onSuccess = { repository.upload(mealId, entryId, image.filename, it) },
            onFailure = { Result.failure(it) },
        )
        upload
            .onSuccess { _message.emit("Mahlzeit und Bild gespeichert.") }
            .onFailure { _message.emit("Mahlzeit gespeichert, Bild-Upload fehlgeschlagen.") }
    }
    private suspend fun Result<Unit>.notifySave() = onSuccess { _message.emit("Mahlzeit gespeichert.") }.onFailure { _message.emit("Speichern fehlgeschlagen.") }
}
