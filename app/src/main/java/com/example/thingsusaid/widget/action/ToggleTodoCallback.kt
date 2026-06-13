package com.example.thingsusaid.widget.action

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.example.thingsusaid.data.AppDatabase
import com.example.thingsusaid.widget.TodoWidgetUpdater

class ToggleTodoCallback : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val noteId = parameters[noteIdKey] ?: return
        val dao = AppDatabase.getInstance(context).appDao()
        val note = dao.getNoteById(noteId) ?: return
        dao.updateNoteCompletion(noteId, !note.isCompleted)
        TodoWidgetUpdater.updateForCategory(context, note.categoryId)
    }

    companion object {
        val noteIdKey = ActionParameters.Key<Long>("note_id")
    }
}
