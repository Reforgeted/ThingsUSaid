package com.example.thingsusaid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.thingsusaid.ui.navigation.Routes
import com.example.thingsusaid.ui.screens.CategoryListScreen
import com.example.thingsusaid.ui.screens.NoteListScreen
import com.example.thingsusaid.ui.theme.ThingsUSaidTheme
import com.example.thingsusaid.widget.TodoWidgetUpdater
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ThingsUSaidTheme {
                AppNavigation()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 鸿蒙等系统对后台广播有限制，回到前台时兜底刷新一次小组件
        lifecycleScope.launch {
            TodoWidgetUpdater.updateAll(this@MainActivity)
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.CATEGORY_LIST) {
        composable(Routes.CATEGORY_LIST) {
            CategoryListScreen(
                onCategoryClick = { catId ->
                    navController.navigate(Routes.noteList(catId))
                }
            )
        }
        composable("note_list/{categoryId}") { backStackEntry ->
            val categoryId = backStackEntry.arguments?.getString("categoryId")?.toLongOrNull() ?: return@composable
            NoteListScreen(
                categoryId = categoryId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
