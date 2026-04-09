package com.manhwaviewer.presentation.screen.home

import androidx.lifecycle.viewModelScope
import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.domain.usecase.GetDeviceImagesUseCase
import com.manhwaviewer.domain.usecase.GetImageEntriesUseCase
import com.manhwaviewer.domain.usecase.GetRecentArchivesUseCase
import com.manhwaviewer.domain.usecase.GetVaultFilesUseCase
import com.manhwaviewer.presentation.mvi.MviViewModel
import com.manhwaviewer.presentation.screen.viewer.ViewerPageModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

class HomeViewModel(
    getRecentArchivesUseCase: GetRecentArchivesUseCase,
    getDeviceImagesUseCase: GetDeviceImagesUseCase,
    getVaultFilesUseCase: GetVaultFilesUseCase,
    private val getImageEntriesUseCase: GetImageEntriesUseCase,
) : MviViewModel<HomeState, HomeIntent, HomeSideEffect>(HomeState()) {

    private var coverLoadJob: Job? = null

    init {
        viewModelScope.launch {
            getRecentArchivesUseCase().collect { archives ->
                val limited = archives.take(20)
                val continueTarget = archives.firstOrNull {
                    it.lastReadAt != null && it.totalPages > 0 && it.lastReadIndex < it.totalPages - 1
                }

                updateState {
                    copy(
                        recentArchives = limited,
                        continueReading = continueTarget,
                    )
                }

                coverLoadJob?.cancel()
                coverLoadJob = launch(Dispatchers.IO) {
                    loadCoverModels(limited, continueTarget)
                }
            }
        }
        viewModelScope.launch {
            getDeviceImagesUseCase().collect { images ->
                updateState { copy(imageCount = images.size) }
            }
        }
        viewModelScope.launch {
            getVaultFilesUseCase().collect { vaultFiles ->
                updateState { copy(vaultFolderCount = vaultFiles.size) }
            }
        }
    }

    private suspend fun loadCoverModels(
        recentArchives: List<ArchiveFile>,
        continueTarget: ArchiveFile?,
    ) {
        val currentModels = state.value.recentCoverModels
        val targets = (recentArchives + listOfNotNull(continueTarget))
            .distinctBy { it.id }
            .filter { it.id !in currentModels }

        if (targets.isEmpty()) return

        supervisorScope {
            val newResults = targets.map { archive ->
                async {
                    try {
                        val entries = getImageEntriesUseCase(archive.filePath)
                        if (entries.isNotEmpty()) {
                            archive.id to ViewerPageModel(
                                archivePath = archive.filePath,
                                entryNames = listOf(entries.first()),
                            )
                        } else {
                            null
                        }
                    } catch (_: Exception) {
                        null
                    }
                }
            }.mapNotNull { it.await() }.toMap()

            val merged = currentModels + newResults
            updateState {
                copy(
                    recentCoverModels = merged,
                    continueReadingCoverModel = continueTarget?.let { merged[it.id] },
                )
            }
        }
    }

    override fun handleIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.NavigateToFiles -> sendSideEffect(HomeSideEffect.NavigateToFiles)
            HomeIntent.NavigateToRecent -> sendSideEffect(HomeSideEffect.NavigateToRecent)
            HomeIntent.NavigateToImages -> sendSideEffect(HomeSideEffect.NavigateToImages)
            HomeIntent.NavigateToVault -> sendSideEffect(HomeSideEffect.NavigateToVault)
            HomeIntent.NavigateToSettings -> sendSideEffect(HomeSideEffect.NavigateToSettings)
            is HomeIntent.OpenArchive -> sendSideEffect(HomeSideEffect.NavigateToViewer(intent.archiveId))
        }
    }
}
