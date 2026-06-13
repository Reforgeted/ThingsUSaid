package com.example.thingsusaid.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.thingsusaid.data.entity.AppSetting
import com.example.thingsusaid.data.entity.Category
import com.example.thingsusaid.data.entity.Note
import com.example.thingsusaid.data.entity.WidgetConfig
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {

    // ==================== 分组操作 ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category): Long

    @Update
    suspend fun updateCategory(category: Category)

    @Delete
    suspend fun deleteCategory(category: Category)

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, createdAt DESC")
    fun getAllCategoriesFlow(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE categoryId = :id")
    suspend fun getCategoryById(id: Long): Category?

    @Query("SELECT * FROM categories WHERE categoryId = :id")
    fun getCategoryByIdFlow(id: Long): Flow<Category?>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun getCategoryCount(): Int

    // ==================== 便签/Todo 操作 ====================

    @Insert
    suspend fun insertNote(note: Note): Long

    @Update
    suspend fun updateNote(note: Note)

    @Delete
    suspend fun deleteNote(note: Note)

    @Query("SELECT * FROM notes WHERE categoryId = :catId ORDER BY isCompleted ASC, sortOrder ASC, createdAt DESC")
    fun getNotesByCategoryFlow(catId: Long): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE categoryId = :catId ORDER BY isCompleted ASC, sortOrder ASC, createdAt DESC")
    suspend fun getNotesByCategoryId(catId: Long): List<Note>

    @Query("SELECT * FROM notes WHERE noteId = :id")
    suspend fun getNoteById(id: Long): Note?

    @Query("UPDATE notes SET isCompleted = :completed, updatedAt = :updatedAt WHERE noteId = :noteId")
    suspend fun updateNoteCompletion(noteId: Long, completed: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM notes WHERE reminderTime IS NOT NULL AND reminderTime > :currentTime")
    suspend fun getNotesWithFutureReminders(currentTime: Long = System.currentTimeMillis()): List<Note>

    @Query("UPDATE categories SET sortOrder = :sortOrder WHERE categoryId = :categoryId")
    suspend fun updateCategorySortOrder(categoryId: Long, sortOrder: Int)

    @Query("UPDATE notes SET sortOrder = :sortOrder WHERE noteId = :noteId")
    suspend fun updateNoteSortOrder(noteId: Long, sortOrder: Int)

    // ==================== 小组件配置操作 ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveWidgetConfig(config: WidgetConfig)

    @Query("SELECT * FROM widget_configs WHERE appWidgetId = :widgetId")
    suspend fun getWidgetConfig(widgetId: Int): WidgetConfig?

    @Query("SELECT * FROM widget_configs WHERE appWidgetId = :widgetId")
    fun getWidgetConfigFlow(widgetId: Int): Flow<WidgetConfig?>

    @Query("DELETE FROM widget_configs WHERE appWidgetId = :widgetId")
    suspend fun deleteWidgetConfig(widgetId: Int)

    @Query("SELECT * FROM widget_configs WHERE boundCategoryId = :categoryId")
    suspend fun getWidgetConfigsByCategoryId(categoryId: Long): List<WidgetConfig>

    // ==================== 应用设置操作 ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSetting(setting: AppSetting)

    @Query("SELECT * FROM app_settings WHERE `key` = :key")
    suspend fun getSetting(key: String): AppSetting?

    @Query("SELECT * FROM app_settings WHERE `key` = :key")
    fun getSettingFlow(key: String): Flow<AppSetting?>
}
