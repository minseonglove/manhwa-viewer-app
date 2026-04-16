package com.manhwaviewer.presentation.di

import com.manhwaviewer.domain.usecase.AnalyzeFuriganaUseCase
import com.manhwaviewer.domain.usecase.ClearAllRecentHistoryUseCase
import com.manhwaviewer.domain.usecase.ClearCacheUseCase
import com.manhwaviewer.domain.usecase.ClearRecentHistoryUseCase
import com.manhwaviewer.domain.usecase.GetArchiveByIdUseCase
import com.manhwaviewer.domain.usecase.GetCacheSizeUseCase
import com.manhwaviewer.domain.usecase.GetDeviceImagesUseCase
import com.manhwaviewer.domain.usecase.GetImageBytesUseCase
import com.manhwaviewer.domain.usecase.GetImageEntriesUseCase
import com.manhwaviewer.domain.usecase.GetNextArchiveUseCase
import com.manhwaviewer.domain.usecase.GetPageOcrCacheUseCase
import com.manhwaviewer.domain.usecase.GetPageTranslationCacheUseCase
import com.manhwaviewer.domain.usecase.GetPreviousArchiveUseCase
import com.manhwaviewer.domain.usecase.GetRecentArchivesCountUseCase
import com.manhwaviewer.domain.usecase.GetRecentArchivesUseCase
import com.manhwaviewer.domain.usecase.GetViewerPreferenceUseCase
import com.manhwaviewer.domain.usecase.RecognizePageTextUseCase
import com.manhwaviewer.domain.usecase.RecognizeTextUseCase
import com.manhwaviewer.domain.usecase.CreateFolderUseCase
import com.manhwaviewer.domain.usecase.DeleteFileUseCase
import com.manhwaviewer.domain.usecase.DeleteVaultFilesUseCase
import com.manhwaviewer.domain.usecase.MoveFilesUseCase
import com.manhwaviewer.domain.usecase.MoveToVaultUseCase
import com.manhwaviewer.domain.usecase.RestoreFromVaultUseCase
import com.manhwaviewer.domain.usecase.RenameFileUseCase
import com.manhwaviewer.domain.usecase.SavePageOcrCacheUseCase
import com.manhwaviewer.domain.usecase.SavePageTranslationCacheUseCase
import com.manhwaviewer.domain.usecase.ScanArchiveEntriesUseCase
import com.manhwaviewer.domain.usecase.ScanLocalFilesUseCase
import com.manhwaviewer.domain.usecase.SearchFilesUseCase
import com.manhwaviewer.domain.usecase.TranslateTextUseCase
import com.manhwaviewer.domain.usecase.UpdateLastReadIndexUseCase
import com.manhwaviewer.domain.usecase.UpdateViewerPreferenceUseCase
import com.manhwaviewer.domain.usecase.UpsertArchiveUseCase
import com.manhwaviewer.presentation.screen.filebrowser.FileBrowserViewModel
import com.manhwaviewer.presentation.screen.filemoveselect.FileMoveSelectViewModel
import com.manhwaviewer.presentation.screen.filemoveselect.MoveRequestHolder
import com.manhwaviewer.presentation.screen.home.HomeViewModel
import com.manhwaviewer.presentation.screen.imagegallery.ImageGalleryViewModel
import com.manhwaviewer.presentation.screen.recent.RecentViewModel
import com.manhwaviewer.presentation.screen.search.SearchViewModel
import com.manhwaviewer.presentation.screen.settings.SettingsViewModel
import com.manhwaviewer.presentation.screen.vault.VaultViewModel
import com.manhwaviewer.presentation.screen.viewer.ViewerViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

val sharedModule = module {
    // UseCases
    factory { GetRecentArchivesUseCase(get()) }
    factory { GetRecentArchivesCountUseCase(get()) }
    factory { ClearRecentHistoryUseCase(get()) }
    factory { ClearAllRecentHistoryUseCase(get()) }
    factory { GetDeviceImagesUseCase(get()) }
    factory { ScanLocalFilesUseCase(get()) }
    factory { ScanArchiveEntriesUseCase(get()) }
    factory { UpdateLastReadIndexUseCase(get()) }
    factory { GetViewerPreferenceUseCase(get()) }
    factory { UpdateViewerPreferenceUseCase(get()) }
    factory { GetArchiveByIdUseCase(get()) }
    factory { UpsertArchiveUseCase(get()) }
    factory { GetImageEntriesUseCase(get()) }
    factory { GetImageBytesUseCase(get()) }
    factory { RecognizeTextUseCase(get()) }
    factory { RecognizePageTextUseCase(get()) }
    factory { GetCacheSizeUseCase(get()) }
    factory { ClearCacheUseCase(get()) }
    factory { TranslateTextUseCase(get()) }
    factory { SavePageTranslationCacheUseCase(get()) }
    factory { GetPageTranslationCacheUseCase(get()) }
    factory { GetPageOcrCacheUseCase(get()) }
    factory { SavePageOcrCacheUseCase(get()) }
    factory { GetNextArchiveUseCase(get()) }
    factory { GetPreviousArchiveUseCase(get()) }
    factory { RenameFileUseCase(get()) }
    factory { DeleteFileUseCase(get()) }
    factory { CreateFolderUseCase(get()) }
    factory { MoveToVaultUseCase(get()) }
    factory { RestoreFromVaultUseCase(get(), get()) }
    factory { DeleteVaultFilesUseCase(get(), get()) }
    factory { MoveFilesUseCase(get()) }
    factory { SearchFilesUseCase(get()) }
    factory { AnalyzeFuriganaUseCase(get()) }

    // Holders
    single { MoveRequestHolder() }

    // ViewModels
    viewModel { HomeViewModel(get(), get(), get()) }
    viewModel { FileBrowserViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(named("defaultScanPath"))) }
    viewModel { FileMoveSelectViewModel(get(), get(), get(), get(), get(named("defaultScanPath")), get(named("vaultPath"))) }
    viewModel { RecentViewModel(get(), get(), get(), get()) }
    viewModel { ImageGalleryViewModel(get(), get(), get()) }
    viewModel { VaultViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(named("vaultPath"))) }
    viewModelOf(::ViewerViewModel)
    viewModel { SearchViewModel(get(), get(), get(), get(named("defaultScanPath"))) }
    viewModel { SettingsViewModel(get(), get()) }
}
