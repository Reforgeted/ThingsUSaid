package com.example.thingsusaid.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.thingsusaid.data.AppDatabase
import com.example.thingsusaid.notification.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            CoroutineScope(Dispatchers.IO).launch {
                val dao = AppDatabase.getInstance(context).appDao()
                val notes = dao.getNotesWithFutureReminders()
                notes.forEach { note ->
                    note.reminderTime?.let {
                        ReminderScheduler.scheduleReminder(context, note.noteId, it)
                    }
                }
            }
        }
    }
}
