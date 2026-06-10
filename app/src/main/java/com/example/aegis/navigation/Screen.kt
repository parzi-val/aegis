package com.example.aegis.navigation

sealed class Screen(val route: String) {
    data object Lock : Screen("lock")
    data object Onboarding : Screen("onboarding")
    data object Home : Screen("home")
    data object Vault : Screen("vault")
    data object DocumentDetail : Screen("vault/detail/{documentId}") {
        fun createRoute(id: Long) = "vault/detail/$id"
    }
    data object Visits : Screen("visits")
    data object AddVisit : Screen("visits/add")
    data object VisitDetail : Screen("visits/detail/{visitId}") {
        fun createRoute(id: Long) = "visits/detail/$id"
    }
    data object Profile : Screen("profile")
    data object Export : Screen("export")
    data object Transfer : Screen("transfer?docIds={docIds}") {
        fun createRoute(docIds: List<Long>) = "transfer?docIds=${docIds.joinToString(",")}"
    }
    data object ModelSetup : Screen("model_setup")
    data object WelcomeChoice : Screen("welcome_choice")
    data object GemmaPoc : Screen("gemma_poc")
    data object Backup : Screen("backup")
}
