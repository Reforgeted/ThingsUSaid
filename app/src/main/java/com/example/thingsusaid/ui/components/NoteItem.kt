package com.example.thingsusaid.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.thingsusaid.data.entity.Note
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NoteItem(
    note: Note,
    onToggleCompletion: (() -> Unit)? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCompleted = note.isCompleted && note.isTodo
    val targetAlpha = if (isCompleted) 0.5f else 1f
    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "completionAlpha"
    )
    val textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (note.isTodo && onToggleCompletion != null) {
                Checkbox(
                    checked = note.isCompleted,
                    onCheckedChange = { onToggleCompletion() },
                    modifier = Modifier.padding(end = 12.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alpha(animatedAlpha),
                    textDecoration = textDecoration
                )
                if (note.content.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = note.content,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.alpha(animatedAlpha),
                        textDecoration = textDecoration
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatDate(note.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.alpha(animatedAlpha)
                    )
                    if (note.reminderTime != null && note.isTodo) {
                        Spacer(modifier = Modifier.width(8.dp))
                        val reminderText = formatReminder(note.reminderTime)
                        val isOverdue = note.reminderTime < System.currentTimeMillis() && !note.isCompleted
                        Text(
                            text = "⏰ $reminderText",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.alpha(animatedAlpha)
                        )
                    }
                }
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatReminder(timestamp: Long): String {
    val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
