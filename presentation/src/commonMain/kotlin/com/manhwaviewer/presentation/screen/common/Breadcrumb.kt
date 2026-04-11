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
import manhwa_viewer.presentation.generated.resources.ic_chevron_right
import manhwa_viewer.presentation.generated.resources.ic_home
import org.jetbrains.compose.resources.painterResource

@Composable
fun Breadcrumb(
    pathSegments: List<String>,
    onSegmentClick: (Int) -> Unit,
    onHomeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_home),
            contentDescription = "홈",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(16.dp)
                .clickable(onClick = onHomeClick),
        )

        pathSegments.forEachIndexed { index, segment ->
            Icon(
                painter = painterResource(Res.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = segment,
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
