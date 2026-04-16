package com.manhwaviewer.presentation.screen.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manhwaviewer.presentation.theme.MangaLensColors
import manhwa_viewer.presentation.generated.resources.Res
import manhwa_viewer.presentation.generated.resources.file_selection_select_all
import manhwa_viewer.presentation.generated.resources.ic_check_off
import manhwa_viewer.presentation.generated.resources.ic_check_on
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

// ── 전체 선택 체크박스 ────────────────────────────────────────────

@Composable
fun SelectAllCheckbox(
    isAllSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SelectionCheckbox(isSelected = isAllSelected)
        Text(
            text = stringResource(Res.string.file_selection_select_all),
            style = MaterialTheme.typography.bodyMedium,
            color = MangaLensColors.onSurface,
        )
    }
}

// ── 선택 모드 체크박스 ────────────────────────────────────────────

@Composable
fun SelectionCheckbox(
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    uncheckedTint: Color = MangaLensColors.textTertiary,
    useCrossfade: Boolean = true,
) {
    if (useCrossfade) {
        Crossfade(
            targetState = isSelected,
            modifier = modifier.size(size),
        ) { checked ->
            Icon(
                painter = painterResource(
                    if (checked) Res.drawable.ic_check_on else Res.drawable.ic_check_off,
                ),
                contentDescription = null,
                tint = if (checked) Color.Unspecified else uncheckedTint,
                modifier = Modifier.size(size),
            )
        }
    } else {
        Icon(
            painter = painterResource(
                if (isSelected) Res.drawable.ic_check_on else Res.drawable.ic_check_off,
            ),
            contentDescription = null,
            tint = if (isSelected) Color.Unspecified else uncheckedTint,
            modifier = modifier.size(size),
        )
    }
}

// ── 선택 모드 leading content (AnimatedVisibility + Checkbox) ───

@Composable
fun SelectionLeadingContent(
    isSelectionMode: Boolean,
    isSelected: Boolean,
    uncheckedTint: Color = MangaLensColors.textTertiary,
) {
    AnimatedVisibility(
        visible = isSelectionMode,
        enter = scaleIn(
            initialScale = 0f,
            animationSpec = tween(durationMillis = 200),
        ) + expandHorizontally(
            animationSpec = tween(durationMillis = 200),
        ),
        exit = scaleOut(
            targetScale = 0f,
            animationSpec = tween(durationMillis = 0),
        ) + shrinkHorizontally(
            animationSpec = tween(durationMillis = 200),
        ),
    ) {
        Row {
            SelectionCheckbox(isSelected = isSelected, uncheckedTint = uncheckedTint)
            Spacer(modifier = Modifier.width(14.dp))
        }
    }
}

// ── 액션 바 버튼 ────────────────────────────────────────────────

@Composable
fun ActionBarButton(
    icon: @Composable (Color) -> Unit,
    label: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .then(
                if (enabled) Modifier.clickable(onClick = onClick) else Modifier,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        icon(color)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.24).sp,
            ),
            color = color,
        )
    }
}
