package com.example.thingsusaid.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 单列滚轮选择器（支持惯性滚动）
 *
 * @param items 数据列表
 * @param selectedIndex 当前选中索引
 * @param onSelectedChange 选中项变化回调（拖拽/惯性滚动过程中实时触发）
 * @param onConfirmed 选择确认回调（拖拽结束或点击时触发）
 * @param modifier 修饰符
 * @param visibleItemCount 可见条目数（默认5，奇数）
 * @param itemHeight 每条高度 dp
 */
@Composable
fun WheelPicker(
    items: List<String>,
    selectedIndex: Int,
    onSelectedChange: (Int) -> Unit,
    onConfirmed: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    visibleItemCount: Int = 5,
    itemHeight: Float = 36f
) {
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeight.dp.toPx() }
    val halfVisible = visibleItemCount / 2
    val coroutineScope = rememberCoroutineScope()

    // 用 Animatable 跟踪连续滚动位置（单位：item索引，可以是小数）
    val scrollPosition = remember {
        Animatable(
            initialValue = selectedIndex.toFloat(),
            visibilityThreshold = 0.01f
        )
    }
    // 设置边界，animateDecay 到达边界时会自动停止
    LaunchedEffect(items.lastIndex) {
        scrollPosition.updateBounds(0f, items.lastIndex.toFloat())
    }
    var isDragging by remember { mutableStateOf(false) }

    // 外部 selectedIndex 变化时同步（非拖拽/非惯性滚动状态下）
    LaunchedEffect(selectedIndex) {
        if (!isDragging && !scrollPosition.isRunning) {
            scrollPosition.snapTo(selectedIndex.toFloat())
        }
    }

    val selectedStyle = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
    val unselectedStyle = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val dividerColor = Color(0xFFE1E2E5)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(with(density) { (itemHeightPx * visibleItemCount).toDp() })
            .pointerInput(items) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var totalDragY = 0f
                    var dragStarted = false

                    // 速度采样：记录最近几帧的位移和时间
                    val velocitySamples = mutableListOf<Pair<Long, Float>>() // timestamp, deltaY
                    val maxSamples = 5

                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break

                        if (!change.pressed) break

                        val deltaY = change.position.y - change.previousPosition.y
                        totalDragY += deltaY

                        if (!dragStarted && abs(totalDragY) > viewConfiguration.touchSlop) {
                            dragStarted = true
                            isDragging = true
                        }

                        if (dragStarted) {
                            change.consume()
                            // 向上拖（负y）增加索引
                            val delta = -deltaY / itemHeightPx
                            val newValue = (scrollPosition.value + delta)
                                .coerceIn(0f, items.lastIndex.toFloat())
                            coroutineScope.launch { scrollPosition.snapTo(newValue) }

                            val newIndex = newValue.roundToInt()
                            if (newIndex != selectedIndex) {
                                onSelectedChange(newIndex)
                            }

                            // 采集速度样本
                            velocitySamples.add(System.nanoTime() to -deltaY) // 像素/帧，向上为正
                            if (velocitySamples.size > maxSamples) {
                                velocitySamples.removeAt(0)
                            }
                        }
                    } while (true)

                    if (dragStarted) {
                        // 计算释放速度（像素/秒）
                        val velocityPxPerSec = if (velocitySamples.size >= 2) {
                            val first = velocitySamples.first()
                            val last = velocitySamples.last()
                            val timeDiffSec = (last.first - first.first) / 1_000_000_000f
                            if (timeDiffSec > 0.001f) {
                                val totalDelta = velocitySamples.sumOf { it.second.toDouble() }.toFloat()
                                totalDelta / timeDiffSec
                            } else 0f
                        } else 0f

                        // 转换为 item/秒
                        val velocityItemsPerSec = velocityPxPerSec / itemHeightPx

                        if (abs(velocityItemsPerSec) > 0.5f) {
                            // 有足够速度，触发惯性滚动
                            val flingJob = coroutineScope.launch {
                                try {
                                    scrollPosition.animateDecay(
                                        initialVelocity = velocityItemsPerSec,
                                        animationSpec = exponentialDecay(frictionMultiplier = 0.6f)
                                    ) {
                                        // 实时通知选中项变化
                                        val idx = value.roundToInt()
                                        if (idx != selectedIndex) {
                                            onSelectedChange(idx)
                                        }
                                    }
                                } catch (_: Exception) {
                                    // 动画被取消（边界碰撞），正常流程
                                } finally {
                                    // 惯性滚动结束，吸附到最近项
                                    val currentVal = scrollPosition.value
                                        .coerceIn(0f, items.lastIndex.toFloat())
                                    scrollPosition.snapTo(currentVal)
                                    val target = currentVal.roundToInt()
                                    scrollPosition.animateTo(
                                        targetValue = target.toFloat(),
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                    isDragging = false
                                    onConfirmed(target)
                                    if (target != selectedIndex) {
                                        onSelectedChange(target)
                                    }
                                }
                            }
                        } else {
                            // 速度不足，直接吸附到最近项
                            val target = scrollPosition.value.roundToInt()
                                .coerceIn(0, items.lastIndex)
                            coroutineScope.launch {
                                scrollPosition.animateTo(
                                    targetValue = target.toFloat(),
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                                isDragging = false
                                onConfirmed(target)
                                if (target != selectedIndex) {
                                    onSelectedChange(target)
                                }
                            }
                        }
                    }
                    // 非拖拽（点击）由单独的 tap handler 处理
                }
            }
            .pointerInput(items, selectedIndex) {
                detectTapGestures { offset ->
                    val centerYPx = itemHeightPx * halfVisible + itemHeightPx / 2
                    val tapOffset = offset.y - centerYPx
                    val tapIndexOffset = (tapOffset / itemHeightPx).roundToInt()
                    val newIndex = (selectedIndex + tapIndexOffset).coerceIn(0, items.lastIndex)
                    if (newIndex != selectedIndex) {
                        onSelectedChange(newIndex)
                    }
                    onConfirmed(newIndex)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // 选中区域分割线
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { itemHeightPx.toDp() })
                .drawBehind {
                    drawLine(
                        color = dividerColor,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = dividerColor,
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
        )

        // 渲染可见条目
        val currentPos = scrollPosition.value
        val centerIdx = currentPos.roundToInt()
        val startIdx = (centerIdx - halfVisible - 1).coerceAtLeast(0)
        val endIdx = (centerIdx + halfVisible + 1).coerceAtMost(items.lastIndex)

        for (i in startIdx..endIdx) {
            val offsetFromCenter = i - currentPos
            val yOffset = offsetFromCenter * itemHeightPx

            val distance = abs(i - currentPos)
            val alpha = when {
                distance < 0.5f -> 1.0f
                distance < 1.5f -> 0.5f
                else -> 0.2f
            }

            val isSelected = i == centerIdx

            Text(
                text = items[i],
                style = if (isSelected) selectedStyle else unselectedStyle,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                },
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(density) { itemHeightPx.toDp() })
                    .offset { IntOffset(0, yOffset.roundToInt()) }
                    .wrapContentHeight(Alignment.CenterVertically)
            )
        }
    }
}
