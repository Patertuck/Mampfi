package ch.mampfi.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ch.mampfi.app.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal const val GROCERY_SYNC_INTERVAL_MILLIS = 2_000L

class GroceryViewModel(private val repository: GroceryRepository) : ViewModel() {
    val items = repository.items.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _suggestions = MutableStateFlow<List<GrocerySuggestion>>(emptyList())
    val suggestions = _suggestions.asStateFlow()
    private val _message = MutableSharedFlow<String>()
    val message = _message.asSharedFlow()
    private var syncJob: Job? = null
    private var searchJob: Job? = null

    fun startSync() {
        if (syncJob != null) return
        syncJob = viewModelScope.launch {
            while (isActive) {
                repository.synchronize().onSuccess { rejected -> rejected.firstOrNull()?.let { _message.emit(it) } }
                delay(GROCERY_SYNC_INTERVAL_MILLIS)
            }
        }
    }
    fun stopSync() { syncJob?.cancel(); syncJob = null }
    fun search(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(650)
            _suggestions.value = repository.search(query)
        }
    }
    fun clearSearch() { searchJob?.cancel(); _suggestions.value = emptyList() }
    fun lookupBarcode(barcode: String, result: (GroceryBarcodeResult) -> Unit) = viewModelScope.launch {
        result(repository.lookupBarcode(barcode))
    }
    fun showMessage(message: String) = viewModelScope.launch { _message.emit(message) }
    fun add(suggestion: GrocerySuggestion, baseQuantity: Double, unit: GroceryUnit) = launch("Hinzufügen fehlgeschlagen.") { repository.add(suggestion, baseQuantity, unit) }
    fun addRecipeIngredients(items: List<RecipeGrocerySelection>, completed: (Boolean) -> Unit = {}) = viewModelScope.launch {
        repository.addAll(items).onSuccess { _message.emit("${items.size} Zutaten hinzugefügt."); completed(true) }
            .onFailure { _message.emit("Zutaten konnten nicht hinzugefügt werden."); completed(false) }
    }
    fun update(item: GroceryItem) = launch("Ändern fehlgeschlagen.") { repository.update(item) }
    fun complete(item: GroceryItem) = launch("Abhaken fehlgeschlagen.") { repository.complete(item) }
    fun restore(item: GroceryItem) = launch("Wiederherstellen fehlgeschlagen.") { repository.restore(item) }
    fun delete(item: GroceryItem) = launch("Löschen fehlgeschlagen.") { repository.delete(item) }

    private fun launch(failure: String, block: suspend () -> Result<Unit>) = viewModelScope.launch {
        block().onFailure { _message.emit(failure) }
    }
}

class GroceryViewModelFactory(private val repository: GroceryRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = GroceryViewModel(repository) as T
}
