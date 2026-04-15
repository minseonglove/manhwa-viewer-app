@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.manhwaviewer.ios.di

import com.manhwaviewer.data.cache.IosCacheRepository
import com.manhwaviewer.data.db.getDatabaseBuilder
import com.manhwaviewer.data.db.getRoomDatabase
import com.manhwaviewer.data.ocr.AppleVisionTextRecognizer
import com.manhwaviewer.data.ocr.CommonMangaOcrEngine
import com.manhwaviewer.data.ocr.CommonYoloTextDetector
import com.manhwaviewer.data.ocr.ManagedOcrEngine
import com.manhwaviewer.data.ocr.MangaOcrEngine
import com.manhwaviewer.data.ocr.PlatformModelPaths
import com.manhwaviewer.data.ocr.TextDetectionRepositoryImpl
import com.manhwaviewer.data.ocr.inference.OcrEncoderRunner
import com.manhwaviewer.data.ocr.inference.OcrDecoderRunner
import com.manhwaviewer.data.ocr.inference.YoloRunner
import platform.Foundation.NSBundle
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile
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
    single(named("vaultPath")) { get<MediaScanner>().getVaultPath() }

    // Platform Repositories
    single<ImageFileRepository> { ImageFileRepositoryImpl() }
    single<CacheRepository> { IosCacheRepository(get(), get()) }
    single<MangaOcrEngine> {
        ManagedOcrEngine {
            CommonMangaOcrEngine(
                encoder = OcrEncoderRunner(),
                decoder = OcrDecoderRunner(),
                vocabLoader = {
                    val path = NSBundle.mainBundle.pathForResource(
                        "vocab", ofType = "txt", inDirectory = "mangaocr"
                    ) ?: error("Vocab file not found in bundle")
                    val content = NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)
                        ?: error("Failed to read vocab file")
                    content.toString().lines()
                },
            )
        }
    }
    single<TextDetectionRepository> {
        TextDetectionRepositoryImpl(
            CommonYoloTextDetector(YoloRunner()),
            verticalOcr = get(),
            platformRecognizer = AppleVisionTextRecognizer(),
        )
    }

    single {
        val bundle = NSBundle.mainBundle
        val clientId = bundle.objectForInfoDictionaryKey("PapagoClientId") as? String ?: ""
        val clientSecret = bundle.objectForInfoDictionaryKey("PapagoClientSecret") as? String ?: ""
        PapagoTranslationService(clientId, clientSecret)
    }
}
