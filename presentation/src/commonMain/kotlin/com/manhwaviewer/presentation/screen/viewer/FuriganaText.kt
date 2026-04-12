package com.manhwaviewer.presentation.screen.viewer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manhwaviewer.domain.entity.FuriganaToken

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FuriganaText(
    tokens: List<FuriganaToken>,
    modifier: Modifier = Modifier,
    baseColor: Color = Color.White,
    baseFontSize: TextUnit = 14.sp,
    rubyFontSize: TextUnit = (baseFontSize.value * 0.5f).sp,
    rubyColor: Color = baseColor.copy(alpha = 0.7f),
) {
    if (tokens.isEmpty()) return

    FlowRow(modifier = modifier) {
        tokens.forEach { token ->
            if (token.isKanji && token.surface != token.reading) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 1.dp),
                ) {
                    Text(
                        text = token.reading,
                        color = rubyColor,
                        fontSize = rubyFontSize,
                        lineHeight = (rubyFontSize.value + 2).sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                    Text(
                        text = token.surface,
                        color = baseColor,
                        fontSize = baseFontSize,
                        lineHeight = (baseFontSize.value + 4).sp,
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height((rubyFontSize.value + 2).dp))
                    Text(
                        text = token.surface,
                        color = baseColor,
                        fontSize = baseFontSize,
                        lineHeight = (baseFontSize.value + 4).sp,
                    )
                }
            }
        }
    }
}
