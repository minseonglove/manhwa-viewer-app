package com.manhwaviewer.presentation.navigation

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay

import com.manhwaviewer.presentation.screen.home.HomeScreen
import com.manhwaviewer.presentation.screen.settings.SettingsScreen
import com.manhwaviewer.presentation.screen.viewer.ViewerScreen
import com.manhwaviewer.presentation.screen.filebrowser.FileBrowserScreen
import com.manhwaviewer.presentation.screen.filemoveselect.FileMoveSelectScreen
import com.manhwaviewer.presentation.screen.recent.RecentScreen
import com.manhwaviewer.presentation.screen.imagegallery.ImageGalleryScreen
import com.manhwaviewer.presentation.screen.search.SearchScreen
import com.manhwaviewer.presentation.screen.vault.VaultScreen

private fun NavBackStack<NavKey>.popToRoot() {
    if (size > 1) {
        subList(1, size).clear()
    }
}

@Composable
fun NavGraph(backStack: NavBackStack<NavKey>) {
    var fileBrowserMoveCompleted by remember { mutableStateOf(false) }
    var vaultMoveCompleted by remember { mutableStateOf(false) }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        transitionSpec = {
            slideInHorizontally(initialOffsetX = { it }) togetherWith
                slideOutHorizontally(targetOffsetX = { -it })
        },
        popTransitionSpec = {
            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                slideOutHorizontally(targetOffsetX = { it })
        },
        predictivePopTransitionSpec = {
            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                slideOutHorizontally(targetOffsetX = { it })
        },
        entryProvider = entryProvider {
            entry<Route.Home> {
                HomeScreen(
                    onNavigateToViewer = { archiveId ->
                        backStack.add(Route.Viewer(archiveId = archiveId))
                    },
                    onNavigateToSettings = {
                        backStack.add(Route.Settings)
                    },
                    onNavigateToSearch = {
                        backStack.add(Route.Search)
                    },
                    onNavigateToFiles = {
                        backStack.add(Route.FileBrowser())
                    },
                    onNavigateToRecent = {
                        backStack.add(Route.Recent)
                    },
                    onNavigateToImages = {
                        backStack.add(Route.ImageGallery)
                    },
                    onNavigateToVault = {
                        backStack.add(Route.Vault)
                    },
                )
            }

            entry<Route.Viewer> { key ->
                if (key.archiveId <= 0L) {
                    backStack.removeLastOrNull()
                    return@entry
                }
                ViewerScreen(
                    archiveId = key.archiveId,
                    startPage = key.startPage,
                    sortByDate = key.sortByDate,
                    sortAscending = key.sortAscending,
                    onBack = { backStack.removeLastOrNull() },
                    onSettings = { backStack.add(Route.Settings) },
                )
            }

            entry<Route.Settings> {
                SettingsScreen(
                    onBack = { backStack.removeLastOrNull() },
                )
            }

            entry<Route.FileBrowser> { key ->
                FileBrowserScreen(
                    onNavigateToViewer = { archiveId, startPage, sortByDate, sortAscending ->
                        backStack.add(Route.Viewer(archiveId, startPage, sortByDate, sortAscending))
                    },
                    onBack = { backStack.removeLastOrNull() },
                    onNavigateToHome = {
                        backStack.popToRoot()
                    },
                    onNavigateToMoveSelect = {
                        backStack.add(Route.FileMoveSelect)
                    },
                    moveCompleted = fileBrowserMoveCompleted,
                    onMoveCompletedConsumed = {
                        fileBrowserMoveCompleted = false
                    },
                    initialPath = key.initialPath,
                )
            }

            entry<Route.FileMoveSelect> {
                FileMoveSelectScreen(
                    onMoveCompleted = {
                        val hasVault = backStack.any { it is Route.Vault }
                        if (hasVault) {
                            vaultMoveCompleted = true
                        } else {
                            fileBrowserMoveCompleted = true
                        }
                        backStack.removeLastOrNull()
                    },
                    onBack = { backStack.removeLastOrNull() },
                )
            }

            entry<Route.Recent> {
                RecentScreen(
                    onNavigateToViewer = { archiveId ->
                        backStack.add(Route.Viewer(archiveId = archiveId))
                    },
                    onBack = { backStack.removeLastOrNull() },
                    onNavigateToHome = {
                        backStack.popToRoot()
                    },
                )
            }

            entry<Route.ImageGallery> {
                ImageGalleryScreen(
                    onNavigateToViewer = { archiveId, startPage, sortByDate, sortAscending ->
                        backStack.add(Route.Viewer(archiveId, startPage, sortByDate, sortAscending))
                    },
                    onBack = { backStack.removeLastOrNull() },
                    onNavigateToHome = {
                        backStack.popToRoot()
                    },
                )
            }

            entry<Route.Search> {
                SearchScreen(
                    onNavigateToViewer = { archiveId, startPage, sortByDate, sortAscending ->
                        backStack.add(Route.Viewer(archiveId, startPage, sortByDate, sortAscending))
                    },
                    onNavigateToFileBrowser = { path ->
                        backStack.add(Route.FileBrowser(initialPath = path))
                    },
                    onBack = { backStack.removeLastOrNull() },
                )
            }

            entry<Route.Vault> {
                VaultScreen(
                    onNavigateToViewer = { archiveId, startPage, sortByDate, sortAscending ->
                        backStack.add(Route.Viewer(archiveId, startPage, sortByDate, sortAscending))
                    },
                    onBack = { backStack.removeLastOrNull() },
                    onNavigateToHome = {
                        backStack.popToRoot()
                    },
                    onNavigateToMoveSelect = {
                        backStack.add(Route.FileMoveSelect)
                    },
                    moveCompleted = vaultMoveCompleted,
                    onMoveCompletedConsumed = {
                        vaultMoveCompleted = false
                    },
                )
            }
        },
    )
}
