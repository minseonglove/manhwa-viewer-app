package com.manhwaviewer.presentation.screen.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.manhwaviewer.presentation.theme.MangaLensColors
import manhwa_viewer.presentation.generated.resources.Res
import manhwa_viewer.presentation.generated.resources.ic_arrow_back
import manhwa_viewer.presentation.generated.resources.viewer_translate_selected
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val ToolbarGradient = MangaLensColors.toolbarGradient
private val BlueButton = MangaLensColors.blue

@Composable
fun ViewerTopToolbar(
    title: String,
    showSelectTranslateButton: Boolean,
    isOverlayEnabled: Boolean,
    isOverlayButtonEnabled: Boolean,
    onBack: () -> Unit,
    onTranslateSelected: () -> Unit,
    onToggleOverlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ToolbarGradient)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Back button
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_arrow_back),
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }

        // Title
        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
        )

        // "선택 번역" button (conditional)
        AnimatedVisibility(
            visible = showSelectTranslateButton,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .background(BlueButton, RoundedCornerShape(980.dp))
                    .clickable(onClick = onTranslateSelected)
                    .padding(horizontal = 13.dp, vertical = 5.dp),
            ) {
                Text(
                    text = stringResource(Res.string.viewer_translate_selected),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Eye icon (overlay toggle)
        IconButton(
            onClick = onToggleOverlay,
            enabled = isOverlayButtonEnabled,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = if (isOverlayEnabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = "Toggle overlay",
                tint = if (isOverlayButtonEnabled) Color.White.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.15f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
