package com.manhwaviewer.presentation.screen.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import manhwa_viewer.presentation.generated.resources.Res
import manhwa_viewer.presentation.generated.resources.ic_chevron_left
import manhwa_viewer.presentation.generated.resources.ic_search
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    pathSegments: List<String> = emptyList(),
    onSegmentClick: (Int) -> Unit = {},
    onHomeClick: () -> Unit = {},
    sortState: SortState? = null,
    onSortFieldChange: (SortField) -> Unit = {},
    onToggleDirection: () -> Unit = {},
    onToggleViewMode: () -> Unit = {},
    trailingActions: (@Composable RowScope.() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                    ),
                )
            },
            navigationIcon = {
                if (onBack != null) {
                    Row(
                        modifier = Modifier.clickable(onClick = onBack),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_chevron_left),
                            contentDescription = "뒤로",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = "뒤로",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            actions = {
                if (trailingActions != null) {
                    trailingActions()
                } else {
                    IconButton(onClick = { /* TODO: 검색 */ }) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_search),
                            contentDescription = "검색",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            },
        )

        if (pathSegments.isNotEmpty()) {
            Breadcrumb(
                pathSegments = pathSegments,
                onSegmentClick = onSegmentClick,
                onHomeClick = onHomeClick,
            )
        }

        if (sortState != null) {
            SortToolbar(
                sortState = sortState,
                onSortFieldChange = onSortFieldChange,
                onToggleDirection = onToggleDirection,
                onToggleViewMode = onToggleViewMode,
            )
        }
    }
}
