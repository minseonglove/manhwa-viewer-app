package com.manhwaviewer.presentation.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import manhwa_viewer.presentation.generated.resources.Res
import manhwa_viewer.presentation.generated.resources.back
import manhwa_viewer.presentation.generated.resources.common_cancel
import manhwa_viewer.presentation.generated.resources.common_delete
import manhwa_viewer.presentation.generated.resources.ic_arrow_back
import manhwa_viewer.presentation.generated.resources.ic_chevron_right
import manhwa_viewer.presentation.generated.resources.settings_cache_delete_title
import manhwa_viewer.presentation.generated.resources.settings_image_cache
import manhwa_viewer.presentation.generated.resources.settings_image_cache_delete_message
import manhwa_viewer.presentation.generated.resources.settings_open_source_license
import manhwa_viewer.presentation.generated.resources.settings_section_app_info
import manhwa_viewer.presentation.generated.resources.settings_section_storage
import manhwa_viewer.presentation.generated.resources.settings_title
import manhwa_viewer.presentation.generated.resources.settings_translation_cache
import manhwa_viewer.presentation.generated.resources.settings_translation_cache_delete_message
import manhwa_viewer.presentation.generated.resources.settings_version
import manhwa_viewer.presentation.generated.resources.settings_view
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToLicense: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showDeleteTranslationCacheDialog by remember { mutableStateOf(false) }
    var showDeleteImageCacheDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is SettingsSideEffect.ShowToast -> {
                    snackbarHostState.showSnackbar(getString(effect.messageRes))
                }
            }
        }
    }

    // Cache delete confirmation dialogs
    if (showDeleteTranslationCacheDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteTranslationCacheDialog = false },
            title = { Text(stringResource(Res.string.settings_cache_delete_title)) },
            text = { Text(stringResource(Res.string.settings_translation_cache_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteTranslationCacheDialog = false
                    viewModel.onIntent(SettingsIntent.ClearTranslationCache)
                }) {
                    Text(stringResource(Res.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteTranslationCacheDialog = false }) {
                    Text(stringResource(Res.string.common_cancel))
                }
            },
        )
    }

    if (showDeleteImageCacheDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteImageCacheDialog = false },
            title = { Text(stringResource(Res.string.settings_cache_delete_title)) },
            text = { Text(stringResource(Res.string.settings_image_cache_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteImageCacheDialog = false
                    viewModel.onIntent(SettingsIntent.ClearImageCache)
                }) {
                    Text(stringResource(Res.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteImageCacheDialog = false }) {
                    Text(stringResource(Res.string.common_cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.settings_title),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_arrow_back),
                            contentDescription = stringResource(Res.string.back),
                            modifier = Modifier.size(32.dp),
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
                        AppInfoSection(onNavigateToLicense = onNavigateToLicense)
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    StorageSection(
                        state = state,
                        onDeleteTranslationCache = { showDeleteTranslationCacheDialog = true },
                        onDeleteImageCache = { showDeleteImageCacheDialog = true },
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    AppInfoSection(onNavigateToLicense = onNavigateToLicense)
                }
            }
        }
    }
}

@Composable
private fun StorageSection(
    state: SettingsState,
    onDeleteTranslationCache: () -> Unit,
    onDeleteImageCache: () -> Unit,
) {
    SectionHeader(stringResource(Res.string.settings_section_storage))

    ListItem(
        headlineContent = { Text(stringResource(Res.string.settings_translation_cache)) },
        supportingContent = { Text(formatFileSize(state.translationCacheSize)) },
        trailingContent = {
            TextButton(onClick = onDeleteTranslationCache) {
                Text(stringResource(Res.string.common_delete))
            }
        },
    )

    ListItem(
        headlineContent = { Text(stringResource(Res.string.settings_image_cache)) },
        supportingContent = { Text(formatFileSize(state.imageCacheSize)) },
        trailingContent = {
            TextButton(onClick = onDeleteImageCache) {
                Text(stringResource(Res.string.common_delete))
            }
        },
    )
}

@Composable
private fun AppInfoSection(
    onNavigateToLicense: () -> Unit,
) {
    SectionHeader(stringResource(Res.string.settings_section_app_info))

    ListItem(
        headlineContent = { Text(stringResource(Res.string.settings_version)) },
        supportingContent = { Text("1.0.0") },
    )

    SettingsClickable(
        title = stringResource(Res.string.settings_open_source_license),
        value = stringResource(Res.string.settings_view),
        onClick = onNavigateToLicense,
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
    if (kb < 1024) return "${formatOneDecimal(kb)} KB"
    val mb = kb / 1024.0
    if (mb < 1024) return "${formatOneDecimal(mb)} MB"
    val gb = mb / 1024.0
    return "${formatOneDecimal(gb)} GB"
}

private fun formatOneDecimal(value: Double): String {
    val rounded = kotlin.math.round(value * 10) / 10.0
    val s = rounded.toString()
    val dotIndex = s.indexOf('.')
    return if (dotIndex < 0) "$s.0"
    else if (s.length - dotIndex - 1 >= 1) s.substring(0, dotIndex + 2)
    else "${s}0"
}
