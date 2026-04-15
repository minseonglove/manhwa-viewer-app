package com.manhwaviewer.presentation.screen.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import manhwa_viewer.presentation.generated.resources.Res
import manhwa_viewer.presentation.generated.resources.file_browser_internal_storage
import manhwa_viewer.presentation.generated.resources.ic_chevron_right
import manhwa_viewer.presentation.generated.resources.ic_home
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun Breadcrumb(
    pathSegments: List<String>,
    onSegmentClick: (Int) -> Unit,
    onHomeClick: () -> Unit,
    modifier: Modifier = Modifier,
    showHomeIcon: Boolean = true,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showHomeIcon) {
            Icon(
                painter = painterResource(Res.drawable.ic_home),
                contentDescription = "홈",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(16.dp)
                    .clickable(onClick = onHomeClick),
            )
        }

        pathSegments.forEachIndexed { index, segment ->
            if (showHomeIcon || index > 0) {
                Icon(
                    painter = painterResource(Res.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp),
                )
            }
            val displayName = if (segment.isEmpty()) {
                stringResource(Res.string.file_browser_internal_storage)
            } else {
                segment
            }
            Text(
                text = displayName,
                style = if (index == pathSegments.lastIndex) {
                    MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    MaterialTheme.typography.labelMedium
                },
                color = if (index == pathSegments.lastIndex) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .clickable { onSegmentClick(index) }
                    .padding(horizontal = 4.dp),
            )
        }
    }
}
