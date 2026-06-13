package com.example.thingsusaid.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.thingsusaid.MainActivity
import com.example.thingsusaid.R
import com.example.thingsusaid.data.entity.Note
import com.example.thingsusaid.receiver.SnoozeReceiver

object NotificationHelper {

    private const val CHANNEL_ID = "todo_reminder_channel"
    private const val CHANNEL_NAME = "Todo 提醒"

    fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Todo 事项到期提醒"
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    fun showReminderNotification(context: Context, note: Note) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("category_id", note.categoryId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            note.noteId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, SnoozeReceiver::class.java).apply {
            putExtra("note_id", note.noteId)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            note.noteId.toInt(),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.tixing)
            .setContentTitle(note.title)
            .setContentText(note.content.takeIf { it.isNotBlank() } ?: "该 Todo 到时间了")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(0, "稍后提醒", snoozePendingIntent)
            .build()

        manager.notify(note.noteId.toInt(), notification)
    }
}
