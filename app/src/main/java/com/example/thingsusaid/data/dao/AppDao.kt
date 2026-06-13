package com.example.thingsusaid.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
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

    @Query("SELECT * FROM categories ORDER BY createdAt DESC")
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

    @Query("SELECT * FROM notes WHERE categoryId = :catId ORDER BY isCompleted ASC, createdAt DESC")
    fun getNotesByCategoryFlow(catId: Long): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE categoryId = :catId ORDER BY isCompleted ASC, createdAt DESC")
    suspend fun getNotesByCategoryId(catId: Long): List<Note>

    @Query("SELECT * FROM notes WHERE noteId = :id")
    suspend fun getNoteById(id: Long): Note?

    @Query("UPDATE notes SET isCompleted = :completed, updatedAt = :updatedAt WHERE noteId = :noteId")
    suspend fun updateNoteCompletion(noteId: Long, completed: Boolean, updatedAt: Long = System.currentTimeMillis())

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
}
