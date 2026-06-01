package com.example.aegis

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.aegis.data.ml.GemmaInferenceHolder
import com.example.aegis.navigation.AegisNavGraph
import com.example.aegis.navigation.Screen
import com.example.aegis.navigation.bottomNavRoutes
import com.example.aegis.ui.theme.AegisTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var inferenceHolder: GemmaInferenceHolder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Warm the engine at launch so the first extraction doesn't pay the load cost.
        if (inferenceHolder.isModelAvailable) {
            lifecycleScope.launch(Dispatchers.IO) {
                try { inferenceHolder.getOrLoad() } catch (_: Exception) {}
            }
        }

        setContent {
            AegisTheme {
                val navController = rememberNavController()
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (currentRoute in bottomNavRoutes) {
                            AegisBottomBar(navController, currentRoute)
                        }
                    },
                ) { innerPadding ->
                    AegisNavGraph(
                        navController = navController,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}

private enum class BottomTab(val screen: Screen, val label: String, val icon: ImageVector) {
    HOME(Screen.Home, "Home", Icons.Outlined.Home),
    VAULT(Screen.Vault, "Vault", Icons.Outlined.Folder),
    VISITS(Screen.Visits, "Visits", Icons.Outlined.DateRange),
    EXPORT(Screen.Export, "Export", Icons.Outlined.Share),
}

@Composable
private fun AegisBottomBar(navController: NavHostController, currentRoute: String?) {
    NavigationBar {
        BottomTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.screen.route,
                onClick = {
                    navController.navigate(tab.screen.route) {
                        popUpTo(Screen.Home.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(imageVector = tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
            )
        }
    }
}
