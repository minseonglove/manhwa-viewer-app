package com.manhwaviewer.android.di

import com.manhwaviewer.android.BuildConfig
import com.manhwaviewer.data.cache.AndroidCacheRepository
import com.manhwaviewer.data.db.getDatabaseBuilder
import com.manhwaviewer.data.db.getRoomDatabase
import com.manhwaviewer.data.di.commonDataModule
import com.manhwaviewer.data.ocr.CommonYoloTextDetector
import com.manhwaviewer.data.ocr.ManagedOcrEngine
import com.manhwaviewer.data.ocr.MangaOcrEngine
import com.manhwaviewer.data.ocr.TextDetectionRepositoryImpl
import com.manhwaviewer.data.ocr.hayai.HayaiEngineFactory
import com.manhwaviewer.data.ocr.inference.YoloRunner
import com.manhwaviewer.data.preference.createPlatformDataStore
import com.manhwaviewer.data.repository.ImageFileRepositoryImpl
import com.manhwaviewer.data.scanner.MediaScanner
import com.manhwaviewer.data.translation.GoogleTranslationService
import com.manhwaviewer.data.translation.TranslationService
import com.manhwaviewer.domain.repository.CacheRepository
import com.manhwaviewer.domain.repository.ImageFileRepository
import com.manhwaviewer.domain.repository.TextDetectionRepository
import com.manhwaviewer.presentation.di.sharedModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

private val androidPlatformModule = module {
    // Database & DataStore
    single { getRoomDatabase(getDatabaseBuilder(androidContext())) }
    single { createPlatformDataStore(androidContext()) }

    // Platform Data Sources
    single { MediaScanner(androidContext()) }
    single(named("defaultScanPath")) { get<MediaScanner>().getDefaultScanPath() }
    single(named("vaultPath")) { get<MediaScanner>().getVaultPath() }

    // Platform Repositories
    single<ImageFileRepository> { ImageFileRepositoryImpl(androidContext()) }
    single<CacheRepository> { AndroidCacheRepository(androidContext(), get(), get()) }
    single<MangaOcrEngine> {
        ManagedOcrEngine { HayaiEngineFactory.fromAssets(androidContext()) }
    }
    single<TextDetectionRepository> {
        TextDetectionRepositoryImpl(
            CommonYoloTextDetector(YoloRunner(androidContext())),
            verticalOcr = get(),
        )
    }
    single<TranslationService> { GoogleTranslationService(BuildConfig.GOOGLE_TRANSLATE_API_KEY) }
}

val appModule = listOf(androidPlatformModule, commonDataModule, sharedModule)
