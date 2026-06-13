package com.example.thingsusaid.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "widget_configs")
data class WidgetConfig(
    @PrimaryKey
    val appWidgetId: Int,
    val boundCategoryId: Long
)
