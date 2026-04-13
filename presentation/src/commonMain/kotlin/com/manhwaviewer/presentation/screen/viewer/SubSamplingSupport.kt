package com.manhwaviewer.presentation.screen.viewer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize
import com.manhwaviewer.domain.usecase.GetImageBytesUseCase
import me.saket.telephoto.zoomable.ZoomableState

/**
 * 플랫폼별 SubSampling 지원 여부.
 * Android: true (telephoto SubSamplingImage 사용 가능)
 * iOS: false (미지원, Coil 폴백 사용)
 */
expect val platformSupportsSubSampling: Boolean

/**
 * 플랫폼별 SubSampling 이미지 소스 결과를 감싸는 타입.
 * Android: 내부적으로 telephoto SubSamplingImageSource + IntSize 보유.
 * iOS: 사용되지 않음 (platformSupportsSubSampling = false).
 */
expect class PlatformSubSamplingResult

/**
 * SubSampling 이미지 소스를 생성.
 * @return null if SubSampling is not supported on this platform
 */
expect suspend fun createPlatformSubSamplingSource(
    archivePath: String,
    entryName: String,
    getImageBytesUseCase: GetImageBytesUseCase,
): PlatformSubSamplingResult?

/**
 * SubSampling 결과에서 이미지 크기를 추출.
 */
expect fun PlatformSubSamplingResult.imageSize(): IntSize

/**
 * SubSampling 이미지를 렌더링하는 Composable.
 * Android: telephoto SubSamplingImage 사용.
 * iOS: 호출되지 않음 (platformSupportsSubSampling = false).
 */
@Composable
expect fun PlatformSubSamplingImage(
    source: PlatformSubSamplingResult,
    zoomableState: ZoomableState,
    contentDescription: String,
    modifier: Modifier,
    alpha: Float,
)
