package com.manhwaviewer.presentation.screen.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
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
import manhwa_viewer.presentation.generated.resources.back_short
import manhwa_viewer.presentation.generated.resources.home_search
import manhwa_viewer.presentation.generated.resources.ic_chevron_left
import manhwa_viewer.presentation.generated.resources.ic_search
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseTopBar(
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
    leadingContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp),
    ) {
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
                if (leadingContent != null) {
                    leadingContent()
                } else if (onBack != null) {
                    Row(
                        modifier = Modifier.clickable(onClick = onBack),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_chevron_left),
                            contentDescription = stringResource(Res.string.back_short),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = stringResource(Res.string.back_short),
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
                            contentDescription = stringResource(Res.string.home_search),
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

        AnimatedVisibility(
            visible = sortState != null,
            enter = expandVertically() + fadeIn(animationSpec = tween(durationMillis = 150)),
            exit = shrinkVertically() + fadeOut(animationSpec = tween(durationMillis = 150)),
        ) {
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
}
