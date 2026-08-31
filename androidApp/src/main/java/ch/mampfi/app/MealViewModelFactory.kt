package ch.mampfi.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ch.mampfi.app.data.MealRepository
class MealViewModelFactory(private val repository: MealRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>) = MealViewModel(repository) as T
}
