package com.example.thingsusaid.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch

/**
 * 支持长按拖动排序的 LazyColumn
 *
 * 交互策略：
 * - 长按 + 拖动 → 排序（支持拖到任意位置）
 * - 长按 + 松手（未拖动）→ 触发 onLongPressWithoutDrag 回调
 * - 非拖动条目使用 animateItem() 实现平滑位移动画
 *
 * 偏移修正原理：
 * 每次交换后，dragOffsetY 减去 positionsMoved * itemSizeWithSpacing，
 * 保证拖动条目的视觉位置不变。不读取 layoutInfo 避免过时数据。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T : Any> ReorderableLazyColumn(
    items: List<T>,
    key: (T) -> Any,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onLongPressWithoutDrag: ((T) -> Unit)? = null,
    onDragStateChanged: ((Boolean) -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    modifier: Modifier = Modifier,
    content: @Composable (item: T, isDragging: Boolean, dragModifier: Modifier) -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val spacingPx = with(density) { 12.dp.toPx() }

    var draggingItemKey by remember { mutableStateOf<Any?>(null) }
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var draggedItemSize by remember { mutableFloatStateOf(0f) }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = verticalArrangement
    ) {
        itemsIndexed(
            items = items,
            key = { _, item -> key(item) }
        ) { index, item ->
            val itemKey = key(item)
            val isDragging = draggingItemKey == itemKey

            val dragGestureModifier = Modifier.pointerInput(itemKey) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        draggingItemKey = itemKey
                        draggingIndex = index
                        dragOffsetY = 0f
                        val itemInfo = listState.layoutInfo.visibleItemsInfo
                            .firstOrNull { it.key == itemKey }
                        draggedItemSize = itemInfo?.size?.toFloat() ?: 92f
                        onDragStateChanged?.invoke(true)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetY += dragAmount.y

                        // 自动滚动
                        val layoutInfo = listState.layoutInfo
                        val viewportTop = layoutInfo.viewportStartOffset
                        val viewportBottom = layoutInfo.viewportEndOffset
                        val draggedItemInfo = layoutInfo.visibleItemsInfo
                            .firstOrNull { it.key == draggingItemKey }
                        if (draggedItemInfo != null) {
                            val visualTop = draggedItemInfo.offset + dragOffsetY
                            val visualBottom = visualTop + draggedItemSize
                            val scrollZone = 80f

                            when {
                                visualTop < viewportTop + scrollZone -> {
                                    scope.launch { listState.scroll { scrollBy(-12f) } }
                                }
                                visualBottom > viewportBottom - scrollZone -> {
                                    scope.launch { listState.scroll { scrollBy(12f) } }
                                }
                            }
                        }

                        // 计算目标索引：基于累积偏移量
                        val itemSizeWithSpacing = draggedItemSize + spacingPx
                        if (itemSizeWithSpacing > 0f) {
                            val positionsMoved = (dragOffsetY / itemSizeWithSpacing).toInt()
                            val targetIndex = (draggingIndex + positionsMoved)
                                .coerceIn(0, items.lastIndex)

                            if (targetIndex != draggingIndex) {
                                val actualMoved = targetIndex - draggingIndex
                                // 修正偏移：保持视觉位置不变
                                dragOffsetY -= actualMoved * itemSizeWithSpacing
                                onMove(draggingIndex, targetIndex)
                                draggingIndex = targetIndex
                            }
                        }
                    },
                    onDragEnd = {
                        draggingItemKey = null
                        draggingIndex = -1
                        dragOffsetY = 0f
                        onDragStateChanged?.invoke(false)
                    },
                    onDragCancel = {
                        draggingItemKey = null
                        draggingIndex = -1
                        dragOffsetY = 0f
                        onDragStateChanged?.invoke(false)
                    }
                )
            }

            val longClickModifier = if (onLongPressWithoutDrag != null) {
                Modifier.combinedClickable(
                    onClick = { },
                    onLongClick = { onLongPressWithoutDrag(item) }
                )
            } else {
                Modifier
            }

            val animateModifier = if (!isDragging) {
                Modifier.animateItem()
            } else {
                Modifier
            }

            val layerModifier = if (isDragging) {
                Modifier
                    .zIndex(1f)
                    .graphicsLayer {
                        translationY = dragOffsetY
                        scaleX = 1.03f
                        scaleY = 1.03f
                        alpha = 0.92f
                        shadowElevation = 8f
                    }
            } else {
                Modifier
            }

            content(
                item,
                isDragging,
                dragGestureModifier.then(longClickModifier).then(animateModifier).then(layerModifier)
            )
        }
    }
}
