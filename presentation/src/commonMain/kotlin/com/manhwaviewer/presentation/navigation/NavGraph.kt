package com.manhwaviewer.presentation.navigation

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

import androidx.savedstate.read
import com.manhwaviewer.presentation.screen.home.HomeScreen
import com.manhwaviewer.presentation.screen.settings.SettingsScreen
import com.manhwaviewer.presentation.screen.viewer.ViewerScreen
import com.manhwaviewer.presentation.screen.filebrowser.FileBrowserScreen
import com.manhwaviewer.presentation.screen.filemoveselect.FileMoveSelectScreen
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
                onNavigateToSearch = {
                    // TODO: 검색 화면 구현 후 네비게이션 연결
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
                navArgument("startPage") { type = NavType.IntType; defaultValue = -1 },
                navArgument("sortByDate") { type = NavType.BoolType; defaultValue = false },
                navArgument("sortAscending") { type = NavType.BoolType; defaultValue = true },
            ),
            popEnterTransition = {
                slideInHorizontally { -it }
            },
            popExitTransition = {
                slideOutHorizontally { it }
            },
        ) { backStackEntry ->
            val args = backStackEntry.arguments
            val archiveId = args?.read { getLong("archiveId") }
                ?.takeIf { it > 0L }
                ?: run { navController.popBackStack(); return@composable }
            val startPage = args?.read { getIntOrNull("startPage") } ?: 0
            val sortByDate = args?.read { getBooleanOrNull("sortByDate") } ?: false
            val sortAscending = args?.read { getBooleanOrNull("sortAscending") } ?: true
            ViewerScreen(
                archiveId = archiveId,
                startPage = startPage,
                sortByDate = sortByDate,
                sortAscending = sortAscending,
                onBack = { navController.popBackStack() },
                onSettings = { navController.navigate(Route.SETTINGS) },
            )
        }
        composable(Route.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(Route.FILE_BROWSER) { backStackEntry ->
            val moveCompleted = backStackEntry.savedStateHandle
                .get<Boolean>("moveCompleted") == true
            FileBrowserScreen(
                onNavigateToViewer = { archiveId, startPage, sortByDate, sortAscending ->
                    navController.navigate(Route.viewer(archiveId, startPage, sortByDate, sortAscending))
                },
                onBack = { navController.popBackStack() },
                onNavigateToHome = { navController.popBackStack(Route.HOME, inclusive = false) },
                onNavigateToMoveSelect = { navController.navigate(Route.FILE_MOVE_SELECT) },
                moveCompleted = moveCompleted,
                onMoveCompletedConsumed = {
                    backStackEntry.savedStateHandle.remove<Boolean>("moveCompleted")
                },
            )
        }
        composable(Route.FILE_MOVE_SELECT) {
            FileMoveSelectScreen(
                onMoveCompleted = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("moveCompleted", true)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
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
                onNavigateToViewer = { archiveId, startPage, sortByDate, sortAscending ->
                    navController.navigate(Route.viewer(archiveId, startPage, sortByDate, sortAscending))
                },
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
