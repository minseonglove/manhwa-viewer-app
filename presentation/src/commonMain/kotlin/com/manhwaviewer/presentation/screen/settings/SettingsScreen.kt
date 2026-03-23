package com.manhwaviewer.presentation.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.manhwaviewer.domain.entity.BackgroundColor
import com.manhwaviewer.domain.entity.ReadingDirection
import com.manhwaviewer.domain.entity.ViewMode
import manhwa_viewer.presentation.generated.resources.Res
import manhwa_viewer.presentation.generated.resources.ic_arrow_back
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val preference = state.preference

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_arrow_back),
                            contentDescription = "뒤로가기",
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            val isLandscape = maxWidth > maxHeight
            if (isLandscape) {
                // Landscape: left panel = viewer + storage, right panel = translation + app info
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        SectionHeader("뷰어 설정")

                        SettingsDropdown(
                            title = "읽기 방향",
                            currentValue = when (preference.readingDirection) {
                                ReadingDirection.LTR -> "왼쪽 → 오른쪽 (LTR)"
                                ReadingDirection.RTL -> "오른쪽 → 왼쪽 (RTL)"
                            },
                            options = listOf("왼쪽 → 오른쪽 (LTR)", "오른쪽 → 왼쪽 (RTL)"),
                            onSelect = { index ->
                                viewModel.onIntent(
                                    SettingsIntent.UpdateReadingDirection(
                                        if (index == 0) ReadingDirection.LTR else ReadingDirection.RTL,
                                    ),
                                )
                            },
                        )

                        SettingsDropdown(
                            title = "뷰 모드",
                            currentValue = when (preference.viewMode) {
                                ViewMode.SINGLE_PAGE -> "단면"
                                ViewMode.DOUBLE_PAGE -> "양면"
                                ViewMode.WEBTOON -> "웹툰 (세로 스크롤)"
                            },
                            options = listOf("단면", "양면", "웹툰 (세로 스크롤)"),
                            onSelect = { index ->
                                viewModel.onIntent(
                                    SettingsIntent.UpdateViewMode(
                                        when (index) {
                                            0 -> ViewMode.SINGLE_PAGE
                                            1 -> ViewMode.DOUBLE_PAGE
                                            else -> ViewMode.WEBTOON
                                        },
                                    ),
                                )
                            },
                        )

                        SettingsSwitch(
                            title = "페이지 전환 시 줌 상태 유지",
                            checked = preference.keepZoomState,
                            onCheckedChange = { viewModel.onIntent(SettingsIntent.UpdateKeepZoomState(it)) },
                        )

                        SettingsDropdown(
                            title = "뷰어 배경색",
                            currentValue = when (preference.backgroundColor) {
                                BackgroundColor.BLACK -> "검정"
                                BackgroundColor.WHITE -> "흰색"
                                BackgroundColor.AUTO -> "자동"
                            },
                            options = listOf("검정", "흰색", "자동"),
                            onSelect = { index ->
                                viewModel.onIntent(
                                    SettingsIntent.UpdateBackgroundColor(
                                        when (index) {
                                            0 -> BackgroundColor.BLACK
                                            1 -> BackgroundColor.WHITE
                                            else -> BackgroundColor.AUTO
                                        },
                                    ),
                                )
                            },
                        )
                    }

                    VerticalDivider(modifier = Modifier.fillMaxHeight())

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        SectionHeader("앱 정보")
                        ListItem(
                            headlineContent = { Text("버전") },
                            supportingContent = { Text("1.0.0") },
                        )
                    }
                }
            } else {
                // Portrait: single scroll column
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    SectionHeader("뷰어 설정")

                    SettingsDropdown(
                        title = "읽기 방향",
                        currentValue = when (preference.readingDirection) {
                            ReadingDirection.LTR -> "왼쪽 → 오른쪽 (LTR)"
                            ReadingDirection.RTL -> "오른쪽 → 왼쪽 (RTL)"
                        },
                        options = listOf("왼쪽 → 오른쪽 (LTR)", "오른쪽 → 왼쪽 (RTL)"),
                        onSelect = { index ->
                            viewModel.onIntent(
                                SettingsIntent.UpdateReadingDirection(
                                    if (index == 0) ReadingDirection.LTR else ReadingDirection.RTL,
                                ),
                            )
                        },
                    )

                    SettingsDropdown(
                        title = "뷰 모드",
                        currentValue = when (preference.viewMode) {
                            ViewMode.SINGLE_PAGE -> "단면"
                            ViewMode.DOUBLE_PAGE -> "양면"
                            ViewMode.WEBTOON -> "웹툰 (세로 스크롤)"
                        },
                        options = listOf("단면", "양면", "웹툰 (세로 스크롤)"),
                        onSelect = { index ->
                            viewModel.onIntent(
                                SettingsIntent.UpdateViewMode(
                                    when (index) {
                                        0 -> ViewMode.SINGLE_PAGE
                                        1 -> ViewMode.DOUBLE_PAGE
                                        else -> ViewMode.WEBTOON
                                    },
                                ),
                            )
                        },
                    )

                    SettingsSwitch(
                        title = "페이지 전환 시 줌 상태 유지",
                        checked = preference.keepZoomState,
                        onCheckedChange = { viewModel.onIntent(SettingsIntent.UpdateKeepZoomState(it)) },
                    )

                    SettingsDropdown(
                        title = "뷰어 배경색",
                        currentValue = when (preference.backgroundColor) {
                            BackgroundColor.BLACK -> "검정"
                            BackgroundColor.WHITE -> "흰색"
                            BackgroundColor.AUTO -> "자동"
                        },
                        options = listOf("검정", "흰색", "자동"),
                        onSelect = { index ->
                            viewModel.onIntent(
                                SettingsIntent.UpdateBackgroundColor(
                                    when (index) {
                                        0 -> BackgroundColor.BLACK
                                        1 -> BackgroundColor.WHITE
                                        else -> BackgroundColor.AUTO
                                    },
                                ),
                            )
                        },
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    SectionHeader("앱 정보")
                    ListItem(
                        headlineContent = { Text("버전") },
                        supportingContent = { Text("1.0.0") },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SettingsDropdown(
    title: String,
    currentValue: String,
    options: List<String>,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(currentValue) },
        modifier = Modifier.clickable { expanded = true },
    )

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        options.forEachIndexed { index, option ->
            DropdownMenuItem(
                text = { Text(option) },
                onClick = {
                    onSelect(index)
                    expanded = false
                },
            )
        }
    }
}

@Composable
private fun SettingsSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        },
    )
}
