package com.example.thingsusaid.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.thingsusaid.data.AppDatabase
import com.example.thingsusaid.notification.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SnoozeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getLongExtra("note_id", -1L)
        if (noteId == -1L) return

        // 取消当前通知
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.cancel(noteId.toInt())

        CoroutineScope(Dispatchers.IO).launch {
            val dao = AppDatabase.getInstance(context).appDao()
            val intervalMinutes = dao.getSetting("snooze_interval")?.value?.toIntOrNull() ?: 5
            val newReminderTime = System.currentTimeMillis() + intervalMinutes * 60 * 1000
            val note = dao.getNoteById(noteId) ?: return@launch
            dao.updateNote(note.copy(reminderTime = newReminderTime))
            ReminderScheduler.scheduleReminder(context, noteId, newReminderTime)
        }
    }
}
