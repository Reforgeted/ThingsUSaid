package com.example.thingsusaid.widget.config

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.example.thingsusaid.data.AppDatabase
import com.example.thingsusaid.data.entity.WidgetConfig
import com.example.thingsusaid.ui.theme.ThingsUSaidTheme
import com.example.thingsusaid.widget.TodoWidgetUpdater
import kotlinx.coroutines.launch

class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setResult(RESULT_CANCELED)

        setContent {
            ThingsUSaidTheme {
                WidgetConfigScreen(
                    onCategorySelected = { categoryId ->
                        lifecycleScope.launch {
                            AppDatabase.getInstance(this@WidgetConfigActivity)
                                .appDao()
                                .saveWidgetConfig(
                                    WidgetConfig(
                                        appWidgetId = appWidgetId,
                                        boundCategoryId = categoryId
                                    )
                                )

                            TodoWidgetUpdater.updateAppWidgetIds(
                                context = this@WidgetConfigActivity,
                                appWidgetIds = intArrayOf(appWidgetId)
                            )

                            // 带 configure 的 widget 系统不会自动发 UPDATE 广播，保留广播作为兼容兜底
                            val intent = Intent(this@WidgetConfigActivity, com.example.thingsusaid.widget.TodoGlanceWidgetReceiver::class.java).apply {
                                action = com.example.thingsusaid.widget.TodoGlanceWidgetReceiver.ACTION_CUSTOM_UPDATE
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
                            }
                            sendBroadcast(intent)

                            val resultValue = Intent().apply {
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            }
                            setResult(RESULT_OK, resultValue)
                            finish()
                        }
                    }
                )
            }
        }
    }
}
