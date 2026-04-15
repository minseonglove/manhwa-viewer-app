package com.manhwaviewer.presentation.screen.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.manhwaviewer.presentation.theme.MangaLensColors
import dev.chrisbanes.haze.HazeState
import manhwa_viewer.presentation.generated.resources.Res
import manhwa_viewer.presentation.generated.resources.file_delete_confirm
import manhwa_viewer.presentation.generated.resources.file_delete_message_multiple_highlight
import manhwa_viewer.presentation.generated.resources.file_delete_message_multiple_suffix
import manhwa_viewer.presentation.generated.resources.file_delete_message_single_file_suffix
import manhwa_viewer.presentation.generated.resources.file_delete_message_single_folder_suffix
import manhwa_viewer.presentation.generated.resources.file_delete_message_single_highlight
import manhwa_viewer.presentation.generated.resources.file_delete_title
import manhwa_viewer.presentation.generated.resources.file_delete_warning
import manhwa_viewer.presentation.generated.resources.file_selection_rename
import manhwa_viewer.presentation.generated.resources.ic_close_circle
import manhwa_viewer.presentation.generated.resources.ic_repeat
import manhwa_viewer.presentation.generated.resources.vault_restore_confirm
import manhwa_viewer.presentation.generated.resources.vault_restore_info
import manhwa_viewer.presentation.generated.resources.vault_restore_message_multiple
import manhwa_viewer.presentation.generated.resources.vault_restore_message_single_file
import manhwa_viewer.presentation.generated.resources.vault_restore_message_single_folder
import manhwa_viewer.presentation.generated.resources.vault_restore_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

// ── 이름 변경 다이얼로그 ──────────────────────────────────────────

@Composable
fun RenameDialog(
    initialName: String,
    siblingNames: Set<String>,
    hazeState: HazeState,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = initialName,
                selection = TextRange(0, initialName.length),
            ),
        )
    }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val value = textFieldValue.text
    val isConfirmEnabled = value.isNotBlank()
        && value != initialName
        && siblingNames.none { it.equals(value, ignoreCase = true) }

    GlassConfirmDialog(
        hazeState = hazeState,
        onDismiss = onDismiss,
        confirmText = stringResource(Res.string.file_selection_rename),
        confirmColor = MangaLensColors.blueHi,
        confirmEnabled = isConfirmEnabled,
        onConfirm = { onConfirm(value) },
    ) {
        Text(
            text = stringResource(Res.string.file_selection_rename),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MangaLensColors.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp, bottom = 24.dp, start = 24.dp, end = 24.dp),
        )

        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MangaLensColors.fillTertiary)
                .border(0.5.dp, MangaLensColors.separator, RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier.padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { textFieldValue = it },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MangaLensColors.onSurface,
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(MangaLensColors.blueHi),
                )

                if (value.isNotEmpty()) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_close_circle),
                        contentDescription = null,
                        tint = MangaLensColors.textTertiary,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable {
                                textFieldValue = TextFieldValue("")
                            },
                    )
                }
            }
        }
    }
}

// ── 삭제 확인 다이얼로그 ──────────────────────────────────────────

@Composable
fun DeleteConfirmDialog(
    count: Int,
    firstName: String,
    isFolder: Boolean,
    hazeState: HazeState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlassConfirmDialog(
        hazeState = hazeState,
        onDismiss = onDismiss,
        confirmText = stringResource(Res.string.file_delete_confirm),
        confirmColor = MangaLensColors.red,
        onConfirm = onConfirm,
    ) {
        Text(
            text = stringResource(Res.string.file_delete_title),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MangaLensColors.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp, bottom = 16.dp, start = 24.dp, end = 24.dp),
        )

        val message = if (count == 1) {
            val highlight = stringResource(Res.string.file_delete_message_single_highlight, firstName)
            val suffix = if (isFolder) {
                stringResource(Res.string.file_delete_message_single_folder_suffix)
            } else {
                stringResource(Res.string.file_delete_message_single_file_suffix)
            }
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MangaLensColors.onSurface)) {
                    append(highlight)
                }
                append(suffix)
            }
        } else {
            val highlight = stringResource(Res.string.file_delete_message_multiple_highlight, firstName, count - 1)
            val suffix = stringResource(Res.string.file_delete_message_multiple_suffix)
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MangaLensColors.onSurface)) {
                    append(highlight)
                }
                append(suffix)
            }
        }

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MangaLensColors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        )

        Text(
            text = stringResource(Res.string.file_delete_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MangaLensColors.red.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        )
    }
}

// ── 보관함 복원 다이얼로그 ──────────────────────────────────────────

@Composable
fun RestoreConfirmDialog(
    count: Int,
    firstName: String,
    isFolder: Boolean,
    hazeState: HazeState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlassConfirmDialog(
        hazeState = hazeState,
        onDismiss = onDismiss,
        confirmText = stringResource(Res.string.vault_restore_confirm),
        confirmColor = MangaLensColors.green,
        onConfirm = onConfirm,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp, bottom = 16.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MangaLensColors.green.copy(alpha = 0.12f))
                    .border(0.5.dp, MangaLensColors.green.copy(alpha = 0.22f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_repeat),
                    contentDescription = null,
                    tint = MangaLensColors.green,
                    modifier = Modifier.size(22.dp),
                )
            }

            Text(
                text = stringResource(Res.string.vault_restore_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MangaLensColors.onSurface,
                textAlign = TextAlign.Center,
            )
        }

        val message = if (count == 1) {
            if (isFolder) {
                stringResource(Res.string.vault_restore_message_single_folder, firstName)
            } else {
                stringResource(Res.string.vault_restore_message_single_file, firstName)
            }
        } else {
            stringResource(Res.string.vault_restore_message_multiple, count)
        }

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MangaLensColors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_repeat),
                contentDescription = null,
                tint = MangaLensColors.textTertiary,
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = stringResource(Res.string.vault_restore_info),
                style = MaterialTheme.typography.bodySmall,
                color = MangaLensColors.textSecondary,
            )
        }
    }
}
