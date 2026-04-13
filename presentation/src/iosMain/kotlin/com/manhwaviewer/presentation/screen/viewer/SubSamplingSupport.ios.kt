package com.manhwaviewer.presentation.screen.viewer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize
import com.manhwaviewer.domain.usecase.GetImageBytesUseCase
import me.saket.telephoto.zoomable.ZoomableState

actual val platformSupportsSubSampling: Boolean = false

actual class PlatformSubSamplingResult

actual suspend fun createPlatformSubSamplingSource(
    archivePath: String,
    entryName: String,
    getImageBytesUseCase: GetImageBytesUseCase,
): PlatformSubSamplingResult? = null

actual fun PlatformSubSamplingResult.imageSize(): IntSize = IntSize.Zero

@Composable
actual fun PlatformSubSamplingImage(
    source: PlatformSubSamplingResult,
    zoomableState: ZoomableState,
    contentDescription: String,
    modifier: Modifier,
    alpha: Float,
) {
    // iOS에서는 SubSampling 미지원 — Coil 폴백 경로 사용
}
