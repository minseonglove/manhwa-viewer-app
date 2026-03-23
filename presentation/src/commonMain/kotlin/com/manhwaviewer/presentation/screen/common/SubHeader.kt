package com.manhwaviewer.presentation.screen.common

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import manhwa_viewer.presentation.generated.resources.Res
import manhwa_viewer.presentation.generated.resources.ic_arrow_back
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
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_arrow_back),
                            contentDescription = "뒤로가기",
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
