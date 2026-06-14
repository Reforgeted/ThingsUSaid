package com.example.thingsusaid.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.thingsusaid.data.entity.Note

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditBottomSheet(
    note: Note?,
    onDismiss: () -> Unit,
    onSave: (title: String, content: String, isTodo: Boolean, dueDate: Long?, reminderTime: Long?) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by remember(note) { mutableStateOf(note?.title ?: "") }
    var content by remember(note) { mutableStateOf(note?.content ?: "") }
    var isTodo by remember(note) { mutableStateOf(note?.isTodo ?: false) }
    var dueDate by remember(note) { mutableStateOf(note?.dueDate) }
    var reminderTime by remember(note) { mutableStateOf(note?.reminderTime) }
    val isEditing = note != null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        scrimColor = Color.Black.copy(alpha = 0.4f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("完成", style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("标题") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("内容") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 5
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isTodo,
                        onCheckedChange = { isTodo = it }
                    )
                    Text("设为 Todo", style = MaterialTheme.typography.bodyMedium)
                }
                if (isEditing && onDelete != null) {
                    TextButton(
                        onClick = {
                            onDelete()
                            onDismiss()
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("删除")
                    }
                }
            }

            if (isTodo) {
                Spacer(modifier = Modifier.height(12.dp))
                CollapsibleTimePicker(
                    reminderTime = reminderTime,
                    onTimeChange = { newTime ->
                        reminderTime = newTime
                        dueDate = newTime
                    }
                )
                if (reminderTime != null) {
                    TextButton(
                        onClick = {
                            reminderTime = null
                            dueDate = null
                        },
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text("清除提醒", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(title.trim(), content.trim(), isTodo, dueDate, reminderTime)
                        onDismiss()
                    }
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存")
            }
        }
    }
}
