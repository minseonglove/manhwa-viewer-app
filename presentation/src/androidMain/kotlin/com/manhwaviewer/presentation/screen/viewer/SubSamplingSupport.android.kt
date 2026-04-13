package com.manhwaviewer.presentation.screen.viewer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize
import com.manhwaviewer.domain.usecase.GetImageBytesUseCase
import com.manhwaviewer.presentation.util.getImageDimensions
import me.saket.telephoto.subsamplingimage.SubSamplingImage
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource
import me.saket.telephoto.subsamplingimage.rememberSubSamplingImageState
import me.saket.telephoto.zoomable.ZoomableState
import okio.Buffer

actual val platformSupportsSubSampling: Boolean = true

actual class PlatformSubSamplingResult(
    val source: SubSamplingImageSource,
    val imageSize: IntSize,
)

actual suspend fun createPlatformSubSamplingSource(
    archivePath: String,
    entryName: String,
    getImageBytesUseCase: GetImageBytesUseCase,
): PlatformSubSamplingResult? {
    val bytes = getImageBytesUseCase(archivePath, entryName)
        ?: error("Failed to load image: $entryName from $archivePath")
    val (w, h) = getImageDimensions(bytes)
    val source = SubSamplingImageSource.rawSource(
        source = { Buffer().write(bytes) },
    )
    return PlatformSubSamplingResult(source = source, imageSize = IntSize(w, h))
}

actual fun PlatformSubSamplingResult.imageSize(): IntSize = imageSize

@Composable
actual fun PlatformSubSamplingImage(
    source: PlatformSubSamplingResult,
    zoomableState: ZoomableState,
    contentDescription: String,
    modifier: Modifier,
    alpha: Float,
) {
    val imageState = rememberSubSamplingImageState(
        imageSource = source.source,
        zoomableState = zoomableState,
    )
    SubSamplingImage(
        state = imageState,
        contentDescription = contentDescription,
        modifier = modifier,
        alpha = alpha,
    )
}
