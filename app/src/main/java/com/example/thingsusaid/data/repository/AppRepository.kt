package com.example.thingsusaid.data.repository

import android.content.Context
import android.util.Log
import com.example.thingsusaid.data.AppDatabase
import com.example.thingsusaid.data.entity.Category
import com.example.thingsusaid.data.entity.Note
import com.example.thingsusaid.notification.ReminderScheduler
import com.example.thingsusaid.widget.TodoWidgetUpdater
import kotlinx.coroutines.flow.Flow

class AppRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dao = AppDatabase.getInstance(appContext).appDao()

    val allCategories: Flow<List<Category>> = dao.getAllCategoriesFlow()

    suspend fun initializeDefaultsIfNeeded() {
        try {
            val count = dao.getCategoryCount()
            if (count == 0) {
                val welcomeId = dao.insertCategory(
                    Category(name = "欢迎", colorHex = "#FF6750A4")
                )
                dao.insertNote(
                    Note(
                        categoryId = welcomeId,
                        title = "欢迎使用 Things U Said",
                        content = "这是一款专注于个人事项管理的应用。你可以创建分组来整理不同类别的便签和待办事项。",
                        isTodo = false
                    )
                )
                dao.insertNote(
                    Note(
                        categoryId = welcomeId,
                        title = "创建分组",
                        content = "点击右下角的 + 按钮创建新的分组，比如「生活」、「工作」、「想法」等。",
                        isTodo = false
                    )
                )
                dao.insertNote(
                    Note(
                        categoryId = welcomeId,
                        title = "添加便签与 Todo",
                        content = "进入分组后，可以添加纯便签或带勾选框的待办事项。完成的 Todo 会自动沉底哦。",
                        isTodo = false
                    )
                )
                dao.insertNote(
                    Note(
                        categoryId = welcomeId,
                        title = "桌面小组件",
                        content = "后续版本支持将分组内容添加到桌面小组件，方便随时查看。敬请期待！",
                        isTodo = false
                    )
                )
                Log.d("AppRepository", "Default data initialized")
            }
        } catch (e: Exception) {
            Log.e("AppRepository", "Failed to initialize defaults", e)
        }
    }

    suspend fun addCategory(name: String, colorHex: String): Long {
        val id = dao.insertCategory(Category(name = name, colorHex = colorHex))
        TodoWidgetUpdater.updateAll(appContext)
        return id
    }

    suspend fun updateCategory(category: Category) {
        dao.updateCategory(category)
        TodoWidgetUpdater.updateAll(appContext)
    }

    suspend fun deleteCategory(category: Category) {
        dao.deleteCategory(category)
        TodoWidgetUpdater.updateAll(appContext)
    }

    suspend fun getCategory(id: Long) = dao.getCategoryById(id)

    fun getNotesByCategory(categoryId: Long): Flow<List<Note>> {
        return dao.getNotesByCategoryFlow(categoryId)
    }

    suspend fun addNote(
        categoryId: Long,
        title: String,
        content: String,
        isTodo: Boolean,
        dueDate: Long? = null,
        reminderTime: Long? = null
    ): Long {
        val id = dao.insertNote(
            Note(
                categoryId = categoryId,
                title = title,
                content = content,
                isTodo = isTodo,
                dueDate = dueDate,
                reminderTime = reminderTime
            )
        )
        if (reminderTime != null && reminderTime > System.currentTimeMillis()) {
            ReminderScheduler.scheduleReminder(appContext, id, reminderTime)
        }
        TodoWidgetUpdater.updateForCategory(appContext, categoryId)
        return id
    }

    suspend fun updateNote(note: Note) {
        dao.updateNote(note)
        // 重新调度提醒
        ReminderScheduler.cancelReminder(appContext, note.noteId)
        note.reminderTime?.let {
            if (it > System.currentTimeMillis()) {
                ReminderScheduler.scheduleReminder(appContext, note.noteId, it)
            }
        }
        TodoWidgetUpdater.updateForCategory(appContext, note.categoryId)
    }

    suspend fun deleteNote(note: Note) {
        ReminderScheduler.cancelReminder(appContext, note.noteId)
        dao.deleteNote(note)
        TodoWidgetUpdater.updateForCategory(appContext, note.categoryId)
    }

    suspend fun getNote(id: Long) = dao.getNoteById(id)

    suspend fun toggleNoteCompletion(noteId: Long, current: Boolean) {
        val note = dao.getNoteById(noteId)
        dao.updateNoteCompletion(noteId, !current)
        note?.let {
            // 如果标记为完成，取消提醒
            if (!current) {
                ReminderScheduler.cancelReminder(appContext, noteId)
            }
            TodoWidgetUpdater.updateForCategory(appContext, it.categoryId)
        }
    }
}
