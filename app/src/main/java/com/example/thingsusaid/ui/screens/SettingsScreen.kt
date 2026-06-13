package com.example.thingsusaid.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.thingsusaid.data.AppDatabase
import com.example.thingsusaid.data.entity.AppSetting
import com.example.thingsusaid.widget.TodoWidgetUpdater
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = AppDatabase.getInstance(context).appDao()

    // 通过 Room Flow 读取设置，自动实时更新
    val snoozeSetting by dao.getSettingFlow("snooze_interval")
        .collectAsState(initial = null)
    val bgAlphaSetting by dao.getSettingFlow("widget_bg_alpha")
        .collectAsState(initial = null)
    val fontSizeSetting by dao.getSettingFlow("widget_font_size")
        .collectAsState(initial = null)

    var snoozeInterval by remember { mutableIntStateOf(5) }
    var widgetAlpha by remember { mutableIntStateOf(255) }
    var widgetFontSize by remember { mutableIntStateOf(1) }

    // Flow 值变化时同步到本地状态
    LaunchedEffect(snoozeSetting) { snoozeInterval = snoozeSetting?.value?.toIntOrNull() ?: 5 }
    LaunchedEffect(bgAlphaSetting) { widgetAlpha = bgAlphaSetting?.value?.toIntOrNull() ?: 255 }
    LaunchedEffect(fontSizeSetting) { widgetFontSize = fontSizeSetting?.value?.toIntOrNull() ?: 1 }

    val fontSizeLabels = listOf("小", "中", "大")
    val snoozeOptions = listOf(5, 10, 15, 30)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text("稍后提醒", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("通知点击「稍后提醒」后，默认间隔多久再次提醒", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                snoozeOptions.forEachIndexed { index, minutes ->
                    SegmentedButton(
                        selected = snoozeInterval == minutes,
                        onClick = {
                            snoozeInterval = minutes
                            scope.launch {
                                dao.saveSetting(AppSetting("snooze_interval", minutes.toString()))
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = snoozeOptions.size
                        )
                    ) {
                        Text("${minutes}分钟")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            Text("小组件设置", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))

            Text("背景透明度: ${(widgetAlpha * 100 / 255)}%", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = widgetAlpha.toFloat(),
                onValueChange = {
                    widgetAlpha = it.toInt()
                    scope.launch {
                        dao.saveSetting(AppSetting("widget_bg_alpha", widgetAlpha.toString()))
                    }
                },
                valueRange = 0f..255f,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text("字体大小", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                fontSizeLabels.forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = widgetFontSize == index,
                        onClick = {
                            widgetFontSize = index
                            scope.launch {
                                dao.saveSetting(AppSetting("widget_font_size", index.toString()))
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = fontSizeLabels.size
                        )
                    ) {
                        Text(label)
                    }
                }
            }
        }
    }
}
