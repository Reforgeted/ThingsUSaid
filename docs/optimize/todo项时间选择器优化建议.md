# 详细设计说明书：折叠式内联时间选择器 (Collapsible Inline Time Picker)

## 1. 业务场景与设计理念

- **业务背景：** 用户在创建或编辑待办事项（Todo）时，需要为其设置精确的提醒日期和时间。
- **现有痛点：** 原生弹窗（DatePicker/TimePicker）路径长、割裂感强，两次弹窗打断了用户的连续输入体验，视觉风格偏向系统底层，缺乏现代 App 的精致感。
- **设计理念：** **“零打断，无缝推开”**。放弃传统弹窗模式，将自定义日期与时间选择器一体化并内联（Inline）集成于当前编辑页中。通过点击平滑展开/收起，让用户在同一个视觉层级内完成高精度的自定义时间配置。

## 2. 交互逻辑 (Interaction Flow)

```
[默认状态]
📅 提醒时间                       2026-06-13 09:00 🔽
--------------------------------------------------

   ↓ 点击该行触发

[展开状态]
📅 提醒时间                       2026-06-13 09:00 🔼
+------------------------------------------------+
|       6月11日周四         07          58       |
|       6月12日周五         08          59       |
|   >   6月13日周六   < >   09   < >    00   <   |
|       6月14日周日         10          01       |
+------------------------------------------------+
--------------------------------------------------
```

1. **默认状态（收起）：** * “提醒时间”整行作为可点击区域（Clickable）。
   - 右侧实时文本显示当前选中的时间（若未设置，则置灰显示“未设置”），末尾带有下箭头图标（`🔽`）。
2. **触发展开：** * 用户点击该行，下箭头图标平滑旋转 180° 变为上箭头（`🔼`）。
   - 正下方通过物理位移，平滑向下推开（Expand）自定义时间滚轮面板。下方原有的“保存”按钮等元素被平滑向下推移，不产生视觉遮挡。
3. **自主定义（无感确认）：** * 面板包含三列独立滚轮：**[日期（含月/日/星期）]**、**[小时]**、**[分钟]**。
   - 用户上下拨动任意滚轮，上方对应的状态文本**实时更新**。整个过程无需点击“确定”或“取消”按钮。
4. **收起状态：** * 再次点击该行，或点击页面其他空白区域/点击“保存”时，面板平滑向上收起（Shrink），箭头恢复原状。

## 3. 视觉规格 (Visual Specs)

### 3.1 触发条整行 (Trigger Row)

- **高度 (Height):** `56dp`
- **内边距 (Padding):** 水平左右各 `16dp`
- **字体规格 (Typography):** * 标题（“提醒时间”）：`16sp`，粗细 `Normal`，颜色 `#1C1B1F` (Material On-Surface)
  - 时间文本：`14sp`，粗细 `Medium`，颜色使用主题色（例如 `#00668B`）以强化视觉焦点。

### 3.2 自定义滚轮面板 (Picker Card)

- **外边距 (Margin):** 上边距 `8dp`，左右保持与父容器对齐。
- **背景色 (Background):** 采用比主背景略深/略浅的卡片色（如 `MaterialTheme.colorScheme.surfaceVariant` 或 `#F2F4F7`），用以营造纵深和层级感。
- **圆角 (Corner Radius):** `12dp`
- **总高度 (Total Height):** `160dp`

### 3.3 滚轮细节 (Wheel Columns)

- **列宽占比 (Weight):** [日期滚轮] 占比 `1.5f`，[小时滚轮] 占比 `1.0f`，[分钟滚轮] 占比 `1.0f`。
- **高亮视觉 (Selection Highlight):** * 中心选中项：字体放大至 `16sp`，粗细 `Bold`，不透明度 `alpha = 1.0f`。可在中心项上下增加两条极细的水平分割线（颜色 `#E1E2E5`，粗细 `1dp`）用于视觉视觉对齐。
  - 非选中项（上下两侧）：字体 `14sp`，不透明度做渐隐处理（邻近项 `alpha = 0.5f`，边缘项 `alpha = 0.2f`）。

## 4. 动效规格 (Animation Specs)

高级感的核心来源于不生硬的物理动效。在 Jetpack Compose 中基于以下参数配置：

- **面板展开/收起动效 (Expand/Shrink Animation):**
  - 采用弹性/缓动曲线：`FastOutSlowInEasing`
  - 持续时间 (Duration): `300ms`
  - Compose 实现机制：利用 `AnimatedVisibility` 组合 `expandVertically()` + `fadeIn()` 以及 `shrinkVertically()` + `fadeOut()`。
- **箭头旋转动效 (Arrow Rotation):**
  - 角度变化：`0f` ↔ `180f`
  - 插值器：与面板展开同步，使用 `animateFloatAsState(targetValue, tween(300, easing = FastOutSlowInEasing))`。

## 5. 数据定义与业务逻辑 (Data & Logic)

### 5.1 状态持有 (State Management)

在 Compose 组件状态中，不推荐分离日期和时间，应统一使用 Java 8 的 `LocalDateTime` 进行单一状态流驱动：

Kotlin

```
var isExpanded by remember { mutableStateOf(false) }
var selectedDateTime by remember { mutableStateOf(LocalDateTime.now()) }
```

### 5.2 滚轮数据源映射

- **日期列：** 动态生成从`今日`开始往后延的 365 天数据列表。列表项格式化为：`6月13日 周六`。特殊处理：前两项可直接显示为 `今天`、`明天`。
- **小时列：** 固定 `00` 至 `23` 的字符串数组。
- **分钟列：** 固定 `00` 至 `59` 的字符串数组。（若追求更高效率，可采用步长为 5 的数组，如 `00, 05, 10...`，根据业务决定）。

## 6. Jetpack Compose 核心结构落地代码

你可以直接使用以下经过精简和动效调优的 Compose 代码架构，替换掉你原有的“选择日期”和“选择时间”按钮区域：

Kotlin

```
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun CollapsibleTimePickerModifier() {
    var isExpanded by remember { mutableStateOf(false) }
    var selectedDateTime by remember { mutableStateOf(LocalDateTime.now()) }
    
    // 箭头随展开状态旋转 180 度的平滑过度动效
    val arrowRotationDegree by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 300)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // 1. 触发整行布局
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded } // 点击整行平滑展开或收起
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
                    text = selectedDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                    color = MaterialTheme.colorScheme.primary, // 高亮文字颜色
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.rotate(arrowRotationDegree), // 应用旋转动效
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 2. 内联折叠面板主体
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
            exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) // 轻微深色底衬托层级
            ) {
                // 滚轮容器区域
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // TODO: 在此处嵌入具体的 3 列滚轮 UI 组件 (Date, Hour, Minute)
                    // 当滚轮滑动停止后，直接回调 invoke 更新 selectedDateTime 即可，上层 UI 会自动重绘。
                    Text("【日期滚轮】", modifier = Modifier.weight(1.5f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("【时】", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("【分】", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
```