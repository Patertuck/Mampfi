package ch.mampfi.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.mampfi.app.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.io.InputStream

data class PendingImageUpload(val filename: String, val openStream: () -> InputStream)

data class SyncStatus(
    val isRefreshing: Boolean = true,
    val isOffline: Boolean = false,
    val lastSuccessfulRefresh: Instant? = null,
)

internal fun SyncStatus.afterRefresh(success: Boolean, completedAt: Instant): SyncStatus = if (success) {
    copy(isRefreshing = false, isOffline = false, lastSuccessfulRefresh = completedAt)
} else {
    copy(isRefreshing = false, isOffline = true)
}

class MealViewModel(private val repository: MealRepository) : ViewModel() {
    val meals = repository.meals.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val awayEntries = repository.awayEntries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _message = MutableSharedFlow<String>(); val message = _message.asSharedFlow()
    private val _syncStatus = MutableStateFlow(SyncStatus())
    val syncStatus = _syncStatus.asStateFlow()
    private var foregroundRefreshJob: Job? = null
    private val refreshMutex = Mutex()

    private suspend fun refreshFromServer(showFailureMessage: Boolean, showActivity: Boolean) = refreshMutex.withLock {
        if (showActivity) _syncStatus.update { it.copy(isRefreshing = true) }
        val result = repository.refresh()
        _syncStatus.update { it.afterRefresh(result.isSuccess, Instant.now()) }
        if (result.isFailure && showFailureMessage) _message.emit("Aktualisierung fehlgeschlagen – lokale Daten werden angezeigt.")
    }

    fun startForegroundRefresh() {
        if (foregroundRefreshJob != null) return
        foregroundRefreshJob = viewModelScope.launch {
            while (isActive) {
                refreshFromServer(
                    showFailureMessage = false,
                    showActivity = _syncStatus.value.lastSuccessfulRefresh == null,
                )
                delay(15_000)
            }
        }
    }

    fun stopForegroundRefresh() {
        foregroundRefreshJob?.cancel()
        foregroundRefreshJob = null
    }
    fun refresh() = viewModelScope.launch { refreshFromServer(showFailureMessage = true, showActivity = true) }
    fun createMeal(meal: Mahlzeit, entry: MahlzeitEintrag, image: PendingImageUpload? = null, onComplete: (Boolean) -> Unit = {}) = viewModelScope.launch {
        saveWithOptionalImage(repository.createMeal(meal, entry), meal.id, entry.id, image, onComplete)
    }
    fun saveIdea(meal: Mahlzeit, isNew: Boolean, onComplete: (Boolean) -> Unit) = viewModelScope.launch {
        val result = if (isNew) repository.createIdea(meal) else repository.updateIdea(meal)
        result
            .onSuccess { _message.emit("Idee gespeichert."); onComplete(true) }
            .onFailure { _message.emit("Speichern fehlgeschlagen."); onComplete(false) }
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
    fun updateMeal(meal: Mahlzeit, onComplete: (Boolean) -> Unit) = viewModelScope.launch {
        repository.updateMeal(meal)
            .onSuccess { _message.emit("Mahlzeit gespeichert."); onComplete(true) }
            .onFailure { _message.emit("Speichern fehlgeschlagen."); onComplete(false) }
    }
    fun createEntry(meal: Mahlzeit, entry: MahlzeitEintrag, image: PendingImageUpload? = null, onComplete: (Boolean) -> Unit = {}) = viewModelScope.launch {
        saveWithOptionalImage(repository.createEntry(meal, entry), meal.id, entry.id, image, onComplete)
    }
    fun updateEntry(meal: Mahlzeit, entry: MahlzeitEintrag, onComplete: (Boolean) -> Unit = {}) = viewModelScope.launch { repository.updateEntry(meal, entry).notifySave(onComplete) }
    fun moveEntry(meal: Mahlzeit, entry: MahlzeitEintrag, date: LocalDate, onComplete: (Boolean) -> Unit) = viewModelScope.launch {
        val result = repository.moveEntry(meal.id, entry, date.toString())
        result.onFailure { _message.emit("Verschieben fehlgeschlagen. Der Zieltag wurde möglicherweise inzwischen belegt.") }
        onComplete(result.isSuccess)
    }
    fun moveAwayEntry(entry: AuswaertsEintrag, date: LocalDate, onComplete: (Boolean) -> Unit) = viewModelScope.launch {
        val result = repository.updateAwayEntry(entry.copy(datum = date.toString()))
        result.onFailure { _message.emit("Verschieben fehlgeschlagen. Am Zieltag gibt es möglicherweise bereits einen Auswärts-Eintrag.") }
        onComplete(result.isSuccess)
    }
    fun deleteEntry(mealId: String, entryId: String) = viewModelScope.launch { repository.deleteEntry(mealId, entryId).onSuccess { _message.emit("Eintrag gelöscht.") }.onFailure { _message.emit("Löschen fehlgeschlagen.") } }
    fun createAwayEntry(entry: AuswaertsEintrag, onSuccess: () -> Unit) = viewModelScope.launch {
        repository.createAwayEntry(entry)
            .onSuccess { _message.emit("Auswärts-Eintrag gespeichert."); onSuccess() }
            .onFailure { _message.emit("Speichern fehlgeschlagen. Der Tag wurde möglicherweise inzwischen belegt.") }
    }
    fun updateAwayEntry(entry: AuswaertsEintrag, onSuccess: () -> Unit) = viewModelScope.launch {
        repository.updateAwayEntry(entry)
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
    private suspend fun saveWithOptionalImage(save: Result<Unit>, mealId: String, entryId: String, image: PendingImageUpload?, onComplete: (Boolean) -> Unit) {
        if (save.isFailure) {
            _message.emit("Speichern fehlgeschlagen.")
            onComplete(false)
            return
        }
        if (image == null) {
            _message.emit("Mahlzeit gespeichert.")
            onComplete(true)
            return
        }
        val upload = runCatching(image.openStream).fold(
            onSuccess = { repository.upload(mealId, entryId, image.filename, it) },
            onFailure = { Result.failure(it) },
        )
        upload
            .onSuccess { _message.emit("Mahlzeit und Bild gespeichert.") }
            .onFailure { _message.emit("Mahlzeit gespeichert, Bild-Upload fehlgeschlagen.") }
        onComplete(true)
    }
    private suspend fun Result<Unit>.notifySave(onComplete: (Boolean) -> Unit) = onSuccess {
        _message.emit("Mahlzeit gespeichert.")
        onComplete(true)
    }.onFailure {
        _message.emit("Speichern fehlgeschlagen.")
        onComplete(false)
    }
}
