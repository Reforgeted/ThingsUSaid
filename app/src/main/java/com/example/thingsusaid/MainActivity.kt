package com.example.thingsusaid

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    var pendingCategoryId by mutableLongStateOf(-1L)
        private set
    var pendingAction by mutableStateOf("")
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        handleIntent(intent)

        setContent {
            ThingsUSaidTheme {
                AppNavigation(
                    initialCategoryId = pendingCategoryId.takeIf { it != -1L },
                    initialOpenCreateNote = pendingAction == "create_note",
                    onNewAction = { action ->
                        pendingAction = action
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        pendingCategoryId = intent.getLongExtra("category_id", -1L)
        pendingAction = intent.getStringExtra("action") ?: ""
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            TodoWidgetUpdater.updateAll(this@MainActivity)
        }
    }
}

@Composable
fun AppNavigation(
    initialCategoryId: Long?,
    initialOpenCreateNote: Boolean = false,
    onNewAction: (String) -> Unit = {}
) {
    val navController = rememberNavController()
    val startDestination = if (initialCategoryId != null) {
        Routes.noteList(initialCategoryId)
    } else {
        Routes.CATEGORY_LIST
    }

    var hasHandledInitialNav by remember { mutableStateOf(false) }

    val activity = androidx.compose.ui.platform.LocalContext.current as? MainActivity
    LaunchedEffect(activity?.pendingCategoryId, activity?.pendingAction) {
        if (activity != null && hasHandledInitialNav) {
            val catId = activity.pendingCategoryId.takeIf { it != -1L }
            val action = activity.pendingAction
            if (catId != null) {
                navController.navigate(Routes.noteList(catId)) {
                    popUpTo(Routes.CATEGORY_LIST) { inclusive = false }
                    launchSingleTop = true
                }
                onNewAction("")
            }
        }
        hasHandledInitialNav = true
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
            val isStartDestination = startDestination.startsWith("note_list")
            NoteListScreen(
                categoryId = categoryId,
                onBack = { navController.popBackStack() },
                autoOpenCreate = isStartDestination && initialOpenCreateNote
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
