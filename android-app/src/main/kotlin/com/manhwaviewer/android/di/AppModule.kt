package com.manhwaviewer.android.di

import com.manhwaviewer.android.BuildConfig
import com.manhwaviewer.data.archive.ArchiveReader
import com.manhwaviewer.data.cache.AndroidCacheRepository
import com.manhwaviewer.data.db.getDatabaseBuilder
import com.manhwaviewer.data.db.getRoomDatabase
import com.manhwaviewer.data.ocr.AndroidMangaOcrEngine
import com.manhwaviewer.data.ocr.ManagedOcrEngine
import com.manhwaviewer.data.ocr.MlKitTextRecognizer
import com.manhwaviewer.data.ocr.YoloTextDetector
import com.manhwaviewer.data.ocr.MangaOcrEngine
import com.manhwaviewer.data.ocr.OcrRepositoryImpl
import com.manhwaviewer.data.ocr.TextDetectionRepositoryImpl
import com.manhwaviewer.data.preference.ViewerPreferenceDataStore
import com.manhwaviewer.data.preference.createPlatformDataStore
import com.manhwaviewer.data.repository.ArchiveFileRepositoryImpl
import com.manhwaviewer.data.repository.ImageFileRepositoryImpl
import com.manhwaviewer.data.scanner.MediaScanner
import com.manhwaviewer.data.furigana.LinderaFuriganaAnalyzer
import com.manhwaviewer.data.translation.PapagoTranslationService
import com.manhwaviewer.data.repository.PageTranslationCacheRepositoryImpl
import com.manhwaviewer.data.translation.TranslationRepositoryImpl
import com.manhwaviewer.domain.repository.ArchiveFileRepository
import com.manhwaviewer.domain.repository.PageTranslationCacheRepository
import com.manhwaviewer.domain.repository.CacheRepository
import com.manhwaviewer.domain.repository.FuriganaAnalyzer
import com.manhwaviewer.domain.repository.ImageFileRepository
import com.manhwaviewer.domain.repository.OcrRepository
import com.manhwaviewer.domain.repository.TextDetectionRepository
import com.manhwaviewer.domain.repository.TranslationRepository
import com.manhwaviewer.domain.repository.ViewerPreferenceRepository
import com.manhwaviewer.domain.usecase.ClearCacheUseCase
import com.manhwaviewer.domain.usecase.AnalyzeFuriganaUseCase
import com.manhwaviewer.domain.usecase.GetArchiveByIdUseCase
import com.manhwaviewer.domain.usecase.GetCacheSizeUseCase
import com.manhwaviewer.domain.usecase.GetDeviceImagesUseCase
import com.manhwaviewer.domain.usecase.GetImageBytesUseCase
import com.manhwaviewer.domain.usecase.GetImageEntriesUseCase
import com.manhwaviewer.domain.usecase.GetRecentArchivesCountUseCase
import com.manhwaviewer.domain.usecase.GetRecentArchivesUseCase
import com.manhwaviewer.domain.usecase.GetVaultFilesUseCase
import com.manhwaviewer.domain.usecase.GetViewerPreferenceUseCase
import com.manhwaviewer.domain.usecase.RecognizePageTextUseCase
import com.manhwaviewer.domain.usecase.RecognizeTextUseCase
import com.manhwaviewer.domain.usecase.ScanArchiveEntriesUseCase
import com.manhwaviewer.domain.usecase.GetPageOcrCacheUseCase
import com.manhwaviewer.domain.usecase.GetPageTranslationCacheUseCase
import com.manhwaviewer.domain.usecase.GetNextArchiveUseCase
import com.manhwaviewer.domain.usecase.GetPreviousArchiveUseCase
import com.manhwaviewer.domain.usecase.SavePageOcrCacheUseCase
import com.manhwaviewer.domain.usecase.SavePageTranslationCacheUseCase
import com.manhwaviewer.domain.usecase.ScanLocalFilesUseCase
import com.manhwaviewer.domain.usecase.TranslateTextUseCase
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
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    // Database
    single { getRoomDatabase(getDatabaseBuilder(androidContext())) }
    single { get<com.manhwaviewer.data.db.AppDatabase>().archiveFileDao() }
    single { get<com.manhwaviewer.data.db.AppDatabase>().translationCacheDao() }
    single { get<com.manhwaviewer.data.db.AppDatabase>().pageOcrCacheDao() }

    // DataStore
    single { createPlatformDataStore(androidContext()) }

    // Data sources
    single { ArchiveReader() }
    single { MediaScanner(androidContext()) }

    // Repositories
    single<ArchiveFileRepository> { ArchiveFileRepositoryImpl(get(), get(), get()) }
    single<ImageFileRepository> { ImageFileRepositoryImpl(androidContext()) }
    single<ViewerPreferenceRepository> { ViewerPreferenceDataStore(get()) }
    single<CacheRepository> { AndroidCacheRepository(androidContext(), get(), get()) }
    single<MangaOcrEngine> { ManagedOcrEngine { AndroidMangaOcrEngine(androidContext()) } }
    single<OcrRepository> { OcrRepositoryImpl(get()) }
    single<TextDetectionRepository> {
        TextDetectionRepositoryImpl(
            YoloTextDetector(androidContext()),
            verticalOcr = get(),
            platformRecognizer = MlKitTextRecognizer(),
        )
    }
    single { PapagoTranslationService(BuildConfig.PAPAGO_CLIENT_ID, BuildConfig.PAPAGO_CLIENT_SECRET) }
    single<TranslationRepository> { TranslationRepositoryImpl(get(), get()) }
    single<PageTranslationCacheRepository> { PageTranslationCacheRepositoryImpl(get()) }
    single<FuriganaAnalyzer> { LinderaFuriganaAnalyzer() }

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
    factory { AnalyzeFuriganaUseCase(get()) }

    // ViewModels
    viewModel { HomeViewModel(get(), get(), get(), get()) }
    viewModel { FileBrowserViewModel(get(), get(), get(), get(), get<MediaScanner>().getDefaultScanPath()) }
    viewModel { RecentViewModel(get(), get()) }
    viewModel { ImageGalleryViewModel(get(), get(), get()) }
    viewModel { VaultViewModel(get(), get(), get()) }
    viewModelOf(::ViewerViewModel)
    viewModel { SettingsViewModel(get(), get(), get(), get()) }
}
