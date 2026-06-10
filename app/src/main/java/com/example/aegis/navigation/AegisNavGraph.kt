package com.example.aegis.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.aegis.ui.screens.backup.BackupScreen
import com.example.aegis.ui.screens.export.ExportScreen
import com.example.aegis.ui.screens.transfer.TransferScreen
import com.example.aegis.ui.screens.home.HomeScreen
import com.example.aegis.ui.screens.lock.LockScreen
import com.example.aegis.ui.screens.model.ModelSetupScreen
import com.example.aegis.ui.screens.welcome.WelcomeChoiceScreen
import com.example.aegis.ui.screens.onboarding.OnboardingScreen
import com.example.aegis.ui.screens.profile.ProfileScreen
import com.example.aegis.ui.screens.vault.DocumentDetailScreen
import com.example.aegis.ui.screens.vault.VaultScreen
import com.example.aegis.ui.screens.visits.AddVisitScreen
import com.example.aegis.ui.screens.visits.VisitDetailScreen
import com.example.aegis.ui.screens.visits.VisitsScreen

@Composable
fun AegisNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Screen.ModelSetup.route,
        modifier = modifier,
    ) {
        composable(Screen.ModelSetup.route) {
            ModelSetupScreen(
                onReady = {
                    navController.navigate(Screen.Lock.route) {
                        popUpTo(Screen.ModelSetup.route) { inclusive = true }
                    }
                },
            )
        }
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
        composable(Screen.WelcomeChoice.route) {
            WelcomeChoiceScreen(
                onStartFresh = {
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(Screen.WelcomeChoice.route) { inclusive = true }
                    }
                },
                onRestoreBackup = {
                    navController.navigate(Screen.Backup.route)
                },
            )
        }
        composable(Screen.Home.route) {
            HomeScreen(
                onNeedsOnboarding = {
                    navController.navigate(Screen.WelcomeChoice.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onAddVisit = { navController.navigate(Screen.AddVisit.route) },
                onProfile = { navController.navigate(Screen.Profile.route) },
            )
        }
        composable(Screen.Profile.route) {
            ProfileScreen(
                onBack = { navController.popBackStack() },
                onNavigateToBackup = { navController.navigate(Screen.Backup.route) },
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
        composable(Screen.Visits.route) {
            VisitsScreen(
                onAddVisit = { navController.navigate(Screen.AddVisit.route) },
                onVisitDetail = { id -> navController.navigate(Screen.VisitDetail.createRoute(id)) },
            )
        }
        composable(Screen.AddVisit.route) {
            AddVisitScreen(
                onBack = { navController.popBackStack() },
                onSaved = {
                    navController.navigate(Screen.Visits.route) {
                        popUpTo(Screen.AddVisit.route) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = Screen.VisitDetail.route,
            arguments = listOf(navArgument("visitId") { type = NavType.LongType }),
        ) {
            VisitDetailScreen(
                onBack = { navController.popBackStack() },
                onNavigateToDocument = { id ->
                    navController.navigate(Screen.DocumentDetail.createRoute(id))
                },
            )
        }
        composable(Screen.Export.route) {
            ExportScreen(
                onStartTransfer = { docIds ->
                    navController.navigate(Screen.Transfer.createRoute(docIds))
                },
            )
        }
        composable(
            route = Screen.Transfer.route,
            arguments = listOf(navArgument("docIds") {
                type = NavType.StringType
                defaultValue = ""
            }),
        ) {
            TransferScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Backup.route) {
            BackupScreen(onBack = { navController.popBackStack() })
        }
        // TODO Phase 11: GemmaPoc route removed — chatbot will use its own engine instance
    }
}

/** Routes that show the bottom navigation bar. */
val bottomNavRoutes = setOf(
    Screen.Home.route,
    Screen.Vault.route,
    Screen.Visits.route,
    Screen.Export.route,
)
