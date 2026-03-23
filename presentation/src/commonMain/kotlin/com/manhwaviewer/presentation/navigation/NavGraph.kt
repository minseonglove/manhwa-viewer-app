package com.manhwaviewer.presentation.navigation

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

import com.manhwaviewer.presentation.screen.home.HomeScreen
import com.manhwaviewer.presentation.screen.settings.SettingsScreen
import com.manhwaviewer.presentation.screen.viewer.ViewerScreen
import com.manhwaviewer.presentation.screen.filebrowser.FileBrowserScreen
import com.manhwaviewer.presentation.screen.recent.RecentScreen
import com.manhwaviewer.presentation.screen.imagegallery.ImageGalleryScreen
import com.manhwaviewer.presentation.screen.vault.VaultScreen

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Route.HOME,
        enterTransition = { slideInHorizontally { it } },
        exitTransition = { slideOutHorizontally { -it } },
        popEnterTransition = { slideInHorizontally { -it } },
        popExitTransition = { slideOutHorizontally { it } },
    ) {
        composable(Route.HOME) {
            HomeScreen(
                onNavigateToViewer = { archiveId ->
                    navController.navigate(Route.viewer(archiveId))
                },
                onNavigateToSettings = {
                    navController.navigate(Route.SETTINGS)
                },
                onNavigateToFiles = {
                    navController.navigate(Route.FILE_BROWSER)
                },
                onNavigateToRecent = {
                    navController.navigate(Route.RECENT)
                },
                onNavigateToImages = {
                    navController.navigate(Route.IMAGE_GALLERY)
                },
                onNavigateToVault = {
                    navController.navigate(Route.VAULT)
                },
            )
        }
        composable(
            route = Route.VIEWER,
            arguments = listOf(
                navArgument("archiveId") { type = NavType.LongType },
                navArgument("startPage") { type = NavType.IntType; defaultValue = 0 },
            ),
        ) { backStackEntry ->
            val archiveId = backStackEntry.arguments?.getLong("archiveId")
                ?.takeIf { it > 0L }
                ?: run { navController.popBackStack(); return@composable }
            val startPage = backStackEntry.arguments?.getInt("startPage") ?: 0
            ViewerScreen(
                archiveId = archiveId,
                startPage = startPage,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Route.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(Route.FILE_BROWSER) {
            FileBrowserScreen(
                onNavigateToViewer = { archiveId, startPage ->
                    navController.navigate(Route.viewer(archiveId, startPage))
                },
                onBack = { navController.popBackStack() },
                onNavigateToHome = { navController.popBackStack(Route.HOME, inclusive = false) },
            )
        }
        composable(Route.RECENT) {
            RecentScreen(
                onNavigateToViewer = { archiveId ->
                    navController.navigate(Route.viewer(archiveId))
                },
                onBack = { navController.popBackStack() },
                onNavigateToHome = { navController.popBackStack(Route.HOME, inclusive = false) },
            )
        }
        composable(Route.IMAGE_GALLERY) {
            ImageGalleryScreen(
                onBack = { navController.popBackStack() },
                onNavigateToHome = { navController.popBackStack(Route.HOME, inclusive = false) },
            )
        }
        composable(Route.VAULT) {
            VaultScreen(
                onNavigateToViewer = { archiveId ->
                    navController.navigate(Route.viewer(archiveId))
                },
                onBack = { navController.popBackStack() },
                onNavigateToHome = { navController.popBackStack(Route.HOME, inclusive = false) },
            )
        }
    }
}
