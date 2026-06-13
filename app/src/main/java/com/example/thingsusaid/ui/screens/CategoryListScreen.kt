package com.example.thingsusaid.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.thingsusaid.data.entity.Category
import com.example.thingsusaid.ui.components.AddCategoryDialog
import com.example.thingsusaid.ui.components.CategoryCard
import com.example.thingsusaid.ui.components.ReorderableLazyColumn
import com.example.thingsusaid.ui.viewmodel.CategoryListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryListScreen(
    onCategoryClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: CategoryListViewModel = viewModel()
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var categoryToDelete by remember { mutableStateOf<Category?>(null) }
    var categoryForMenu by remember { mutableStateOf<Category?>(null) }
    var reorderedCategories by remember { mutableStateOf(categories) }
    var isDragging by remember { mutableStateOf(false) }

    // 拖动期间不同步数据库，避免覆盖本地排序状态
    LaunchedEffect(categories) {
        if (!isDragging) {
            reorderedCategories = categories
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Things U Said") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "新建分组")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            if (reorderedCategories.isEmpty()) {
                EmptyState(
                    title = "还没有分组",
                    subtitle = "点击右下角按钮创建你的第一个分组"
                )
            } else {
                ReorderableLazyColumn(
                    items = reorderedCategories,
                    key = { it.categoryId },
                    onMove = { fromIndex, toIndex ->
                        val mutableList = reorderedCategories.toMutableList()
                        val item = mutableList.removeAt(fromIndex)
                        mutableList.add(toIndex, item)
                        reorderedCategories = mutableList
                        viewModel.updateSortOrders(
                            mutableList.mapIndexed { index, cat ->
                                cat.categoryId to index
                            }
                        )
                    },
                    onLongPressWithoutDrag = { category ->
                        categoryForMenu = category
                    },
                    onDragStateChanged = { dragging ->
                        isDragging = dragging
                        // 拖动结束后同步数据库最新状态
                        if (!dragging) {
                            reorderedCategories = categories
                        }
                    },
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) { category, isDragging, dragModifier ->
                    CategoryCard(
                        category = category,
                        onClick = { onCategoryClick(category.categoryId) },
                        modifier = dragModifier
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddCategoryDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, color ->
                viewModel.addCategory(name, color)
            }
        )
    }

    // 长按（未拖动）弹出的菜单
    categoryForMenu?.let { category ->
        AlertDialog(
            onDismissRequest = { categoryForMenu = null },
            title = { Text(category.name) },
            text = {
                Text("长按并拖动可以调整排序\n如需添加桌面小组件，请在桌面空白处长按 → 小组件 → 选择 Things U Said")
            },
            confirmButton = {
                TextButton(onClick = { categoryForMenu = null }) {
                    Text("知道了")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        categoryToDelete = category
                        categoryForMenu = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除分组")
                }
            }
        )
    }

    categoryToDelete?.let { category ->
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("删除分组") },
            text = { Text("确定要删除「${category.name}」吗？该分组下的所有便签也会被删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCategory(category)
                        categoryToDelete = null
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun EmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
    }
}
