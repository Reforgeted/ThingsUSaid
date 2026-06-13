package com.example.thingsusaid.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.thingsusaid.data.AppDatabase

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val noteId = inputData.getLong("note_id", -1)
        if (noteId == -1L) return Result.failure()

        val dao = AppDatabase.getInstance(applicationContext).appDao()
        val note = dao.getNoteById(noteId) ?: return Result.success()

        // 如果已完成或没有提醒时间，不发送通知
        if (note.isCompleted || note.reminderTime == null) return Result.success()

        NotificationHelper.showReminderNotification(applicationContext, note)
        return Result.success()
    }
}
