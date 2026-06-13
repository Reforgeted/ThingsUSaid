package com.example.thingsusaid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.thingsusaid.data.entity.Note
import com.example.thingsusaid.ui.components.NoteEditBottomSheet
import com.example.thingsusaid.ui.components.NoteItem
import com.example.thingsusaid.ui.viewmodel.NoteListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListScreen(
    categoryId: Long,
    onBack: () -> Unit,
    viewModel: NoteListViewModel = viewModel(
        factory = NoteListViewModel.Factory(
            androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application,
            categoryId
        )
    )
) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val category = viewModel.category
    var showEditSheet by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<Note?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(category?.name ?: "便签") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editingNote = null
                showEditSheet = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "新建便签")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (notes.isEmpty()) {
                EmptyState(
                    title = "这里空空如也",
                    subtitle = "点击右下角添加第一条便签或 Todo"
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(notes, key = { it.noteId }) { note ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { dismissValue ->
                                when (dismissValue) {
                                    SwipeToDismissBoxValue.EndToStart -> {
                                        viewModel.deleteNote(note)
                                        true
                                    }
                                    SwipeToDismissBoxValue.StartToEnd -> {
                                        if (note.isTodo) {
                                            viewModel.toggleCompletion(note)
                                        }
                                        false
                                    }
                                    SwipeToDismissBoxValue.Settled -> false
                                }
                            },
                            positionalThreshold = { it * 0.3f }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                val color = when (dismissState.dismissDirection) {
                                    SwipeToDismissBoxValue.EndToStart -> Color(0xFFFF3B30)
                                    SwipeToDismissBoxValue.StartToEnd -> Color(0xFF34C759)
                                    else -> Color.Transparent
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(color)
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                                        Alignment.CenterEnd
                                    } else {
                                        Alignment.CenterStart
                                    }
                                ) {
                                    if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "删除",
                                            tint = Color.White
                                        )
                                    } else if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "完成",
                                            tint = Color.White
                                        )
                                    }
                                }
                            },
                            enableDismissFromStartToEnd = note.isTodo,
                            enableDismissFromEndToStart = true,
                        ) {
                            NoteItem(
                                note = note,
                                onToggleCompletion = if (note.isTodo) {
                                    { viewModel.toggleCompletion(note) }
                                } else null,
                                onClick = {
                                    editingNote = note
                                    showEditSheet = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditSheet) {
        NoteEditBottomSheet(
            note = editingNote,
            onDismiss = { showEditSheet = false },
            onSave = { title, content, isTodo, dueDate, reminderTime ->
                if (editingNote != null) {
                    viewModel.updateNote(
                        editingNote!!.copy(
                            title = title,
                            content = content,
                            isTodo = isTodo,
                            dueDate = dueDate,
                            reminderTime = reminderTime
                        )
                    )
                } else {
                    viewModel.addNote(title, content, isTodo, dueDate, reminderTime)
                }
            },
            onDelete = editingNote?.let { note ->
                { viewModel.deleteNote(note) }
            }
        )
    }
}
