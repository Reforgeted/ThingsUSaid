package com.example.thingsusaid.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 折叠式内联时间选择器
 *
 * @param reminderTime 当前提醒时间戳（毫秒），null 表示未设置
 * @param onTimeChange 时间变化回调（确认选择时触发，非拖拽过程中）
 * @param modifier 修饰符
 */
@Composable
fun CollapsibleTimePicker(
    reminderTime: Long?,
    onTimeChange: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    // 初始化日历（仅用于计算初始索引）
    val initialCalendar = remember {
        Calendar.getInstance().apply {
            if (reminderTime != null) {
                timeInMillis = reminderTime
            } else {
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }
    }

    // 日期列表：今天起往后365天
    val dateItems = remember {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val items = mutableListOf<String>()
        for (i in 0 until 365) {
            val cal = today.clone() as Calendar
            cal.add(Calendar.DAY_OF_YEAR, i)
            items.add(formatDateLabel(cal, i))
        }
        items
    }

    // 小时列表
    val hourItems = remember {
        (0..23).map { String.format("%02d", it) }
    }

    // 分钟列表
    val minuteItems = remember {
        (0..59).map { String.format("%02d", it) }
    }

    // 本地选中索引（不用 reminderTime 作为 remember key，避免拖拽时重置）
    var selectedDateIndex by remember { mutableIntStateOf(calculateDateIndex(initialCalendar)) }
    var selectedHourIndex by remember { mutableIntStateOf(initialCalendar.get(Calendar.HOUR_OF_DAY)) }
    var selectedMinuteIndex by remember { mutableIntStateOf(initialCalendar.get(Calendar.MINUTE)) }

    // 当 reminderTime 被外部清除（变为null）时重置索引
    LaunchedEffect(reminderTime == null) {
        if (reminderTime == null) {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            selectedDateIndex = calculateDateIndex(cal)
            selectedHourIndex = cal.get(Calendar.HOUR_OF_DAY)
            selectedMinuteIndex = cal.get(Calendar.MINUTE)
        }
    }

    // 根据本地索引计算当前时间（用于实时显示）
    val currentTimeText = remember(selectedDateIndex, selectedHourIndex, selectedMinuteIndex) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        cal.add(Calendar.DAY_OF_YEAR, selectedDateIndex)
        cal.set(Calendar.HOUR_OF_DAY, selectedHourIndex)
        cal.set(Calendar.MINUTE, selectedMinuteIndex)
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(cal.time)
    }

    // 箭头旋转动画
    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "arrowRotation"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        // 触发行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "提醒时间",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isExpanded) currentTimeText
                           else (reminderTime?.let {
                               SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(it)
                           } ?: "未设置"),
                    color = if (reminderTime != null || isExpanded) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.rotate(arrowRotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 折叠面板
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
            exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 日期滚轮
                    WheelPicker(
                        items = dateItems,
                        selectedIndex = selectedDateIndex,
                        onSelectedChange = { newIndex ->
                            selectedDateIndex = newIndex
                        },
                        onConfirmed = { newIndex ->
                            selectedDateIndex = newIndex
                            onTimeChange(computeTimeFromIndices(newIndex, selectedHourIndex, selectedMinuteIndex))
                        },
                        modifier = Modifier.weight(1.5f),
                        visibleItemCount = 5,
                        itemHeight = 32f
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // 小时滚轮
                    WheelPicker(
                        items = hourItems,
                        selectedIndex = selectedHourIndex,
                        onSelectedChange = { newIndex ->
                            selectedHourIndex = newIndex
                        },
                        onConfirmed = { newIndex ->
                            selectedHourIndex = newIndex
                            onTimeChange(computeTimeFromIndices(selectedDateIndex, newIndex, selectedMinuteIndex))
                        },
                        modifier = Modifier.weight(1f),
                        visibleItemCount = 5,
                        itemHeight = 32f
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // 分钟滚轮
                    WheelPicker(
                        items = minuteItems,
                        selectedIndex = selectedMinuteIndex,
                        onSelectedChange = { newIndex ->
                            selectedMinuteIndex = newIndex
                        },
                        onConfirmed = { newIndex ->
                            selectedMinuteIndex = newIndex
                            onTimeChange(computeTimeFromIndices(selectedDateIndex, selectedHourIndex, newIndex))
                        },
                        modifier = Modifier.weight(1f),
                        visibleItemCount = 5,
                        itemHeight = 32f
                    )
                }
            }
        }
    }
}

private fun formatDateLabel(cal: Calendar, dayOffset: Int): String {
    val month = cal.get(Calendar.MONTH) + 1
    val day = cal.get(Calendar.DAY_OF_MONTH)
    val weekDay = when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.SUNDAY -> "周日"
        Calendar.MONDAY -> "周一"
        Calendar.TUESDAY -> "周二"
        Calendar.WEDNESDAY -> "周三"
        Calendar.THURSDAY -> "周四"
        Calendar.FRIDAY -> "周五"
        Calendar.SATURDAY -> "周六"
        else -> ""
    }
    return when (dayOffset) {
        0 -> "今天"
        1 -> "明天"
        else -> "${month}月${day}日$weekDay"
    }
}

private fun calculateDateIndex(calendar: Calendar): Int {
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val target = (calendar.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val diffMs = target.timeInMillis - today.timeInMillis
    val diffDays = (diffMs / (24 * 60 * 60 * 1000)).toInt()
    return diffDays.coerceIn(0, 364)
}

private fun computeTimeFromIndices(dateIndex: Int, hourIndex: Int, minuteIndex: Int): Long {
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    cal.add(Calendar.DAY_OF_YEAR, dateIndex)
    cal.set(Calendar.HOUR_OF_DAY, hourIndex)
    cal.set(Calendar.MINUTE, minuteIndex)
    return cal.timeInMillis
}
