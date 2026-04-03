package com.manhwaviewer.presentation.screen.viewer

import com.manhwaviewer.domain.usecase.GetImageBytesUseCase
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource
import okio.Buffer

/**
 * 아카이브 단일 페이지용 SubSamplingImageSource를 생성.
 * telephoto가 내부적으로 플랫폼별 타일 디코더(BitmapRegionDecoder / Skia)를 사용.
 */
suspend fun createArchiveImageSource(
    archivePath: String,
    entryName: String,
    getImageBytesUseCase: GetImageBytesUseCase,
): SubSamplingImageSource {
    val bytes = getImageBytesUseCase(archivePath, entryName)
        ?: error("Failed to load image: $entryName from $archivePath")
    return SubSamplingImageSource.rawSource(
        source = { Buffer().write(bytes) },
    )
}
