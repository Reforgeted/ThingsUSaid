package com.example.thingsusaid.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.thingsusaid.data.entity.Note
import com.example.thingsusaid.data.repository.AppRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NoteListViewModel(
    application: Application,
    private val categoryId: Long
) : AndroidViewModel(application) {

    private val repository = AppRepository(application)

    var category by mutableStateOf<com.example.thingsusaid.data.entity.Category?>(null)
        private set

    init {
        viewModelScope.launch {
            category = repository.getCategory(categoryId)
        }
    }

    val notes = repository.getNotesByCategory(categoryId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addNote(title: String, content: String, isTodo: Boolean, dueDate: Long? = null, reminderTime: Long? = null) {
        viewModelScope.launch {
            repository.addNote(categoryId, title, content, isTodo, dueDate, reminderTime)
        }
    }

    fun updateNote(note: Note) {
        viewModelScope.launch {
            repository.updateNote(note)
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            repository.deleteNote(note)
        }
    }

    fun toggleCompletion(note: Note) {
        viewModelScope.launch {
            repository.toggleNoteCompletion(note.noteId, note.isCompleted)
        }
    }

    class Factory(
        private val application: Application,
        private val categoryId: Long
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(NoteListViewModel::class.java)) {
                return NoteListViewModel(application, categoryId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
