package com.example.thingsusaid.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.example.thingsusaid.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TodoWidgetUpdater {

    suspend fun updateAll(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, TodoGlanceWidgetReceiver::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

                if (appWidgetIds.isNotEmpty()) {
                    updateAppWidgetIds(context, appWidgetIds)

                    val intent = Intent(context, TodoGlanceWidgetReceiver::class.java).apply {
                        action = TodoGlanceWidgetReceiver.ACTION_CUSTOM_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                    }
                    context.sendBroadcast(intent)
                    Log.d("TodoWidgetUpdater", "Custom broadcast sent to ${appWidgetIds.size} widget(s)")
                } else {
                    Log.d("TodoWidgetUpdater", "No widgets found to update")
                }
            } catch (e: Exception) {
                Log.e("TodoWidgetUpdater", "Failed to broadcast updateAll", e)
            }
        }
    }

    suspend fun updateForCategory(context: Context, categoryId: Long) {
        withContext(Dispatchers.IO) {
            try {
                val dao = AppDatabase.getInstance(context).appDao()
                val configs = dao.getWidgetConfigsByCategoryId(categoryId)
                val appWidgetIds = configs.map { it.appWidgetId }.toIntArray()

                if (appWidgetIds.isNotEmpty()) {
                    updateAppWidgetIds(context, appWidgetIds)

                    val intent = Intent(context, TodoGlanceWidgetReceiver::class.java).apply {
                        action = TodoGlanceWidgetReceiver.ACTION_CUSTOM_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                    }
                    context.sendBroadcast(intent)
                    Log.d("TodoWidgetUpdater", "Custom broadcast sent to ${appWidgetIds.size} widget(s) for category $categoryId")
                } else {
                    Log.d("TodoWidgetUpdater", "No widgets bound to category $categoryId")
                }
            } catch (e: Exception) {
                Log.e("TodoWidgetUpdater", "Failed to broadcast updateForCategory $categoryId", e)
            }
        }
    }

    suspend fun updateAppWidgetIds(context: Context, appWidgetIds: IntArray) {
        withContext(Dispatchers.IO) {
            val glanceManager = GlanceAppWidgetManager(context)
            val widget = TodoGlanceWidget()

            appWidgetIds.distinct().forEach { appWidgetId ->
                try {
                    val glanceId = glanceManager.getGlanceIdBy(appWidgetId)
                    widget.update(context, glanceId)
                    Log.d("TodoWidgetUpdater", "Glance widget $appWidgetId updated directly")
                } catch (e: Exception) {
                    Log.e("TodoWidgetUpdater", "Failed to directly update widget $appWidgetId", e)
                }
            }
        }
    }
}
