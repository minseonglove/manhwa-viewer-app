package com.manhwaviewer.presentation.screen.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import manhwa_viewer.presentation.generated.resources.Res
import manhwa_viewer.presentation.generated.resources.ic_sort_ascending
import manhwa_viewer.presentation.generated.resources.ic_sort_descending
import manhwa_viewer.presentation.generated.resources.ic_view_grid
import manhwa_viewer.presentation.generated.resources.ic_view_list
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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggleViewMode, modifier = Modifier.size(36.dp)) {
                Icon(
                    painter = painterResource(
                        if (sortState.viewMode == ListViewMode.LIST) {
                            Res.drawable.ic_view_grid
                        } else {
                            Res.drawable.ic_view_list
                        }
                    ),
                    contentDescription = "뷰 전환",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { showDropdown = true }) {
                Text(
                    text = when (sortState.field) {
                        SortField.NAME -> "이름순"
                        SortField.DATE -> "최신순"
                    },
                    style = MaterialTheme.typography.bodySmall,
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

            IconButton(onClick = onToggleDirection, modifier = Modifier.size(36.dp)) {
                Icon(
                    painter = painterResource(
                        if (sortState.ascending) {
                            Res.drawable.ic_sort_ascending
                        } else {
                            Res.drawable.ic_sort_descending
                        }
                    ),
                    contentDescription = "정렬 방향",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
