package com.example.thingsusaid

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.thingsusaid.ui.navigation.Routes
import com.example.thingsusaid.ui.screens.CategoryListScreen
import com.example.thingsusaid.ui.screens.NoteListScreen
import com.example.thingsusaid.ui.screens.SettingsScreen
import com.example.thingsusaid.ui.theme.ThingsUSaidTheme
import com.example.thingsusaid.widget.TodoWidgetUpdater
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val initialCategoryId = intent.getLongExtra("category_id", -1L).takeIf { it != -1L }

        setContent {
            ThingsUSaidTheme {
                AppNavigation(initialCategoryId = initialCategoryId)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            TodoWidgetUpdater.updateAll(this@MainActivity)
        }
    }
}

@Composable
fun AppNavigation(initialCategoryId: Long?) {
    val navController = rememberNavController()
    val startDestination = if (initialCategoryId != null) {
        Routes.noteList(initialCategoryId)
    } else {
        Routes.CATEGORY_LIST
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.CATEGORY_LIST) {
            CategoryListScreen(
                onCategoryClick = { catId ->
                    navController.navigate(Routes.noteList(catId))
                },
                onSettingsClick = {
                    navController.navigate(Routes.SETTINGS)
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
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
