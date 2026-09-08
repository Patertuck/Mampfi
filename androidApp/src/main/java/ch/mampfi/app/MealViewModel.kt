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

class MealViewModel(private val repository: MealRepository) : ViewModel() {
    val meals = repository.meals.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
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
    fun createMeal(meal: Mahlzeit, entry: MahlzeitEintrag) = viewModelScope.launch { repository.createMeal(meal, entry).notifySave() }
    fun createEntry(meal: Mahlzeit, entry: MahlzeitEintrag) = viewModelScope.launch { repository.createEntry(meal, entry).notifySave() }
    fun updateEntry(meal: Mahlzeit, entry: MahlzeitEintrag) = viewModelScope.launch { repository.updateEntry(meal, entry).notifySave() }
    fun deleteEntry(mealId: String, entryId: String) = viewModelScope.launch { repository.deleteEntry(mealId, entryId).onSuccess { _message.emit("Eintrag gelöscht.") }.onFailure { _message.emit("Löschen fehlgeschlagen.") } }
    fun addImage(mealId: String, entryId: String, name: String, stream: () -> java.io.InputStream) = viewModelScope.launch {
        repository.upload(mealId, entryId, name, stream()).onFailure { _message.emit("Bild-Upload fehlgeschlagen.") }
    }
    private suspend fun Result<Unit>.notifySave() = onSuccess { _message.emit("Mahlzeit gespeichert.") }.onFailure { _message.emit("Speichern fehlgeschlagen.") }
}
