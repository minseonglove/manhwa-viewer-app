package com.manhwaviewer.ios.di

import com.manhwaviewer.data.cache.IosCacheRepository
import com.manhwaviewer.data.db.getDatabaseBuilder
import com.manhwaviewer.data.db.getRoomDatabase
import com.manhwaviewer.data.ocr.AppleVisionTextRecognizer
import com.manhwaviewer.data.ocr.IosMangaOcrEngine
import com.manhwaviewer.data.ocr.IosYoloTextDetector
import com.manhwaviewer.data.ocr.ManagedOcrEngine
import com.manhwaviewer.data.ocr.MangaOcrEngine
import com.manhwaviewer.data.ocr.TextDetectionRepositoryImpl
import com.manhwaviewer.data.preference.createPlatformDataStore
import com.manhwaviewer.data.repository.ImageFileRepositoryImpl
import com.manhwaviewer.data.scanner.MediaScanner
import com.manhwaviewer.data.translation.PapagoTranslationService
import com.manhwaviewer.domain.repository.CacheRepository
import com.manhwaviewer.domain.repository.ImageFileRepository
import com.manhwaviewer.domain.repository.TextDetectionRepository
import org.koin.core.qualifier.named
import org.koin.dsl.module

val iosPlatformModule = module {
    // Database & DataStore (iOS는 no-arg — NSFileManager로 경로 자동 구성)
    single { getRoomDatabase(getDatabaseBuilder()) }
    single { createPlatformDataStore() }

    // Platform Data Sources
    single { MediaScanner() }
    single(named("defaultScanPath")) { get<MediaScanner>().getDefaultScanPath() }

    // Platform Repositories
    single<ImageFileRepository> { ImageFileRepositoryImpl() }
    single<CacheRepository> { IosCacheRepository(get(), get()) }
    single<MangaOcrEngine> { ManagedOcrEngine { IosMangaOcrEngine() } }
    single<TextDetectionRepository> {
        TextDetectionRepositoryImpl(
            IosYoloTextDetector(),
            verticalOcr = get(),
            platformRecognizer = AppleVisionTextRecognizer(),
        )
    }

    // TODO: iOS 빌드 설정에서 Papago API 키 주입
    single { PapagoTranslationService("", "") }
}
