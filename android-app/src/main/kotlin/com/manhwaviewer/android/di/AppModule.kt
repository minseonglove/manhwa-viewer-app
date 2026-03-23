package com.manhwaviewer.android.di

import com.manhwaviewer.data.archive.ArchiveReader
import com.manhwaviewer.data.db.getDatabaseBuilder
import com.manhwaviewer.data.db.getRoomDatabase
import com.manhwaviewer.data.preference.ViewerPreferenceDataStore
import com.manhwaviewer.data.preference.createPlatformDataStore
import com.manhwaviewer.data.repository.ArchiveFileRepositoryImpl
import com.manhwaviewer.data.repository.ImageFileRepositoryImpl
import com.manhwaviewer.data.scanner.MediaScanner
import com.manhwaviewer.domain.repository.ArchiveFileRepository
import com.manhwaviewer.domain.repository.ImageFileRepository
import com.manhwaviewer.domain.repository.ViewerPreferenceRepository
import com.manhwaviewer.domain.usecase.GetArchiveByIdUseCase
import com.manhwaviewer.domain.usecase.GetDeviceImagesUseCase
import com.manhwaviewer.domain.usecase.GetImageBytesUseCase
import com.manhwaviewer.domain.usecase.GetImageEntriesUseCase
import com.manhwaviewer.domain.usecase.GetRecentArchivesCountUseCase
import com.manhwaviewer.domain.usecase.GetRecentArchivesUseCase
import com.manhwaviewer.domain.usecase.GetVaultFilesUseCase
import com.manhwaviewer.domain.usecase.GetViewerPreferenceUseCase
import com.manhwaviewer.domain.usecase.ScanArchiveEntriesUseCase
import com.manhwaviewer.domain.usecase.ScanLocalFilesUseCase
import com.manhwaviewer.domain.usecase.UpdateLastReadIndexUseCase
import com.manhwaviewer.domain.usecase.UpdateViewerPreferenceUseCase
import com.manhwaviewer.domain.usecase.UpsertArchiveUseCase
import com.manhwaviewer.presentation.screen.filebrowser.FileBrowserViewModel
import com.manhwaviewer.presentation.screen.home.HomeViewModel
import com.manhwaviewer.presentation.screen.imagegallery.ImageGalleryViewModel
import com.manhwaviewer.presentation.screen.recent.RecentViewModel
import com.manhwaviewer.presentation.screen.settings.SettingsViewModel
import com.manhwaviewer.presentation.screen.vault.VaultViewModel
import com.manhwaviewer.presentation.screen.viewer.ViewerViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // Database
    single { getRoomDatabase(getDatabaseBuilder(androidContext())) }
    single { get<com.manhwaviewer.data.db.AppDatabase>().archiveFileDao() }

    // DataStore
    single { createPlatformDataStore(androidContext()) }

    // Data sources
    single { ArchiveReader() }
    single { MediaScanner(androidContext()) }

    // Repositories
    single<ArchiveFileRepository> { ArchiveFileRepositoryImpl(get(), get(), get()) }
    single<ImageFileRepository> { ImageFileRepositoryImpl(androidContext()) }
    single<ViewerPreferenceRepository> { ViewerPreferenceDataStore(get()) }

    // UseCases
    factory { GetRecentArchivesUseCase(get()) }
    factory { GetRecentArchivesCountUseCase(get()) }
    factory { GetDeviceImagesUseCase(get()) }
    factory { GetVaultFilesUseCase(get()) }
    factory { ScanLocalFilesUseCase(get()) }
    factory { ScanArchiveEntriesUseCase(get()) }
    factory { UpdateLastReadIndexUseCase(get()) }
    factory { GetViewerPreferenceUseCase(get()) }
    factory { UpdateViewerPreferenceUseCase(get()) }
    factory { GetArchiveByIdUseCase(get()) }
    factory { UpsertArchiveUseCase(get()) }
    factory { GetImageEntriesUseCase(get()) }
    factory { GetImageBytesUseCase(get()) }

    // ViewModels
    viewModel { HomeViewModel(get(), get(), get()) }
    viewModel { FileBrowserViewModel(get(), get(), get(), get<MediaScanner>().getDefaultScanPath()) }
    viewModel { RecentViewModel(get()) }
    viewModel { ImageGalleryViewModel(get()) }
    viewModel { VaultViewModel(get(), get()) }
    viewModel { ViewerViewModel(get(), get(), get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get()) }
}
