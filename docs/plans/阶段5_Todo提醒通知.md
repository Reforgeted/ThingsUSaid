# 阶段 5：Todo 提醒通知（详细设计）

## 1. 阶段目标与范围

### 核心目标
为 Todo 事项增加**截止日期**和**提醒时间**功能，到达设定时间后发送系统通知提醒用户。

### 范围边界
- 只针对 `isTodo = true` 的 Note 增加提醒能力，纯便签不支持提醒。
- 提醒通过 Android 系统通知栏推送，点击通知可打开 App 并跳转到对应分组。
- 本阶段不涉及重复提醒（如每天提醒），只支持一次性提醒。

---

## 2. 技术依赖

| 依赖 | 用途 |
|------|------|
| `androidx.work:work-runtime-ktx:2.9.0` | WorkManager 定时任务，用于触发提醒通知 |

---

## 3. 数据库架构调整

### 3.1 Note 表新增字段

在现有 `Note` 实体上增加两个字段（Room 自动迁移）：

```kotlin
@Entity(tableName = "notes", /* ... 现有外键和索引 ... */)
data class Note(
    @PrimaryKey(autoGenerate = true) val noteId: Long = 0,
    val categoryId: Long,
    val title: String,
    val content: String,
    val isTodo: Boolean = false,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    // 新增字段
    val dueDate: Long? = null,      // 截止日期时间戳（可选）
    val reminderTime: Long? = null  // 提醒时间时间戳（可选）
)
```

### 3.2 数据库版本迁移

Room 数据库版本从 `1` 升级到 `2`，使用自动迁移：

```kotlin
@Database(
    entities = [Category::class, Note::class, WidgetConfig::class],
    version = 2,
    autoMigrations = [
        AutoMigration(from = 1, to = 2)
    ]
)
```

---

## 4. 通知系统设计

### 4.1 通知渠道（Notification Channel）

在 `Application.onCreate()` 中创建通知渠道：

```kotlin
val channel = NotificationChannel(
    "todo_reminder_channel",
    "Todo 提醒",
    NotificationManager.IMPORTANCE_HIGH
).apply {
    description = "Todo 事项到期提醒"
}
notificationManager.createNotificationChannel(channel)
```

### 4.2 提醒触发机制

使用 **WorkManager** 的 `OneTimeWorkRequest` 进行精确延时触发：

```kotlin
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val noteId = inputData.getLong("note_id", -1)
        val note = AppDatabase.getInstance(applicationContext).appDao().getNoteById(noteId) ?: return Result.success()
        
        // 发送通知
        NotificationHelper.showReminderNotification(applicationContext, note)
        
        // 标记提醒已触发（可选：清空 reminderTime）
        return Result.success()
    }
}
```

创建 Worker 时计算延时：

```kotlin
fun scheduleReminder(context: Context, noteId: Long, reminderTime: Long) {
    val delay = reminderTime - System.currentTimeMillis()
    if (delay <= 0) return // 时间已过，不调度
    
    val workRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
        .setInputData(workDataOf("note_id" to noteId))
        .build()
    
    WorkManager.getInstance(context).enqueueUniqueWork(
        "reminder_$noteId",
        ExistingWorkPolicy.REPLACE,
        workRequest
    )
}
```

**为什么不用 AlarmManager？**
- WorkManager 更可靠，系统会考虑 Doze 模式和 App Standby
- 对于"分钟级精度足够"的 Todo 提醒，WorkManager 是推荐方案
- 如果用户需要秒级精确提醒（如闹钟），才需要用 AlarmManager + `SCHEDULE_EXACT_ALARM` 权限

### 4.3 取消提醒

当 Todo 被删除、完成或修改提醒时间时，取消对应 Worker：

```kotlin
WorkManager.getInstance(context).cancelUniqueWork("reminder_$noteId")
```

---

## 5. UI 层设计

### 5.1 编辑 BottomSheet 增加时间选择

在 `NoteEditBottomSheet` 中增加：

```
[标题输入框]
[内容输入框]
[☑ 设为 Todo]
[提醒时间: 2026-06-15 09:00]  ← 新增，点击弹出日期时间选择器
[操作按钮区：删除 | 取消 | 保存]
```

使用 Material3 的 `DatePicker` + `TimePicker` 组合选择日期和时间。

### 5.2 便签列表显示提醒标识

在 `NoteItem` 中，如果 Todo 有 `dueDate`，显示一个小时钟图标和格式化后的日期：

```
☐ 买牛奶                    📅 6月15日
```

---

## 6. 关键实现细节

### 6.1 权限申请

Android 13+ (API 33) 需要动态申请 `POST_NOTIFICATIONS` 权限：

```kotlin
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

在 App 首次启动或用户首次创建带提醒的 Todo 时申请。

### 6.2 通知点击行为

点击通知后打开 App，并跳转到该 Todo 所在的分组列表页：

```kotlin
val intent = Intent(context, MainActivity::class.java).apply {
    putExtra("category_id", note.categoryId)
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
}
```

### 6.3 设备重启后恢复提醒

使用 `BroadcastReceiver` 监听 `BOOT_COMPLETED`，在设备重启后重新调度所有未触发的提醒：

```kotlin
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // 查询所有 reminderTime > currentTime 的 Note
            // 重新调度 Worker
        }
    }
}
```

---

## 7. 验收标准

| 验收项 | 通过标准 |
|--------|---------|
| 创建带提醒的 Todo | 在编辑页中选择日期时间，保存后数据库正确记录 |
| 到期提醒 | 到达设定时间后，系统通知栏弹出提醒通知 |
| 通知点击跳转 | 点击通知打开 App 并进入对应分组 |
| 删除/完成 Todo | 对应提醒 Worker 被取消，不再弹出通知 |
| 设备重启 | 未触发的提醒在重启后仍然有效 |
| 权限处理 | Android 13+ 设备首次使用时正确申请通知权限 |

---

## 8. 待决策项（请用户确认）

### 问题 1：提醒精度
Todo 提醒是精确到**分钟**（如 09:00）还是精确到**具体分钟**（如 09:23）？

**用户决策**：精确到具体分钟（日期 + 小时 + 分钟）。UI 上提供 DatePicker + TimePicker。

### 问题 2：是否显示截止日期（dueDate）和提醒时间（reminderTime）两个独立字段？
- 方案 A：`dueDate` 和 `reminderTime` 独立。用户可以设截止时间为 6月15日，但提醒时间为 6月14日晚上。
- 方案 B：只有一个 `reminderTime`，`dueDate` 只是 UI 上的显示概念。

**用户决策**：方案 A，两个字段都保留。`dueDate` 用于 UI 显示截止日期，`reminderTime` 用于实际触发提醒。

### 问题 3：已完成 Todo 的提醒处理
当用户勾选完成 Todo 后，如果该 Todo 有未到期的提醒，是否：
- A. 自动取消提醒
- B. 提醒仍然保留（用户可能想再打开）

**用户决策**：A，自动取消。已完成的 Todo 不再需要提醒。

### 问题 4：过期提醒的默认行为
如果用户设置了一个过去的提醒时间（如 1 小时前），保存时：
- A. 直接忽略，不创建 Worker
- B. 弹出提示"提醒时间已过，是否立即提醒？"

**用户决策**：A，直接忽略不创建 Worker。过去的提醒没有意义。

### 补充需求：通知支持"稍后再提醒"

用户提出：通知弹出时支持"稍后再提醒"操作，默认 **5 分钟后**再次提醒。用户可以在 App 设置中修改"稍后提醒"的默认间隔（如 5 分钟、10 分钟、15 分钟）。

**实现方案**：
1. 点击通知本身即打开 App 并跳转到对应 Todo 条目所在的分组。
2. 通知上添加一个 Action 按钮：「稍后提醒」（圆润样式）。
3. 点击「稍后提醒」后，取消当前 Worker，创建一个新的 `OneTimeWorkRequest`，延时为用户设置的间隔（默认 5 分钟）。
4. App 中新增「设置」页面，包含「稍后提醒间隔」选项（Spinner：5/10/15/30 分钟）。
5. 设置值使用 `SharedPreferences` 持久化。
