package com.example.thingsusaid

import android.app.Application
import com.example.thingsusaid.notification.NotificationHelper

class ThingsUSaidApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)
    }
}
