package com.manhwaviewer.presentation.screen.common

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.manhwaviewer.presentation.theme.MangaLensColors
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect

@Composable
fun GlassDialog(
    hazeState: HazeState,
    onDismiss: () -> Unit,
    width: Dp = 270.dp,
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
                .width(width)
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
