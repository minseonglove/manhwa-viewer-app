package com.manhwaviewer.presentation.screen.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import manhwa_viewer.presentation.generated.resources.Res
import manhwa_viewer.presentation.generated.resources.ic_filter
import manhwa_viewer.presentation.generated.resources.ic_grid
import manhwa_viewer.presentation.generated.resources.ic_list
import manhwa_viewer.presentation.generated.resources.ic_sort_ascending
import manhwa_viewer.presentation.generated.resources.ic_sort_descending
import org.jetbrains.compose.resources.painterResource

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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left: List/Grid toggle (pill segment)
        Row(
            modifier = Modifier
                .height(32.dp)
                .clip(CircleShape)
                .background(pillBg)
                .border(0.5.dp, pillBorder, CircleShape)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // List button
            Box(
                modifier = Modifier
                    .height(24.dp)
                    .clip(CircleShape)
                    .then(
                        if (sortState.viewMode == ListViewMode.LIST) {
                            Modifier.background(selectedBg)
                        } else {
                            Modifier
                        },
                    )
                    .clickable(onClick = {
                        if (sortState.viewMode != ListViewMode.LIST) onToggleViewMode()
                    })
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_list),
                    contentDescription = "리스트 보기",
                    tint = if (sortState.viewMode == ListViewMode.LIST) activeIconTint else inactiveIconTint,
                    modifier = Modifier.size(16.dp),
                )
            }

            // Grid button
            Box(
                modifier = Modifier
                    .height(24.dp)
                    .clip(CircleShape)
                    .then(
                        if (sortState.viewMode == ListViewMode.GRID) {
                            Modifier.background(selectedBg)
                        } else {
                            Modifier
                        },
                    )
                    .clickable(onClick = {
                        if (sortState.viewMode != ListViewMode.GRID) onToggleViewMode()
                    })
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_grid),
                    contentDescription = "그리드 보기",
                    tint = if (sortState.viewMode == ListViewMode.GRID) activeIconTint else inactiveIconTint,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        // Right: Sort field + direction
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Sort field pill button
            Box {
                Row(
                    modifier = Modifier
                        .height(32.dp)
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
                            SortField.NAME -> "이름순"
                            SortField.DATE -> "최신순"
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
                        text = { Text("이름순") },
                        onClick = {
                            onSortFieldChange(SortField.NAME)
                            showDropdown = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("최신순") },
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
                    .size(32.dp)
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
                    contentDescription = "정렬 방향",
                    tint = accentTint,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
