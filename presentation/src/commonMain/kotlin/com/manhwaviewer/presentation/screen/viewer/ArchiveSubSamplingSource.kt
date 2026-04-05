package com.manhwaviewer.presentation.screen.viewer

import androidx.compose.ui.unit.IntSize
import com.manhwaviewer.domain.usecase.GetImageBytesUseCase
import com.manhwaviewer.presentation.util.getImageDimensions
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource
import okio.Buffer

data class ArchiveImageSourceResult(
    val source: SubSamplingImageSource,
    val imageSize: IntSize,
)

/**
 * 아카이브 단일 페이지용 SubSamplingImageSource를 생성.
 * telephoto가 내부적으로 플랫폼별 타일 디코더(BitmapRegionDecoder / Skia)를 사용.
 */
suspend fun createArchiveImageSource(
    archivePath: String,
    entryName: String,
    getImageBytesUseCase: GetImageBytesUseCase,
): ArchiveImageSourceResult {
    val bytes = getImageBytesUseCase(archivePath, entryName)
        ?: error("Failed to load image: $entryName from $archivePath")
    val (w, h) = getImageDimensions(bytes)
    val source = SubSamplingImageSource.rawSource(
        source = { Buffer().write(bytes) },
    )
    return ArchiveImageSourceResult(source = source, imageSize = IntSize(w, h))
}
