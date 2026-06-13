# 阶段 2：Glance 桌面小组件首航 —— 详细设计文档

> **阶段定位**：攻克桌面小组件核心技术。引入 Jetpack Glance，让小组件能成功拖到桌面并展示 App 内的便签/Todo 数据。  
> **交付标准**：桌面上能添加小组件，显示指定分组的便签/Todo 列表，且 App 内数据变更后小组件能自动刷新。  
> **暂不涉及**：多实例分组绑定（阶段 3）、小组件内直接勾选完成（阶段 4）。本阶段小组件暂时固定显示「欢迎」分组的内容，以验证 Glance 数据流与刷新机制。

---

## 1. 阶段目标与范围

| 模块 | 内容 | 说明 |
|------|------|------|
| **依赖引入** | Jetpack Glance | 声明式小组件框架，用类似 Compose 的语法写小组件 |
| **小组件定义** | `AppWidgetProviderInfo` XML + `GlanceAppWidgetReceiver` | 向系统注册小组件尺寸、预览图、更新周期等 |
| **小组件 UI** | `GlanceAppWidget` + 列表布局 | 显示便签标题、Todo 勾选状态（只读展示） |
| **数据刷新** | 数据库变更 → 主动触发 Glance 更新 | App 内增删改便签后，小组件自动刷新 |
| **交互** | 点击小组件打开 App | 点击小组件任意区域跳转回 App 主界面 |

---

## 2. 技术依赖与 Gradle 配置

### 2.1 新增依赖（`gradle/libs.versions.toml`）

```toml
[versions]
# ... 保留原有版本 ...
glance = "1.1.1"

[libraries]
# Glance
androidx-glance-appwidget = { group = "androidx.glance", name = "glance-appwidget", version.ref = "glance" }
androidx-glance-material3 = { group = "androidx.glance", name = "glance-material3", version.ref = "glance" }
```

### 2.2 `app/build.gradle.kts` 补充

```kotlin
dependencies {
    // ... 保留原有依赖 ...
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
}
```

> **版本说明**：Glance 1.1.1 是目前稳定的版本，兼容 Android 12+（API 31+）的完整功能。由于你的 `minSdk = 29`，Glance 在 API 29-30 上会以有限功能运行（无交互式列表），这是可接受的。

---

## 3. 小组件架构设计

### 3.1 核心类关系图

```
┌─────────────────────────────┐
│  TodoWidgetProviderInfo.xml │  ← 系统读取：尺寸、预览图、更新周期
└──────────────┬──────────────┘
               │
┌──────────────▼──────────────┐
│  TodoGlanceWidgetReceiver   │  ← 继承 GlanceAppWidgetReceiver
│  (系统广播接收器)            │     负责接收系统生命周期事件
└──────────────┬──────────────┘
               │
┌──────────────▼──────────────┐
│     TodoGlanceWidget        │  ← 继承 GlanceAppWidget
│   (小组件 UI 与数据逻辑)      │     提供 Content + 处理交互
└─────────────────────────────┘
```

### 3.2 数据流设计

```
App 内操作（新增/删除/修改便签）
    │
    ▼
Repository → Room 更新
    │
    ▼ 触发
TodoGlanceWidget.update(context)  // 主动请求刷新
    │
    ▼
系统调度 Glance 渲染
    │
    ▼
桌面小组件 UI 更新
```

- **主动刷新**：Glance 不像 Compose 能自动观察 Flow，必须在数据变更后手动调用 `GlanceAppWidgetManager` 请求更新。
- **刷新位置**：在 `AppRepository` 的数据变更方法之后统一触发。

---

## 4. 小组件详细实现

### 4.1 Provider Info XML

路径：`app/src/main/res/xml/todo_widget_info.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="180dp"
    android:minHeight="180dp"
    android:targetCellWidth="3"
    android:targetCellHeight="3"
    android:maxResizeWidth="300dp"
    android:maxResizeHeight="500dp"
    android:updatePeriodMillis="0"
    android:initialLayout="@layout/glance_default_loading_layout"
    android:previewImage="@drawable/ic_launcher_foreground"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen" />
```

> **关键参数**：
> - `updatePeriodMillis="0"`：禁用系统定时刷新，完全由 App 主动控制（避免耗电）。
> - `targetCellWidth/Height="3"`：默认占用 3x3 桌面格子，足够展示列表。
> - `resizeMode="horizontal|vertical"`：允许用户拖拽调整大小。

### 4.2 Receiver 定义

```kotlin
package com.example.thingsusaid.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class TodoGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodoGlanceWidget()
}
```

### 4.3 GlanceWidget 核心实现

```kotlin
package com.example.thingsusaid.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.thingsusaid.data.AppDatabase
import com.example.thingsusaid.data.entity.Note
import com.example.thingsusaid.ui.theme.PresetColors
import kotlinx.coroutines.runBlocking

class TodoGlanceWidget : GlanceAppWidget() {

    // 阶段 2 固定显示「欢迎」分组（categoryId = 1，预置数据首条）
    // 阶段 3 再改为从 widget_configs 表读取绑定关系
    private val fixedCategoryId = 1L

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val notes = loadNotes(context, fixedCategoryId)
        provideContent {
            WidgetContent(notes = notes, context = context)
        }
    }

    private fun loadNotes(context: Context, categoryId: Long): List<Note> {
        return runBlocking {
            AppDatabase.getInstance(context).appDao().getNotesByCategoryId(categoryId)
        }
    }
}

@Composable
private fun WidgetContent(notes: List<Note>, context: Context) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(androidx.glance.unit.ColorProvider(R.color.widget_background))
            .padding(16.dp)
    ) {
        Text(
            text = "Things U Said",
            style = TextStyle(
                color = ColorProvider(R.color.widget_title),
                fontSize = androidx.glance.unit.TextUnit(18f, androidx.glance.unit.TextUnitType.Sp)
            )
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        if (notes.isEmpty()) {
            Text(
                text = "暂无便签",
                style = TextStyle(color = ColorProvider(R.color.widget_text))
            )
        } else {
            notes.forEach { note ->
                NoteRow(note = note)
                Spacer(modifier = GlanceModifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun NoteRow(note: Note) {
    val alpha = if (note.isCompleted) 0.5f else 1f
    val titleText = if (note.isTodo) "☐ ${note.title}" else note.title
    Text(
        text = titleText,
        style = TextStyle(
            color = ColorProvider(R.color.widget_text),
            fontSize = androidx.glance.unit.TextUnit(14f, androidx.glance.unit.TextUnitType.Sp)
        ),
        modifier = GlanceModifier.alpha(alpha)
    )
}
```

> **注意**：Glance 的 API 与 Compose 有差异，`GlanceModifier`、`ColorProvider`、`TextUnit` 都来自 `androidx.glance` 包，不能混用 Compose 的 Modifier。上述代码为**设计示意**，实际编码时会根据 Glance 1.1.1 的确切 API 微调。

---

## 5. 主动刷新机制

### 5.1 封装 Widget 刷新入口

```kotlin
package com.example.thingsusaid.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TodoWidgetUpdater {
    suspend fun updateAll(context: Context) {
        withContext(Dispatchers.IO) {
            GlanceAppWidgetManager(context).getGlanceIds(TodoGlanceWidget::class.java)
                .forEach { glanceId ->
                    TodoGlanceWidget().update(context, glanceId)
                }
        }
    }
}
```

### 5.2 在 Repository 数据变更后触发

在 `AppRepository` 中，每次增删改便签/Todo 后，调用 `TodoWidgetUpdater.updateAll()`。

由于 Repository 目前还不是 `suspend` 上下文的全局刷新点，建议给 `AppRepository` 传入 `CoroutineScope` 或使用 `GlobalScope` 做轻量触发。阶段 2 暂用以下简化方案：

```kotlin
// AppRepository.kt
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class AppRepository(context: Context) {
    // ...
    private fun triggerWidgetUpdate() {
        GlobalScope.launch {
            TodoWidgetUpdater.updateAll(context)
        }
    }
}
```

> **阶段 3 优化**：后续会引入更精确的刷新策略（只刷新绑定了该分组的组件实例）。

---

## 6. 点击交互：打开 App

在 `WidgetContent` 中给根容器加上 `clickable`：

```kotlin
import androidx.glance.appwidget.action.actionStartActivity
import android.content.Intent
import com.example.thingsusaid.MainActivity

// 在 WidgetContent 的 Column modifier 中
modifier = GlanceModifier
    .fillMaxSize()
    .clickable(
        actionStartActivity(
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        )
    )
```

---

## 7. AndroidManifest 注册

必须在 `AndroidManifest.xml` 中注册 Receiver：

```xml
<receiver
    android:name=".widget.TodoGlanceWidgetReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/todo_widget_info" />
</receiver>
```

---

## 8. 验收标准（Checklist）

- [ ] 长按桌面 → 小组件列表中能找到 "Things U Said" 的小组件。
- [ ] 小组件能成功拖到桌面上，默认显示 3x3 大小。
- [ ] 小组件显示「欢迎」分组下的便签/Todo 列表（标题 + Todo 标识）。
- [ ] 当 App 内新增/删除/修改便签后，桌面小组件在 1-2 秒内自动刷新（无需手动等系统周期）。
- [ ] 点击小组件能打开 App 主界面。
- [ ] 拖拽调整小组件尺寸时，列表内容自适应（不出现截断或空白）。

---

## 9. 风险与待决策项

| 问题 | 建议方案 | 需要用户确认 |
|------|---------|-----------|
| **小组件默认显示内容** | 阶段 2 固定显示「欢迎」分组，阶段 3 再支持多实例绑定。是否接受？ | 待确认 |
| **列表滚动** | Glance 在 Android 12+ 支持 `LazyColumn` 效果，低版本自动降级为截断显示。是否只做静态截断列表（展示前 N 条）？ | 待确认 |
| **显示条数** | 如果做截断列表，最多展示几条？建议 5-8 条。 | 待确认 |
| **Todo 标记** | 小组件里用 "☐" 前缀标记 Todo，还是单独显示一个小图标？（Glance 图标资源有限） | 待确认 |

---

> **下一步动作**：请您 Review 以上阶段 2 详设，特别是第 9 节的待决策项。确认后，我将按此文档逐步写入实际代码。
