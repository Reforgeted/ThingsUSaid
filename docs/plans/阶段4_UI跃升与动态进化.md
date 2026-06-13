# 阶段 4：UI 跃升与动态进化（详细设计）

## 1. 阶段目标与范围

### 核心目标
1. **Material You 动态配色**：App 界面与小组件跟随系统壁纸自动变色（Android 12+）。
2. **小组件快捷操作**：在小组件内直接勾选/取消勾选 Todo，无需打开 App。

### 范围边界
- 本阶段只改造 **App 主题配色** 和 **小组件交互**，不动数据库结构和核心逻辑。
- 鸿蒙系统上小组件刷新存在已知延迟（阶段 3 已确认），快捷操作勾选后数据会立即更新，但 UI 刷新可能延迟（打开 App 再退出后一定刷新）。

---

## 2. 技术依赖

| 依赖 | 用途 |
|------|------|
| `androidx.compose.material3:material3:1.2.x` | App 内 Material You 动态配色 |
| `androidx.glance:glance-appwidget:1.1.1` | 小组件内动态配色（`dynamicThemeColorProviders`） |

---

## 3. Material You 动态配色设计

### 3.1 App 内配色方案

使用 Material3 的 `dynamicDarkColorScheme` / `dynamicLightColorScheme`：

```kotlin
@Composable
fun ThingsUSaidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true, // Android 12+ 启用动态配色
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
```

- **Android 12+（API 31+）**：自动提取系统壁纸主色调，生成完整配色方案。
- **低版本（< API 31）**：回退到预设的静态配色方案（基于阶段 2 的 Material Theme Builder 配色或当前配色）。

### 3.2 小组件配色方案

Glance 提供 `dynamicThemeColorProviders()` 函数，在 Android 12+ 上自动跟随系统：

```kotlin
import androidx.glance.material3.ColorProviders
import androidx.glance.material3.dynamicThemeColorProviders

val widgetColors: ColorProviders = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    dynamicThemeColorProviders()
} else {
    // 低版本回退：使用与 App 一致的静态配色
    ColorProviders(
        primary = ColorProvider(R.color.widget_primary),
        onPrimary = ColorProvider(R.color.widget_on_primary),
        // ... 其他色槽
    )
}
```

小组件整体使用 `ColorProviders` 替代当前的硬编码颜色：

```kotlin
Column(
    modifier = GlanceModifier
        .fillMaxSize()
        .background(widgetColors.background)
        .clickable(clickAction)
        .padding(16.dp)
) {
    Text(
        text = title,
        style = TextStyle(
            color = widgetColors.onBackground,
            fontSize = 18.sp
        )
    )
    // ...
}
```

---

## 4. 小组件内快捷操作设计

### 4.1 交互方案

在小组件内，**每条 Todo 前显示一个勾选框**（纯便签不显示勾选框）。点击勾选框后：
1. 触发 `ActionCallback`，更新数据库中该 Todo 的 `isCompleted` 状态
2. 发送自定义广播刷新该小组件

```kotlin
// Glance ActionCallback
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
        
        // 精确刷新绑定了该分组的组件
        TodoWidgetUpdater.updateForCategory(context, note.categoryId)
    }

    companion object {
        val noteIdKey = ActionParameters.Key<Long>("note_id")
    }
}
```

### 4.2 小组件布局调整

当前每条记录独占一行，需要为 Todo 增加勾选区域：

```
[☐] 买牛奶          ← Todo，左侧 Checkbox 可点击
    读书笔记        ← 纯便签，无 Checkbox
[☑] 还书           ← 已完成 Todo，Checkbox 为勾选状态
```

**点击区域分配**：
- **勾选框区域**：点击触发 `ToggleTodoCallback`，切换完成状态
- **文字区域 / 整行（非勾选框区域）**：保持现有行为，点击打开 App（或打开配置页）

### 4.3 视觉状态

| 状态 | 视觉表现 |
|------|---------|
| Todo 未完成 | `[☐]` 前缀 + 正常文字颜色 |
| Todo 已完成 | `[☑]` 前缀 + 次要文字颜色（或删除线） |
| 纯便签 | 无 Checkbox 前缀 |

---

## 5. 数据层调整

无需新增表或字段。`AppDao` 中已有 `updateNoteCompletion`，直接复用。

`TodoWidgetUpdater.updateForCategory` 已在阶段 3 实现，直接复用。

---

## 6. 关键实现细节

### 6.1 App 主题改造

当前 `Theme.kt` 使用硬编码的 `LightColorScheme` / `DarkColorScheme`，需要扩展为动态配色：

1. 保留现有静态配色作为低版本 fallback
2. 在 `ThingsUSaidTheme` 中增加 `dynamicColor` 判断逻辑
3. 所有 Screen 继续使用 `MaterialTheme.colorScheme.xxx`，无需改动

### 6.2 小组件 Checkbox 实现

Glance 1.1.1 提供了 `androidx.glance.Checkbox` 组件：

```kotlin
import androidx.glance.Checkbox
import androidx.glance.text.Text
import androidx.glance.layout.Row

Row {
    Checkbox(
        checked = note.isCompleted,
        onCheckedChange = actionRunCallback<ToggleTodoCallback>(
            parameters = actionParametersOf(ToggleTodoCallback.noteIdKey to note.noteId)
        )
    )
    Text(text = note.title, style = ...)
}
```

注意：`Checkbox` 只在 Glance 较新版本中可用，需确认 `androidx.glance:glance:1.1.1` 是否包含。如果不包含，改用 `Image` + `actionRunCallback` 模拟勾选框（用两个 drawable：`ic_checkbox_unchecked` / `ic_checkbox_checked`）。

### 6.3 动态配色资源引入

需要新增 Glance Material3 依赖：

```toml
[versions]
glance = "1.1.1"

[libraries]
glance-appwidget = { group = "androidx.glance", name = "glance-appwidget", version.ref = "glance" }
glance-material3 = { group = "androidx.glance", name = "glance-material3", version.ref = "glance" }
```

并在 `build.gradle.kts` 中引入 `glance-material3`。

---

## 7. 验收标准

| 验收项 | 通过标准 |
|--------|---------|
| Material You 动态配色 | Android 12+ 真机上，切换系统壁纸后打开 App，App 主题色跟随变化 |
| 低版本兼容 | Android 11 及以下设备上，App 使用预设静态配色，不崩溃 |
| 小组件动态配色 | Android 12+ 上，小组件背景/文字色跟随系统壁纸变化 |
| 小组件快捷勾选 | 小组件内点击 Todo 的 Checkbox，数据被正确切换；打开 App 后能看到状态变化 |
| 纯便签无 Checkbox | 小组件内纯便签不显示 Checkbox，只显示文字 |

---

## 8. 待决策项（请用户确认）

### 问题 1：低版本配色 Fallback
当前 App 的静态配色是蓝紫色调。低版本（Android 11 及以下）是否继续使用现有配色，还是换一套更中性的配色？

**建议**：保留现有配色作为 fallback，因为 Material You 只在 Android 12+ 生效，低版本用户占比会越来越低。

### 问题 2：小组件已完成 Todo 的显示方式
已完成 Todo 在小组件内是只改变 Checkbox 状态（从 `☐` 变 `☑`），还是同时加删除线（`~~已完成事项~~`）？

**建议**：只变 Checkbox 状态 + 文字颜色变淡（次要色），不加删除线。删除线在小组件小字号下可读性差。

### 问题 3：小组件内纯便签是否也要显示某种前缀标记？
为了视觉上区分 Todo 和纯便签，纯便签是否用某种图标（如 `📝` 或 `•`）前缀？

**建议**：纯便签不加任何前缀，只显示标题文字。Todo 用 Checkbox 本身就形成了足够区分度。

### 问题 4：动态配色是否应用到分组颜色选择器？
当前新建分组时可以从 8 种预设颜色中选择。Material You 引入后，这 8 种颜色是否也要从系统动态色板中提取？

**建议**：分组预设颜色保持独立（用户手动选择），不受系统动态配色影响。这样用户可以为分组设置固定颜色，不受换壁纸影响。App 的"主题框架色"跟随系统，但"分组内容色"由用户自主决定。
