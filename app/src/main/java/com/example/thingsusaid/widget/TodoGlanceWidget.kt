package com.example.thingsusaid.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
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
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

        provideContent {
            WidgetContent(
                appWidgetId = appWidgetId,
                dao = dao,
                initialConfig = initialConfig,
                initialCategory = initialCategory,
                initialNotes = initialNotes
            )
        }
    }
}

@Composable
private fun WidgetContent(
    appWidgetId: Int,
    dao: AppDao,
    initialConfig: WidgetConfig?,
    initialCategory: Category?,
    initialNotes: List<Note>
) {
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
    val clickAction = if (hasValidConfig) {
        actionStartActivity(
            Intent().setClassName("com.example.thingsusaid", "com.example.thingsusaid.MainActivity")
        )
    } else {
        actionStartActivity(
            Intent().apply {
                setClassName("com.example.thingsusaid", "com.example.thingsusaid.widget.config.WidgetConfigActivity")
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
        )
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(R.color.widget_background))
            .clickable(clickAction)
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = TextStyle(
                color = ColorProvider(R.color.widget_title),
                fontSize = 18.sp
            )
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        when {
            !hasValidConfig -> {
                Text(
                    text = if (categoryName == null) "点击选择分组" else "分组已删除，点击重新配置",
                    style = TextStyle(
                        color = ColorProvider(R.color.widget_text_secondary),
                        fontSize = 14.sp
                    )
                )
            }
            notes.isEmpty() -> {
                Text(
                    text = "该分组暂无便签",
                    style = TextStyle(
                        color = ColorProvider(R.color.widget_text_secondary),
                        fontSize = 14.sp
                    )
                )
            }
            else -> {
                notes.take(5).forEach { note ->
                    NoteRow(note = note)
                    Spacer(modifier = GlanceModifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun NoteRow(note: Note) {
    if (note.isTodo) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (note.isCompleted) "☑" else "☐",
                style = TextStyle(
                    color = ColorProvider(R.color.widget_primary),
                    fontSize = 16.sp
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
                    fontSize = 14.sp
                ),
                modifier = GlanceModifier.defaultWeight()
            )
        }
    } else {
        Text(
            text = "• ${note.title}",
            style = TextStyle(
                color = ColorProvider(R.color.widget_text),
                fontSize = 14.sp
            )
        )
    }
}
