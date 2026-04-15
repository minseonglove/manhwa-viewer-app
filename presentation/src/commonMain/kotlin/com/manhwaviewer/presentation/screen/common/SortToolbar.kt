package com.manhwaviewer.presentation.screen.common

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import manhwa_viewer.presentation.generated.resources.Res
import manhwa_viewer.presentation.generated.resources.ic_filter
import manhwa_viewer.presentation.generated.resources.ic_grid
import manhwa_viewer.presentation.generated.resources.ic_list
import manhwa_viewer.presentation.generated.resources.ic_sort_ascending
import manhwa_viewer.presentation.generated.resources.ic_sort_descending
import manhwa_viewer.presentation.generated.resources.sort_by_date
import manhwa_viewer.presentation.generated.resources.sort_by_name
import manhwa_viewer.presentation.generated.resources.sort_direction
import manhwa_viewer.presentation.generated.resources.sort_grid_view
import manhwa_viewer.presentation.generated.resources.sort_list_view
import com.manhwaviewer.presentation.theme.Dimens
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun SortToolbar(
    sortState: SortState,
    onSortFieldChange: (SortField) -> Unit,
    onToggleDirection: () -> Unit,
    onToggleViewMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDropdown by remember { mutableStateOf(false) }

    val pillBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val pillBorder = MaterialTheme.colorScheme.outlineVariant
    val selectedBg = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    val activeIconTint = MaterialTheme.colorScheme.onSurface
    val inactiveIconTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val accentTint = MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left: List/Grid toggle (pill segment with animated indicator)
        ViewModeTabRow(
            selectedMode = sortState.viewMode,
            onToggle = onToggleViewMode,
            pillBg = pillBg,
            pillBorder = pillBorder,
            selectedBg = selectedBg,
            activeIconTint = activeIconTint,
            inactiveIconTint = inactiveIconTint,
        )

        // Right: Sort field + direction
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Sort field pill button
            Box {
                Row(
                    modifier = Modifier
                        .height(Dimens.sortToolbarItemHeight)
                        .clip(CircleShape)
                        .background(pillBg)
                        .border(0.5.dp, pillBorder, CircleShape)
                        .clickable(onClick = { showDropdown = true })
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = when (sortState.field) {
                            SortField.NAME -> stringResource(Res.string.sort_by_name)
                            SortField.DATE -> stringResource(Res.string.sort_by_date)
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.2.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Icon(
                        painter = painterResource(Res.drawable.ic_filter),
                        contentDescription = null,
                        tint = accentTint,
                        modifier = Modifier.size(16.dp),
                    )
                }
                DropdownMenu(
                    expanded = showDropdown,
                    onDismissRequest = { showDropdown = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.sort_by_name)) },
                        onClick = {
                            onSortFieldChange(SortField.NAME)
                            showDropdown = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.sort_by_date)) },
                        onClick = {
                            onSortFieldChange(SortField.DATE)
                            showDropdown = false
                        },
                    )
                }
            }

            // Sort direction button (circle)
            Box(
                modifier = Modifier
                    .size(Dimens.sortToolbarItemHeight)
                    .clip(CircleShape)
                    .background(pillBg)
                    .border(0.5.dp, pillBorder, CircleShape)
                    .clickable(onClick = onToggleDirection),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(
                        if (sortState.ascending) {
                            Res.drawable.ic_sort_ascending
                        } else {
                            Res.drawable.ic_sort_descending
                        },
                    ),
                    contentDescription = stringResource(Res.string.sort_direction),
                    tint = accentTint,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun ViewModeTabRow(
    selectedMode: ListViewMode,
    onToggle: () -> Unit,
    pillBg: Color,
    pillBorder: Color,
    selectedBg: Color,
    activeIconTint: Color,
    inactiveIconTint: Color,
) {
    val density = LocalDensity.current
    val tabHeight = Dimens.sortToolbarItemHeight - 8.dp

    // 각 탭의 x 좌표와 너비를 측정
    var listTabX by remember { mutableStateOf(0.dp) }
    var listTabWidth by remember { mutableStateOf(0.dp) }
    var gridTabX by remember { mutableStateOf(0.dp) }
    var gridTabWidth by remember { mutableStateOf(0.dp) }

    val targetX = if (selectedMode == ListViewMode.LIST) listTabX else gridTabX
    val targetWidth = if (selectedMode == ListViewMode.LIST) listTabWidth else gridTabWidth

    val animatedX by animateDpAsState(
        targetValue = targetX,
        animationSpec = tween(durationMillis = 200),
    )
    val animatedWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = tween(durationMillis = 200),
    )

    val pillPadding = 4.dp

    Box(
        modifier = Modifier
            .height(Dimens.sortToolbarItemHeight)
            .clip(CircleShape)
            .background(pillBg)
            .border(0.5.dp, pillBorder, CircleShape)
            .padding(pillPadding),
    ) {
        // 슬라이딩 인디케이터
        if (animatedWidth > 0.dp) {
            Box(
                modifier = Modifier
                    .offset(x = animatedX)
                    .width(animatedWidth)
                    .height(tabHeight)
                    .clip(CircleShape)
                    .background(selectedBg),
            )
        }

        // 탭 아이콘
        Row(
            modifier = Modifier.height(tabHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Box(
                modifier = Modifier
                    .height(tabHeight)
                    .clip(CircleShape)
                    .clickable { if (selectedMode != ListViewMode.LIST) onToggle() }
                    .onGloballyPositioned { coords ->
                        with(density) {
                            listTabX = coords.positionInParent().x.toDp()
                            listTabWidth = coords.size.width.toDp()
                        }
                    }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_list),
                    contentDescription = stringResource(Res.string.sort_list_view),
                    tint = if (selectedMode == ListViewMode.LIST) activeIconTint else inactiveIconTint,
                    modifier = Modifier.size(16.dp),
                )
            }

            Box(
                modifier = Modifier
                    .height(tabHeight)
                    .clip(CircleShape)
                    .clickable { if (selectedMode != ListViewMode.GRID) onToggle() }
                    .onGloballyPositioned { coords ->
                        with(density) {
                            gridTabX = coords.positionInParent().x.toDp()
                            gridTabWidth = coords.size.width.toDp()
                        }
                    }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_grid),
                    contentDescription = stringResource(Res.string.sort_grid_view),
                    tint = if (selectedMode == ListViewMode.GRID) activeIconTint else inactiveIconTint,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
