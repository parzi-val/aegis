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
    data object Export : Screen("export")
    data object GemmaPoc : Screen("gemma_poc")
}
