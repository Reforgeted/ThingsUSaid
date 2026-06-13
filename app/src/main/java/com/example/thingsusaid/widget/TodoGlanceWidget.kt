package com.example.thingsusaid.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.thingsusaid.R
import com.example.thingsusaid.data.AppDatabase
import com.example.thingsusaid.data.dao.AppDao
import com.example.thingsusaid.data.entity.Category
import com.example.thingsusaid.data.entity.Note
import com.example.thingsusaid.data.entity.WidgetConfig
import com.example.thingsusaid.widget.action.ToggleTodoCallback
import kotlinx.coroutines.flow.flowOf

class TodoGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val dao = AppDatabase.getInstance(context).appDao()
        val initialConfig = dao.getWidgetConfig(appWidgetId)
        val initialCategory = initialConfig?.boundCategoryId?.let { dao.getCategoryById(it) }
        val initialNotes = initialCategory?.categoryId
            ?.let { dao.getNotesByCategoryId(it) }
            ?: emptyList()

        // 读取尺寸
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        val sizeCategory = when {
            minWidth <= 180 && minHeight <= 180 -> SizeCategory.MEDIUM
            else -> SizeCategory.LARGE
        }

        // 读取初始设置值
        val initialBgAlpha = dao.getSetting("widget_bg_alpha")?.value?.toIntOrNull() ?: 255
        val initialFontSizeIndex = dao.getSetting("widget_font_size")?.value?.toIntOrNull() ?: 1

        provideContent {
            WidgetContent(
                appWidgetId = appWidgetId,
                dao = dao,
                initialConfig = initialConfig,
                initialCategory = initialCategory,
                initialNotes = initialNotes,
                sizeCategory = sizeCategory,
                initialBgAlpha = initialBgAlpha,
                initialFontSizeIndex = initialFontSizeIndex
            )
        }
    }
}

private enum class SizeCategory {
    MEDIUM, LARGE
}

@Composable
private fun WidgetContent(
    appWidgetId: Int,
    dao: AppDao,
    initialConfig: WidgetConfig?,
    initialCategory: Category?,
    initialNotes: List<Note>,
    sizeCategory: SizeCategory,
    initialBgAlpha: Int,
    initialFontSizeIndex: Int
) {
    // 通过 Room Flow 监听设置变化，和分组数据用同样的机制
    val bgAlphaSetting by dao.getSettingFlow("widget_bg_alpha")
        .collectAsState(initial = null)
    val fontSizeSetting by dao.getSettingFlow("widget_font_size")
        .collectAsState(initial = null)

    val bgAlpha = bgAlphaSetting?.value?.toIntOrNull() ?: initialBgAlpha
    val fontSizeIndex = fontSizeSetting?.value?.toIntOrNull() ?: initialFontSizeIndex
    val fontSize = when (fontSizeIndex) {
        0 -> 12.sp
        2 -> 16.sp
        else -> 14.sp
    }

    val config by dao.getWidgetConfigFlow(appWidgetId).collectAsState(initial = initialConfig)

    val categoryFlow = remember(config?.boundCategoryId) {
        config?.boundCategoryId?.let { dao.getCategoryByIdFlow(it) }
            ?: flowOf<Category?>(null)
    }
    val categoryInitial = initialCategory.takeIf { it?.categoryId == config?.boundCategoryId }
    val category by categoryFlow.collectAsState(initial = categoryInitial)

    val notesFlow = remember(category?.categoryId) {
        category?.categoryId?.let { dao.getNotesByCategoryFlow(it) }
            ?: flowOf(emptyList())
        }
    val notesInitial = initialNotes.takeIf { initialCategory?.categoryId == category?.categoryId }
        ?: emptyList()
    val notes by notesFlow.collectAsState(initial = notesInitial)

    val hasValidConfig = config != null && category != null
    val categoryName = category?.name
    val title = categoryName ?: "Things U Said"

    // 点击头部：打开 App 定位到当前分组
    val headerClickAction = if (hasValidConfig) {
        actionStartActivity(
            Intent().apply {
                setClassName("com.example.thingsusaid", "com.example.thingsusaid.MainActivity")
                putExtra("category_id", category?.categoryId ?: -1L)
            }
        )
    } else {
        actionStartActivity(
            Intent().apply {
                setClassName("com.example.thingsusaid", "com.example.thingsusaid.widget.config.WidgetConfigActivity")
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
        )
    }

    // 点击 + 按钮：打开 MainActivity 携带新建意图
    val addAction = actionStartActivity(
        Intent().apply {
            setClassName("com.example.thingsusaid", "com.example.thingsusaid.MainActivity")
            putExtra("category_id", category?.categoryId ?: -1L)
            putExtra("action", "create_note")
        }
    )

    // 点击便签文本：打开 App
    val noteClickAction = if (hasValidConfig) {
        actionStartActivity(
            Intent().apply {
                setClassName("com.example.thingsusaid", "com.example.thingsusaid.MainActivity")
                putExtra("category_id", category?.categoryId ?: -1L)
            }
        )
    } else {
        headerClickAction
    }

    val maxNotes = when (sizeCategory) {
        SizeCategory.MEDIUM -> 2
        SizeCategory.LARGE -> 5
    }

    val baseColor = android.graphics.Color.parseColor("#FF1C1B1F")
    val alpha = bgAlpha.coerceIn(0, 255)
    val red = android.graphics.Color.red(baseColor)
    val green = android.graphics.Color.green(baseColor)
    val blue = android.graphics.Color.blue(baseColor)
    val bgColor = Color(red / 255f, green / 255f, blue / 255f, alpha / 255f)

    // 解析分组颜色用于指示条
    val categoryColor = try {
        category?.colorHex?.let { Color(android.graphics.Color.parseColor(it)) }
    } catch (_: Exception) {
        null
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(bgColor))
            .cornerRadius(24.dp)
            .padding(20.dp)
    ) {
        // 头部：左侧分组名称（加粗 18sp）+ 右侧 + 按钮
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(headerClickAction),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = TextStyle(
                    color = ColorProvider(R.color.widget_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.defaultWeight()
            )
            if (hasValidConfig) {
                Text(
                    text = "+",
                    style = TextStyle(
                        color = ColorProvider(R.color.widget_primary),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = GlanceModifier.clickable(addAction)
                )
            }
        }
        Spacer(modifier = GlanceModifier.height(10.dp))
        when {
            !hasValidConfig -> {
                Text(
                    text = if (categoryName == null) "点击选择分组" else "分组已删除，点击重新配置",
                    style = TextStyle(
                        color = ColorProvider(R.color.widget_text_secondary),
                        fontSize = fontSize
                    )
                )
            }
            notes.isEmpty() -> {
                Text(
                    text = "该分组暂无便签",
                    style = TextStyle(
                        color = ColorProvider(R.color.widget_text_secondary),
                        fontSize = fontSize
                    )
                )
            }
            else -> {
                notes.take(maxNotes).forEach { note ->
                    NoteRow(
                        note = note,
                        fontSize = fontSize,
                        categoryColor = categoryColor,
                        noteClickAction = noteClickAction
                    )
                    Spacer(modifier = GlanceModifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun NoteRow(
    note: Note,
    fontSize: androidx.compose.ui.unit.TextUnit,
    categoryColor: Color?,
    noteClickAction: androidx.glance.action.Action
) {
    if (note.isTodo) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 圆形选框：未选中 ○ / 选中 ◉
            Text(
                text = if (note.isCompleted) "◉" else "○",
                style = TextStyle(
                    color = ColorProvider(
                        if (note.isCompleted) R.color.widget_primary else R.color.widget_text_secondary
                    ),
                    fontSize = fontSize
                ),
                modifier = GlanceModifier.clickable(
                    actionRunCallback<ToggleTodoCallback>(
                        parameters = actionParametersOf(
                            ToggleTodoCallback.noteIdKey to note.noteId
                        )
                    )
                )
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            Text(
                text = note.title,
                style = TextStyle(
                    color = ColorProvider(
                        if (note.isCompleted) R.color.widget_text_secondary else R.color.widget_text
                    ),
                    fontSize = fontSize,
                    textDecoration = if (note.isCompleted) TextDecoration.LineThrough else null
                ),
                modifier = GlanceModifier
                    .defaultWeight()
                    .clickable(noteClickAction)
            )
        }
    } else {
        // 便签项：左侧彩色垂直指示条 + 文本
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(noteClickAction),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 彩色垂直指示条（用带背景色的 Spacer 近似实现）
            Spacer(
                modifier = GlanceModifier
                    .width(3.dp)
                    .height(16.dp)
                    .cornerRadius(1.5.dp)
                    .background(ColorProvider(categoryColor ?: Color(0xFFD0BCFF)))
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            Text(
                text = note.title,
                style = TextStyle(
                    color = ColorProvider(R.color.widget_text),
                    fontSize = fontSize
                ),
                modifier = GlanceModifier.defaultWeight()
            )
        }
    }
}
