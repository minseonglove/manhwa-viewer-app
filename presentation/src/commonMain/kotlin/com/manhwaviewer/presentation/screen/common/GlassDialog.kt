package com.manhwaviewer.presentation.screen.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.manhwaviewer.presentation.theme.MangaLensColors
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import manhwa_viewer.presentation.generated.resources.Res
import manhwa_viewer.presentation.generated.resources.file_selection_cancel
import org.jetbrains.compose.resources.stringResource

@Composable
fun GlassDialog(
    hazeState: HazeState,
    onDismiss: () -> Unit,
    width: Dp = 320.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
        ),
    ) {
        // Dialog에서는 Haze tint를 사용할 수 없음 (배경 위에 scrim이 표시됨)
        // 대신 Surface에 반투명 배경을 설정
        Surface(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .widthIn(max = width)
                .clip(RoundedCornerShape(16.dp))
                .border(0.5.dp, MangaLensColors.separator, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            color = MangaLensColors.surfaceGlass,
            contentColor = MangaLensColors.onSurface,
        ) {
            Box(
                modifier = Modifier
                    .hazeEffect(
                        state = hazeState,
                        style = HazeStyle(
                            backgroundColor = MangaLensColors.surface1,
                            tints = emptyList(),
                            blurRadius = 20.dp,
                            noiseFactor = 0.15f,
                        ),
                    ),
            ) {
                Column(content = content)
            }
        }
    }
}

@Composable
fun GlassConfirmDialog(
    hazeState: HazeState,
    onDismiss: () -> Unit,
    confirmText: String,
    confirmColor: Color,
    confirmEnabled: Boolean = true,
    onConfirm: () -> Unit,
    width: Dp = 320.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassDialog(
        hazeState = hazeState,
        onDismiss = onDismiss,
        width = width,
    ) {
        content()

        HorizontalDivider(color = MangaLensColors.separator, thickness = 0.5.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 24.dp)
                    .fillMaxHeight()
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.file_selection_cancel),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Normal),
                    color = MangaLensColors.blueHi,
                )
            }

            Box(
                modifier = Modifier
                    .width(0.5.dp)
                    .fillMaxHeight()
                    .background(MangaLensColors.separator),
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 24.dp)
                    .fillMaxHeight()
                    .then(
                        if (confirmEnabled) {
                            Modifier.clickable(onClick = onConfirm)
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = confirmText,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = confirmColor.copy(
                        alpha = if (confirmEnabled) 1f else 0.35f,
                    ),
                )
            }
        }
    }
}
