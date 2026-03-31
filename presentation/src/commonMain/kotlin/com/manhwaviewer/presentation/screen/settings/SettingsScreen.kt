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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.manhwaviewer.domain.entity.BackgroundColor
import com.manhwaviewer.domain.entity.ReadingDirection
import com.manhwaviewer.domain.entity.TranslationEngine
import com.manhwaviewer.domain.entity.ViewMode
import com.manhwaviewer.domain.entity.ViewerPreference
import manhwa_viewer.presentation.generated.resources.Res
import manhwa_viewer.presentation.generated.resources.ic_arrow_back
import manhwa_viewer.presentation.generated.resources.ic_chevron_right
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
    val snackbarHostState = remember { SnackbarHostState() }

    var showDeleteTranslationCacheDialog by remember { mutableStateOf(false) }
    var showDeleteImageCacheDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is SettingsSideEffect.ShowToast -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
            }
        }
    }

    // Cache delete confirmation dialogs
    if (showDeleteTranslationCacheDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteTranslationCacheDialog = false },
            title = { Text("캐시 삭제") },
            text = { Text("번역 캐시를 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteTranslationCacheDialog = false
                    viewModel.onIntent(SettingsIntent.ClearTranslationCache)
                }) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteTranslationCacheDialog = false }) {
                    Text("취소")
                }
            },
        )
    }

    if (showDeleteImageCacheDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteImageCacheDialog = false },
            title = { Text("캐시 삭제") },
            text = { Text("이미지 캐시를 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteImageCacheDialog = false
                    viewModel.onIntent(SettingsIntent.ClearImageCache)
                }) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteImageCacheDialog = false }) {
                    Text("취소")
                }
            },
        )
    }

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            val isLandscape = maxWidth > maxHeight
            if (isLandscape) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        ViewerSection(preference, viewModel)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        StorageSection(
                            state = state,
                            onDeleteTranslationCache = { showDeleteTranslationCacheDialog = true },
                            onDeleteImageCache = { showDeleteImageCacheDialog = true },
                        )
                    }

                    VerticalDivider(modifier = Modifier.fillMaxHeight())

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        TranslationEngineSection(preference, viewModel)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        AppInfoSection()
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    ViewerSection(preference, viewModel)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    TranslationEngineSection(preference, viewModel)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    StorageSection(
                        state = state,
                        onDeleteTranslationCache = { showDeleteTranslationCacheDialog = true },
                        onDeleteImageCache = { showDeleteImageCacheDialog = true },
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    AppInfoSection()
                }
            }
        }
    }
}

@Composable
private fun ViewerSection(
    preference: ViewerPreference,
    viewModel: SettingsViewModel,
) {
    SectionHeader("뷰어")

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
        title = "줌 상태 유지",
        checked = preference.keepZoomState,
        onCheckedChange = { viewModel.onIntent(SettingsIntent.UpdateKeepZoomState(it)) },
    )

    SettingsDropdown(
        title = "배경색",
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

@Composable
private fun TranslationEngineSection(
    preference: ViewerPreference,
    viewModel: SettingsViewModel,
) {
    SectionHeader("번역 엔진")

    SettingsDropdown(
        title = "기본 엔진",
        currentValue = when (preference.defaultTranslationEngine) {
            TranslationEngine.ML_KIT -> "ML Kit (오프라인)"
            TranslationEngine.GOOGLE_CLOUD -> "Google Cloud"
            TranslationEngine.PAPAGO -> "Papago"
        },
        options = listOf("ML Kit (오프라인)", "Google Cloud", "Papago"),
        onSelect = { index ->
            viewModel.onIntent(
                SettingsIntent.UpdateTranslationEngine(
                    when (index) {
                        0 -> TranslationEngine.ML_KIT
                        1 -> TranslationEngine.GOOGLE_CLOUD
                        else -> TranslationEngine.PAPAGO
                    },
                ),
            )
        },
    )

    SettingsSwitch(
        title = "오프라인 번역",
        subtitle = "ML Kit 오프라인 번역 사용",
        checked = preference.offlineTranslation,
        onCheckedChange = { viewModel.onIntent(SettingsIntent.UpdateOfflineTranslation(it)) },
    )

    SettingsClickable(
        title = "API 키 관리 (BYOK)",
        value = "설정",
        onClick = { /* 추후 구현 */ },
    )

    SettingsSwitch(
        title = "자동 전체 번역",
        subtitle = "페이지 진입 시 자동 번역",
        checked = preference.autoTranslateOnPageLoad,
        onCheckedChange = { viewModel.onIntent(SettingsIntent.UpdateAutoTranslate(it)) },
    )
}

@Composable
private fun StorageSection(
    state: SettingsState,
    onDeleteTranslationCache: () -> Unit,
    onDeleteImageCache: () -> Unit,
) {
    SectionHeader("저장소")

    ListItem(
        headlineContent = { Text("번역 캐시") },
        supportingContent = { Text(formatFileSize(state.translationCacheSize)) },
        trailingContent = {
            TextButton(onClick = onDeleteTranslationCache) {
                Text("삭제")
            }
        },
    )

    ListItem(
        headlineContent = { Text("이미지 캐시") },
        supportingContent = { Text(formatFileSize(state.imageCacheSize)) },
        trailingContent = {
            TextButton(onClick = onDeleteImageCache) {
                Text("삭제")
            }
        },
    )
}

@Composable
private fun AppInfoSection() {
    SectionHeader("앱 정보")

    ListItem(
        headlineContent = { Text("버전") },
        supportingContent = { Text("1.0.0") },
    )

    SettingsClickable(
        title = "오픈소스 라이선스",
        value = "보기",
        onClick = { /* 추후 구현 */ },
    )
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
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        },
    )
}

@Composable
private fun SettingsClickable(
    title: String,
    value: String? = null,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = value?.let { { Text(it) } },
        trailingContent = {
            Icon(
                painter = painterResource(Res.drawable.ic_chevron_right),
                contentDescription = null,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.1f GB".format(gb)
}
