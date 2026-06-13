package com.example.thingsusaid.util

import android.content.Context
import com.example.thingsusaid.data.AppDatabase
import com.example.thingsusaid.data.entity.AppSetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SettingsPrefs {

    private const val KEY_SNOOZE_INTERVAL = "snooze_interval"
    private const val KEY_WIDGET_BG_ALPHA = "widget_bg_alpha"
    private const val KEY_WIDGET_FONT_SIZE = "widget_font_size"

    private fun dao(context: Context) = AppDatabase.getInstance(context).appDao()

    // 稍后提醒间隔（分钟）
    suspend fun getSnoozeInterval(context: Context): Int {
        val setting = dao(context).getSetting(KEY_SNOOZE_INTERVAL)
        return setting?.value?.toIntOrNull() ?: 5
    }

    suspend fun setSnoozeInterval(context: Context, minutes: Int) {
        dao(context).saveSetting(AppSetting(KEY_SNOOZE_INTERVAL, minutes.toString()))
    }

    // 小组件背景透明度（0-255）
    suspend fun getWidgetBgAlpha(context: Context): Int {
        val setting = dao(context).getSetting(KEY_WIDGET_BG_ALPHA)
        return setting?.value?.toIntOrNull() ?: 255
    }

    suspend fun setWidgetBgAlpha(context: Context, alpha: Int) {
        dao(context).saveSetting(AppSetting(KEY_WIDGET_BG_ALPHA, alpha.toString()))
    }

    // 小组件字体大小（0=小, 1=中, 2=大）
    suspend fun getWidgetFontSize(context: Context): Int {
        val setting = dao(context).getSetting(KEY_WIDGET_FONT_SIZE)
        return setting?.value?.toIntOrNull() ?: 1
    }

    suspend fun setWidgetFontSize(context: Context, size: Int) {
        dao(context).saveSetting(AppSetting(KEY_WIDGET_FONT_SIZE, size.toString()))
    }

    // 同步版本（用于 provideGlance 等协程上下文）
    suspend fun getWidgetBgAlphaSuspend(context: Context): Int = withContext(Dispatchers.IO) {
        getWidgetBgAlpha(context)
    }

    suspend fun getWidgetFontSizeSuspend(context: Context): Int = withContext(Dispatchers.IO) {
        getWidgetFontSize(context)
    }
}
