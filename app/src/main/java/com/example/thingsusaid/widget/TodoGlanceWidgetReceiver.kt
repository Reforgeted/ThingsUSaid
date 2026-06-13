package com.example.thingsusaid.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.example.thingsusaid.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TodoGlanceWidgetReceiver : GlanceAppWidgetReceiver() {

    companion object {
        const val ACTION_CUSTOM_UPDATE = "com.example.thingsusaid.action.WIDGET_UPDATE"
    }

    override val glanceAppWidget: GlanceAppWidget = TodoGlanceWidget()

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d("TodoWidgetReceiver", "onUpdate called for ${appWidgetIds.contentToString()}")
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("TodoWidgetReceiver", "onReceive: action=${intent.action}, extras=${intent.extras?.keySet()}")
        when (intent.action) {
            ACTION_CUSTOM_UPDATE -> {
                val appWidgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                if (appWidgetIds != null) {
                    onUpdate(context, AppWidgetManager.getInstance(context), appWidgetIds)
                }
            }
            else -> super.onReceive(context, intent)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        CoroutineScope(Dispatchers.IO).launch {
            val dao = AppDatabase.getInstance(context).appDao()
            appWidgetIds.forEach { id ->
                dao.deleteWidgetConfig(id)
            }
        }
    }
}
