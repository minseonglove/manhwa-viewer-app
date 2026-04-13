package com.manhwaviewer.data.di

import com.manhwaviewer.data.archive.ArchiveReader
import com.manhwaviewer.data.db.AppDatabase
import com.manhwaviewer.data.furigana.LinderaFuriganaAnalyzer
import com.manhwaviewer.data.ocr.OcrRepositoryImpl
import com.manhwaviewer.data.preference.ViewerPreferenceDataStore
import com.manhwaviewer.data.repository.ArchiveFileRepositoryImpl
import com.manhwaviewer.data.repository.PageTranslationCacheRepositoryImpl
import com.manhwaviewer.data.translation.TranslationRepositoryImpl
import com.manhwaviewer.domain.repository.ArchiveFileRepository
import com.manhwaviewer.domain.repository.FuriganaAnalyzer
import com.manhwaviewer.domain.repository.OcrRepository
import com.manhwaviewer.domain.repository.PageTranslationCacheRepository
import com.manhwaviewer.domain.repository.TranslationRepository
import com.manhwaviewer.domain.repository.ViewerPreferenceRepository
import org.koin.dsl.module

val commonDataModule = module {
    // DAOs (AppDatabase는 플랫폼 모듈에서 제공)
    single { get<AppDatabase>().archiveFileDao() }
    single { get<AppDatabase>().translationCacheDao() }
    single { get<AppDatabase>().pageOcrCacheDao() }

    // Data Sources
    single { ArchiveReader() }

    // Repositories (플랫폼 무관)
    single<ArchiveFileRepository> { ArchiveFileRepositoryImpl(get(), get(), get(), get()) }
    single<ViewerPreferenceRepository> { ViewerPreferenceDataStore(get()) }
    single<OcrRepository> { OcrRepositoryImpl(get()) }
    single<TranslationRepository> { TranslationRepositoryImpl(get(), get()) }
    single<PageTranslationCacheRepository> { PageTranslationCacheRepositoryImpl(get()) }
    single<FuriganaAnalyzer> { LinderaFuriganaAnalyzer() }
}
