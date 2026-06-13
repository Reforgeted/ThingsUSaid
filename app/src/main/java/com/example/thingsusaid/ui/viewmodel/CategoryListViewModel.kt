package com.example.thingsusaid.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.thingsusaid.data.entity.Category
import com.example.thingsusaid.data.repository.AppRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoryListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application)

    val categories = repository.allCategories
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            repository.initializeDefaultsIfNeeded()
        }
    }

    fun addCategory(name: String, colorHex: String) {
        viewModelScope.launch {
            try {
                repository.addCategory(name, colorHex)
            } catch (e: Exception) {
                Log.e("CategoryListVM", "addCategory failed: ${e.message}", e)
            }
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            try {
                repository.deleteCategory(category)
            } catch (e: Exception) {
                Log.e("CategoryListVM", "deleteCategory failed: ${e.message}", e)
            }
        }
    }

    fun updateSortOrders(items: List<Pair<Long, Int>>) {
        viewModelScope.launch {
            repository.updateCategorySortOrders(items)
        }
    }
}
