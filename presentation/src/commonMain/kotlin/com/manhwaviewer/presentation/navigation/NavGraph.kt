package com.manhwaviewer.presentation.navigation

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    // 다음 권 이동 시 RTL이면 오른쪽→왼쪽, LTR이면 왼쪽→오른쪽 슬라이드
    val nextVolumeRtl = remember { mutableStateOf<Boolean?>(null) }

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
                navArgument("startPage") { type = NavType.IntType; defaultValue = -1 },
                navArgument("sortByDate") { type = NavType.BoolType; defaultValue = false },
                navArgument("sortAscending") { type = NavType.BoolType; defaultValue = true },
            ),
            enterTransition = {
                val rtl = nextVolumeRtl.value
                if (rtl != null) {
                    nextVolumeRtl.value = null
                    slideInHorizontally { if (rtl) -it else it }
                } else {
                    slideInHorizontally { it }
                }
            },
            exitTransition = {
                slideOutHorizontally { -it }
            },
        ) { backStackEntry ->
            val archiveId = backStackEntry.arguments?.getLong("archiveId")
                ?.takeIf { it > 0L }
                ?: run { navController.popBackStack(); return@composable }
            val startPage = backStackEntry.arguments?.getInt("startPage") ?: 0
            val sortByDate = backStackEntry.arguments?.getBoolean("sortByDate") ?: false
            val sortAscending = backStackEntry.arguments?.getBoolean("sortAscending") ?: true
            ViewerScreen(
                archiveId = archiveId,
                startPage = startPage,
                sortByDate = sortByDate,
                sortAscending = sortAscending,
                onBack = { navController.popBackStack() },
                onSettings = { navController.navigate(Route.SETTINGS) },
                onNavigateToNextVolume = { nextArchiveId, isRtl ->
                    nextVolumeRtl.value = isRtl
                    navController.navigate(Route.viewer(nextArchiveId, startPage = 0)) {
                        popUpTo(Route.VIEWER) { inclusive = true }
                    }
                },
            )
        }
        composable(Route.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(Route.FILE_BROWSER) {
            FileBrowserScreen(
                onNavigateToViewer = { archiveId, startPage, sortByDate, sortAscending ->
                    navController.navigate(Route.viewer(archiveId, startPage, sortByDate, sortAscending))
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
