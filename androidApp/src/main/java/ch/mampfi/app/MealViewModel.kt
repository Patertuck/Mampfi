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
    fun save(meal: Mahlzeit) = viewModelScope.launch { repository.save(meal).onSuccess { _message.emit("Mahlzeit gespeichert.") }.onFailure { _message.emit("Speichern fehlgeschlagen.") } }
    fun deleteMeal(id: String) = viewModelScope.launch { repository.delete(id).onSuccess { _message.emit("Mahlzeit gelöscht.") }.onFailure { _message.emit("Löschen fehlgeschlagen.") } }
    fun removeDate(id: String, date: LocalDate) = viewModelScope.launch { repository.removeDate(id, date.toString()).onSuccess { _message.emit("Termin entfernt.") }.onFailure { _message.emit("Löschen fehlgeschlagen.") } }
    fun addImage(meal: Mahlzeit, date: LocalDate, name: String, stream: () -> java.io.InputStream) = viewModelScope.launch {
        repository.upload(name, stream()).onSuccess { url -> save(meal.copy(bilder = meal.bilder + MahlzeitBild(url, date.toString()))) }.onFailure { _message.emit("Bild-Upload fehlgeschlagen.") }
    }
}
