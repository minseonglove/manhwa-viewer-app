@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)

package com.manhwaviewer.ios.di

import com.manhwaviewer.data.cache.IosCacheRepository
import com.manhwaviewer.data.db.getDatabaseBuilder
import com.manhwaviewer.data.db.getRoomDatabase
import com.manhwaviewer.data.ocr.AppleVisionTextRecognizer
import com.manhwaviewer.data.ocr.CommonYoloTextDetector
import com.manhwaviewer.data.ocr.ManagedOcrEngine
import com.manhwaviewer.data.ocr.MangaOcrEngine
import com.manhwaviewer.data.ocr.TextDetectionRepositoryImpl
import com.manhwaviewer.data.ocr.hayai.HayaiEngineFactory
import com.manhwaviewer.data.ocr.inference.YoloRunner
import platform.Foundation.NSBundle
import com.manhwaviewer.data.preference.InstallIdStore
import com.manhwaviewer.data.preference.createPlatformDataStore
import com.manhwaviewer.data.repository.ImageFileRepositoryImpl
import com.manhwaviewer.data.scanner.MediaScanner
import com.manhwaviewer.data.translation.FallbackTranslationService
import com.manhwaviewer.data.translation.GoogleTranslationService
import com.manhwaviewer.data.translation.ProxyTranslationService
import com.manhwaviewer.data.translation.TranslationService
import com.manhwaviewer.data.translation.attestation.AppAttestAttestor
import com.manhwaviewer.data.translation.attestation.DeviceAttestor
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
        ManagedOcrEngine { HayaiEngineFactory.fromBundle() }
    }
    single<TextDetectionRepository> {
        TextDetectionRepositoryImpl(
            CommonYoloTextDetector(YoloRunner()),
            verticalOcr = get(),
            platformRecognizer = AppleVisionTextRecognizer(),
        )
    }

    single<DeviceAttestor> { AppAttestAttestor(get()) }
    single<TranslationService> {
        fun info(key: String) = (NSBundle.mainBundle.objectForInfoDictionaryKey(key) as? String).orEmpty()
        val google = GoogleTranslationService(info("GoogleTranslateApiKey"))
        // xcconfig 는 "//" 뒤를 주석으로 잘라 버려서 URL 대신 호스트만 둔다.
        val proxyHost = info("TranslationProxyHost")
        if (proxyHost.isBlank()) google
        else FallbackTranslationService(
            primary = ProxyTranslationService(
                baseUrl = "https://$proxyHost",
                deviceId = { get<InstallIdStore>().get() },
                // 플랫폼 증명이 붙기 전까지 쓰는 개발용 증명. 릴리스 바이너리에서는 보내지 않는다.
                devSecret = info("TranslationProxyDevSecret").takeIf { it.isNotBlank() && Platform.isDebugBinary },
                attestor = get(),
            ),
            fallback = google,
        )
    }
}
