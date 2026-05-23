package com.example.aegis.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.aegis.ui.screens.export.ExportScreen
import com.example.aegis.ui.screens.gemma.GemmaScreen
import com.example.aegis.ui.screens.home.HomeScreen
import com.example.aegis.ui.screens.lock.LockScreen
import com.example.aegis.ui.screens.onboarding.OnboardingScreen
import com.example.aegis.ui.screens.vault.DocumentDetailScreen
import com.example.aegis.ui.screens.vault.VaultScreen
import com.example.aegis.ui.screens.visits.VisitsScreen

@Composable
fun AegisNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Lock.route,
        modifier = modifier,
    ) {
        composable(Screen.Lock.route) {
            LockScreen(
                onAuthenticated = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Lock.route) { inclusive = true }
                    }
                },
            )
        }
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                },
            )
        }
        composable(Screen.Home.route) {
            HomeScreen(
                onNeedsOnboarding = {
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onGemmaPoc = { navController.navigate(Screen.GemmaPoc.route) },
            )
        }
        composable(Screen.Vault.route) {
            VaultScreen(
                onNavigateToDetail = { id ->
                    navController.navigate(Screen.DocumentDetail.createRoute(id))
                },
            )
        }
        composable(
            route = Screen.DocumentDetail.route,
            arguments = listOf(navArgument("documentId") { type = NavType.LongType }),
        ) {
            DocumentDetailScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Visits.route) { VisitsScreen() }
        composable(Screen.Export.route) { ExportScreen() }
        composable(Screen.GemmaPoc.route) {
            GemmaScreen(onBack = { navController.popBackStack() })
        }
    }
}

/** Routes that show the bottom navigation bar. */
val bottomNavRoutes = setOf(
    Screen.Home.route,
    Screen.Vault.route,
    Screen.Visits.route,
    Screen.Export.route,
)
