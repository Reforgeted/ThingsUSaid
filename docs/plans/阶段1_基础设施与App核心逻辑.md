# 阶段 1：基础设施与 App 核心逻辑 —— 详细设计文档

> **阶段定位**：打地基。完成数据层与 App 内基础 UI，确保分组、便签/Todo 的增删改查在应用内完整跑通。  
> **交付标准**：App 打开后，能正常创建“生活”、“想法”等分组，并在各分组下成功添加、编辑、删除便签与 Todo。  
> **暂不涉及**：桌面小组件（Glance）、动态配色、多实例配置。这些留给阶段 2/3/4。

---

## 1. 阶段目标与范围

| 模块 | 内容 | 说明 |
|------|------|------|
| **数据层** | Room 数据库搭建 | 3 张表：`categories`、`notes`、`widget_configs`（widget_configs 本阶段只建表，暂不被 UI 直接使用，为阶段 3 留好接口） |
| **业务层** | Repository + ViewModel | 封装数据库操作，暴露 `Flow` 给 UI，实现响应式刷新 |
| **UI 层** | Jetpack Compose 纯声明式界面 | 分组管理页、便签列表页、便签详情/编辑页 |
| **架构** | 单 Activity + Navigation Compose | 为后续扩展预留导航能力 |
| **依赖升级** | 引入 Room、KSP、ViewModel Compose、Navigation Compose | 见第 2 节 |

---

## 2. 技术依赖与 Gradle 配置

### 2.1 需要新增的依赖（`gradle/libs.versions.toml`）

```toml
[versions]
# ... 保留原有版本 ...
room = "2.7.1"
ksp = "2.2.10-1.0.31"
lifecycle = "2.9.0"
navigation = "2.9.0"
kotlinxCoroutines = "1.10.2"

[libraries]
# Room
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }

# Lifecycle & ViewModel
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }

# Navigation
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }

# Coroutines
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "kotlinxCoroutines" }

[plugins]
# ... 保留原有插件 ...
google-devtools-ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

### 2.2 `app/build.gradle.kts` 需要补充的内容

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp) // 新增：KSP 用于 Room 注解处理
}

dependencies {
    // ... 保留原有依赖 ...

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // ViewModel Compose + Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Navigation Compose
    implementation(libs.androidx.navigation.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
}
```

> **注意**：引入 KSP 后，首次 Sync 需要在 Android Studio 的 Gradle 面板中点击 "Sync Project with Gradle Files"。

---

## 3. 数据库架构详设（Room）

### 3.1 数据实体（Entities）

```kotlin
package com.example.thingsusaid.data.entity

import androidx.room.*

/**
 * 分组表
 * 用于归类便签/Todo，例如：生活、想法、工作
 */
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true)
    val categoryId: Long = 0,
    val name: String,
    val colorHex: String, // 存储如 "#FF6750A4"，供 UI 显示主题色
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 便签表（同时承载纯便签与 Todo 两种形态）
 * isTodo = false -> 纯便签
 * isTodo = true  -> 待办事项（可勾选完成）
 */
@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["categoryId"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE // 删除分组时，级联删除其下所有便签
        )
    ],
    indices = [Index("categoryId")] // 加速按分组查询
)
data class Note(
    @PrimaryKey(autoGenerate = true)
    val noteId: Long = 0,
    val categoryId: Long,
    val title: String,
    val content: String,
    val isTodo: Boolean = false,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 小组件配置表（阶段 1 只建表，阶段 3 启用）
 * 记录每个桌面小组件实例绑定的分组 ID
 */
@Entity(tableName = "widget_configs")
data class WidgetConfig(
    @PrimaryKey
    val appWidgetId: Int,
    val boundCategoryId: Long
)
```

### 3.2 DAO 接口

```kotlin
package com.example.thingsusaid.data.dao

import androidx.room.*
import com.example.thingsusaid.data.entity.*
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

    // ==================== 便签/Todo 操作 ====================

    @Insert
    suspend fun insertNote(note: Note): Long

    @Update
    suspend fun updateNote(note: Note)

    @Delete
    suspend fun deleteNote(note: Note)

    @Query("SELECT * FROM notes WHERE categoryId = :catId ORDER BY createdAt DESC")
    fun getNotesByCategoryFlow(catId: Long): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE noteId = :id")
    suspend fun getNoteById(id: Long): Note?

    @Query("UPDATE notes SET isCompleted = :completed WHERE noteId = :noteId")
    suspend fun updateNoteCompletion(noteId: Long, completed: Boolean)

    // ==================== 小组件配置操作（阶段 3 使用） ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveWidgetConfig(config: WidgetConfig)

    @Query("SELECT * FROM widget_configs WHERE appWidgetId = :widgetId")
    suspend fun getWidgetConfig(widgetId: Int): WidgetConfig?

    @Query("DELETE FROM widget_configs WHERE appWidgetId = :widgetId")
    suspend fun deleteWidgetConfig(widgetId: Int)
}
```

### 3.3 Database 定义

```kotlin
package com.example.thingsusaid.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.thingsusaid.data.dao.AppDao
import com.example.thingsusaid.data.entity.Category
import com.example.thingsusaid.data.entity.Note
import com.example.thingsusaid.data.entity.WidgetConfig

@Database(
    entities = [Category::class, Note::class, WidgetConfig::class],
    version = 1,
    exportSchema = false // 初期迭代快，暂不导出 schema；稳定后建议开启
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "things_you_said.db"
                )
                .fallbackToDestructiveMigration() // 开发期方便，上线后需改为 Migration
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}
```

---

## 4. Repository 层设计

采用单例模式（由 ViewModel 持有），封装所有数据库操作，向上屏蔽 Room 细节。

```kotlin
package com.example.thingsusaid.data.repository

import android.content.Context
import com.example.thingsusaid.data.AppDatabase
import com.example.thingsusaid.data.entity.Category
import com.example.thingsusaid.data.entity.Note
import kotlinx.coroutines.flow.Flow

class AppRepository(context: Context) {

    private val dao = AppDatabase.getInstance(context).appDao()

    // ----- 分组 -----
    val allCategories: Flow<List<Category>> = dao.getAllCategoriesFlow()

    suspend fun addCategory(name: String, colorHex: String): Long {
        return dao.insertCategory(Category(name = name, colorHex = colorHex))
    }

    suspend fun updateCategory(category: Category) = dao.updateCategory(category)
    suspend fun deleteCategory(category: Category) = dao.deleteCategory(category)
    suspend fun getCategory(id: Long) = dao.getCategoryById(id)

    // ----- 便签/Todo -----
    fun getNotesByCategory(categoryId: Long): Flow<List<Note>> {
        return dao.getNotesByCategoryFlow(categoryId)
    }

    suspend fun addNote(categoryId: Long, title: String, content: String, isTodo: Boolean): Long {
        return dao.insertNote(
            Note(categoryId = categoryId, title = title, content = content, isTodo = isTodo)
        )
    }

    suspend fun updateNote(note: Note) = dao.updateNote(note)
    suspend fun deleteNote(note: Note) = dao.deleteNote(note)
    suspend fun getNote(id: Long) = dao.getNoteById(id)
    suspend fun toggleNoteCompletion(noteId: Long, current: Boolean) {
        dao.updateNoteCompletion(noteId, !current)
    }
}
```

---

## 5. ViewModel 层设计

使用 `ViewModel` + ` StateFlow ` 管理 UI 状态，配合 `collectAsStateWithLifecycle()` 在 Compose 中消费。

### 5.1 分组列表 ViewModel

```kotlin
package com.example.thingsusaid.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.thingsusaid.data.entity.Category
import com.example.thingsusaid.data.repository.AppRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoryListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application)

    val categories = repository.allCategories
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addCategory(name: String, colorHex: String) {
        viewModelScope.launch {
            repository.addCategory(name, colorHex)
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            repository.deleteCategory(category)
        }
    }
}
```

### 5.2 便签列表 ViewModel

```kotlin
package com.example.thingsusaid.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.thingsusaid.data.entity.Note
import com.example.thingsusaid.data.repository.AppRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class NoteListViewModel(
    application: Application,
    private val categoryId: Long
) : AndroidViewModel(application) {

    private val repository = AppRepository(application)

    val notes = repository.getNotesByCategory(categoryId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addNote(title: String, content: String, isTodo: Boolean) {
        viewModelScope.launch {
            repository.addNote(categoryId, title, content, isTodo)
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            repository.deleteNote(note)
        }
    }

    fun toggleCompletion(note: Note) {
        viewModelScope.launch {
            repository.toggleNoteCompletion(note.noteId, note.isCompleted)
        }
    }
}
```

> **工厂模式提示**：`NoteListViewModel` 需要外部传入 `categoryId`，需配合 `ViewModelProvider.Factory` 或直接使用 Navigation 的 `viewModel()` + 自定义 Factory。详设代码中略去工厂，实际编码时补充。

---

## 6. UI 层设计（Compose Screens）

### 6.1 页面路由定义

```kotlin
package com.example.thingsusaid.ui.navigation

object Routes {
    const val CATEGORY_LIST = "category_list"
    const val NOTE_LIST = "note_list/{categoryId}"
    const val NOTE_DETAIL = "note_detail/{noteId}"

    fun noteList(categoryId: Long) = "note_list/$categoryId"
    fun noteDetail(noteId: Long) = "note_detail/$noteId"
}
```

### 6.2 主入口（`MainActivity.kt` 改造）

```kotlin
package com.example.thingsusaid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.thingsusaid.ui.navigation.Routes
import com.example.thingsusaid.ui.screens.CategoryListScreen
import com.example.thingsusaid.ui.screens.NoteListScreen
import com.example.thingsusaid.ui.theme.ThingsYouSaidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ThingsYouSaidTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.CATEGORY_LIST) {
        composable(Routes.CATEGORY_LIST) {
            CategoryListScreen(
                onCategoryClick = { catId ->
                    navController.navigate(Routes.noteList(catId))
                }
            )
        }
        composable("note_list/{categoryId}") { backStackEntry ->
            val categoryId = backStackEntry.arguments?.getString("categoryId")?.toLongOrNull() ?: return@composable
            NoteListScreen(
                categoryId = categoryId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
```

### 6.3 分组列表页（`CategoryListScreen`）

- **布局**：顶部 `TopAppBar` + 中部 `LazyColumn` 展示分组卡片 + 右下角 `FloatingActionButton` 新增分组。
- **交互**：点击分组进入便签列表；长按或点击菜单删除分组。
- **新增分组弹窗**：底部 `ModalBottomSheet` 或 `AlertDialog`，输入名称 + 选择颜色（提供 6-8 个预设色块）。

### 6.4 便签列表页（`NoteListScreen`）

- **布局**：顶部 `TopAppBar`（显示分组名称）+ 中部 `LazyColumn`。
- **列表项**：
  - 纯便签：显示标题 + 内容摘要 + 创建时间。
  - Todo：左侧显示勾选框（`Checkbox`），勾选后文字带删除线且透明度降低。
- **底部/悬浮按钮**：`FloatingActionButton` 点击后弹出选择：“新建便签”或“新建 Todo”。
- **空状态**：当分组下无内容时，居中显示插画 + 文字引导。

### 6.5 便签详情/编辑页（`NoteDetailScreen`，可选做）

- 如果阶段 1 时间充裕，可加上；否则列表页内直接“点击标题编辑”也是一种轻量方案。  
- 建议：阶段 1 以「列表页内直接展开编辑」或「BottomSheet 编辑」代替独立详情页，减少页面数量，快速验证数据流。

---

## 7. 建议的代码目录结构

```
com.example.thingsusaid
├── data
│   ├── AppDatabase.kt
│   ├── entity
│   │   ├── Category.kt
│   │   ├── Note.kt
│   │   └── WidgetConfig.kt
│   ├── dao
│   │   └── AppDao.kt
│   └── repository
│       └── AppRepository.kt
├── ui
│   ├── navigation
│   │   └── Routes.kt
│   ├── screens
│   │   ├── CategoryListScreen.kt
│   │   └── NoteListScreen.kt
│   ├── components
│   │   ├── CategoryCard.kt
│   │   ├── NoteItem.kt
│   │   └── AddCategoryDialog.kt
│   ├── viewmodel
│   │   ├── CategoryListViewModel.kt
│   │   └── NoteListViewModel.kt
│   └── theme
│       ├── Color.kt
│       ├── Theme.kt
│       └── Type.kt
└── MainActivity.kt
```

---

## 8. 数据流架构说明

```
UI Layer (Compose Screen)
    │
    ▼  collectAsStateWithLifecycle()
ViewModel (StateFlow)
    │
    ▼ 调用挂起函数 / Flow
Repository
    │
    ▼ Room DAO
Room Database (SQLite)
```

- **单向数据流**：用户操作 → ViewModel 调用 Repository → Room 更新 → Flow 自动下发 → UI 刷新。
- **线程安全**：所有数据库操作均通过 Room 自动切换至后台线程；Compose 收集 Flow 在主线程安全。

---

## 9. 验收标准（Checklist）

- [ ] Gradle Sync 成功，App 能编译运行。
- [ ] 打开 App 能看到分组列表（首次进入可预置一个“默认”分组，或引导用户创建）。
- [ ] 能成功创建新分组（如“生活”、“想法”），并为其选择颜色。
- [ ] 能删除分组，删除后其下所有便签/Todo 级联消失（验证外键约束）。
- [ ] 进入分组后，能添加纯便签（标题+内容）。
- [ ] 能添加 Todo（标题+内容，带勾选框）。
- [ ] 勾选 Todo 后，界面即时反映完成状态（文字删除线 + 变灰）。
- [ ] 能删除单条便签/Todo。
- [ ] 前后台切换、屏幕旋转后数据不丢失，且自动恢复。
- [ ] （可选）在 Android Studio 的 Database Inspector 中能实时查看 `categories`、`notes` 表数据。

---

## 10. 风险与待决策项

| 问题 | 建议方案 | 需要用户确认 |
|------|---------|-----------|
| **预置数据** | 首次安装是否预置“默认”分组？还是完全空白让用户自建？ | 待确认 |
| **颜色选择** | 分组颜色是固定 6-8 个预设色板，还是允许用户自由输入 HEX？ | 待确认 |
| **编辑交互** | 便签编辑用「列表项内联编辑」还是「独立编辑页/BottomSheet」？ | 待确认 |
| **Todo 排序** | Todo 勾选后是否自动沉底，还是保持原有创建时间排序？ | 待确认 |

---

> **下一步动作**：请您 Review 以上阶段 1 详设，特别是第 10 节的待决策项。确认后，我将按照这份文档逐步写入实际代码。
