package com.manhwaviewer.presentation.screen.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import com.manhwaviewer.domain.entity.ImageScaleMode
import com.manhwaviewer.domain.entity.TextBlock
import com.manhwaviewer.domain.entity.TranslationResult
import com.manhwaviewer.presentation.util.contrastingTextColor

private data class MeasuredBlock(
    val fontSize: Float,
    val textLayout: TextLayoutResult,
    val textColor: Color,
)

@Composable
fun TranslationOverlay(
    textBlocks: List<TextBlock>,
    translationResults: List<TranslationResult>,
    dominantColors: List<Color>,
    imageWidth: Int,
    imageHeight: Int,
    imageScaleMode: ImageScaleMode = ImageScaleMode.AUTO_FIT,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current.density

    BoxWithConstraints(modifier = modifier) {
        val (fitScale, _) = calculateImageTransform(
            imageScaleMode,
            constraints.maxWidth.toFloat(),
            constraints.maxHeight.toFloat(),
            imageWidth.toFloat(),
            imageHeight.toFloat(),
        )

        // 텍스트 크기 측정 결과를 캐싱 — recomposition 시 재계산 방지
        val measuredBlocks = remember(textBlocks, translationResults, dominantColors, imageWidth, imageHeight, fitScale) {
            val minCount = minOf(textBlocks.size, translationResults.size, dominantColors.size)
            val padding = 2f * density

            (0 until minCount).mapNotNull { i ->
                val block = textBlocks[i]
                val translation = translationResults[i]
                val bgColor = dominantColors[i]

                if (translation.translatedText.isBlank()) return@mapNotNull null

                val screenW = block.width * fitScale
                val screenH = block.height * fitScale
                if (screenW < 2f || screenH < 2f) return@mapNotNull null

                val availableWidth = (screenW - padding * 2).coerceAtLeast(1f).toInt()
                val availableHeight = (screenH - padding * 2).coerceAtLeast(1f).toInt()

                val bestSize = findBestFontSize(
                    textMeasurer = textMeasurer,
                    text = translation.translatedText,
                    availableWidth = availableWidth,
                    availableHeight = availableHeight,
                )

                val textColor = contrastingTextColor(bgColor)
                val finalStyle = TextStyle(
                    fontSize = bestSize.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    textAlign = TextAlign.Center,
                )
                val finalLayout = textMeasurer.measure(
                    text = translation.translatedText,
                    style = finalStyle,
                    constraints = Constraints(maxWidth = availableWidth),
                    overflow = TextOverflow.Clip,
                )

                MeasuredBlock(
                    fontSize = bestSize,
                    textLayout = finalLayout,
                    textColor = textColor,
                )
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            if (imageWidth <= 0 || imageHeight <= 0) return@Canvas

            val (canvasFitScale, transformOffset) = calculateImageTransform(
                imageScaleMode, size.width, size.height,
                imageWidth.toFloat(), imageHeight.toFloat(),
            )

            val minCount = minOf(textBlocks.size, translationResults.size, dominantColors.size)
            var measuredIdx = 0

            for (i in 0 until minCount) {
                val block = textBlocks[i]
                val translation = translationResults[i]
                val bgColor = dominantColors[i]

                if (translation.translatedText.isBlank()) continue

                val screenW = block.width * canvasFitScale
                val screenH = block.height * canvasFitScale
                if (screenW < 2f || screenH < 2f) continue

                if (measuredIdx >= measuredBlocks.size) break
                val measured = measuredBlocks[measuredIdx++]

                val screenX = block.x * canvasFitScale + transformOffset.x
                val screenY = block.y * canvasFitScale + transformOffset.y

                drawOverlayBlock(
                    measured = measured,
                    bgColor = bgColor,
                    x = screenX,
                    y = screenY,
                    width = screenW,
                    height = screenH,
                )
            }
        }
    }
}

private fun findBestFontSize(
    textMeasurer: TextMeasurer,
    text: String,
    availableWidth: Int,
    availableHeight: Int,
): Float {
    var lo = 1f
    var hi = 48f
    var bestSize = lo

    repeat(10) {
        val mid = (lo + hi) / 2f
        val measured = textMeasurer.measure(
            text = text,
            style = TextStyle(fontSize = mid.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
            constraints = Constraints(maxWidth = availableWidth),
            overflow = TextOverflow.Clip,
        )
        if (measured.size.height <= availableHeight && measured.size.width <= availableWidth) {
            bestSize = mid
            lo = mid
        } else {
            hi = mid
        }
    }

    return bestSize
}

private fun DrawScope.drawOverlayBlock(
    measured: MeasuredBlock,
    bgColor: Color,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
) {
    val cornerRadius = minOf(4f * density, width * 0.1f, height * 0.1f)
    val fadeMargin = minOf(3f * density, width * 0.08f, height * 0.08f)

    // 1. 중심부 불투명 배경 (drawRoundRect 직접 사용)
    drawRoundRect(
        color = bgColor,
        topLeft = Offset(x, y),
        size = Size(width, height),
        cornerRadius = CornerRadius(cornerRadius),
    )

    // 2. 가장자리 페이드 아웃 — clipRect로 블록 외부 확장 방지
    if (fadeMargin > 1f) {
        val transparent = bgColor.copy(alpha = 0f)

        // 상단 페이드
        clipRect(x, y, x + width, y + fadeMargin) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(transparent, bgColor),
                    startY = y - fadeMargin,
                    endY = y + fadeMargin,
                ),
                topLeft = Offset(x, y - fadeMargin),
                size = Size(width, fadeMargin * 2),
            )
        }
        // 하단 페이드
        clipRect(x, y + height - fadeMargin, x + width, y + height) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(bgColor, transparent),
                    startY = y + height - fadeMargin,
                    endY = y + height + fadeMargin,
                ),
                topLeft = Offset(x, y + height - fadeMargin),
                size = Size(width, fadeMargin * 2),
            )
        }
        // 좌측 페이드
        clipRect(x, y, x + fadeMargin, y + height) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(transparent, bgColor),
                    startX = x - fadeMargin,
                    endX = x + fadeMargin,
                ),
                topLeft = Offset(x - fadeMargin, y),
                size = Size(fadeMargin * 2, height),
            )
        }
        // 우측 페이드
        clipRect(x + width - fadeMargin, y, x + width, y + height) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(bgColor, transparent),
                    startX = x + width - fadeMargin,
                    endX = x + width + fadeMargin,
                ),
                topLeft = Offset(x + width - fadeMargin, y),
                size = Size(fadeMargin * 2, height),
            )
        }
    }

    // 3. 사전 측정된 텍스트를 블록 중앙에 배치
    val padding = 2f * density
    val availableWidth = (width - padding * 2).coerceAtLeast(1f)
    val availableHeight = (height - padding * 2).coerceAtLeast(1f)

    val textX = x + padding + (availableWidth - measured.textLayout.size.width) / 2f
    val textY = y + padding + (availableHeight - measured.textLayout.size.height) / 2f

    drawText(
        textLayoutResult = measured.textLayout,
        topLeft = Offset(textX, textY),
    )
}
