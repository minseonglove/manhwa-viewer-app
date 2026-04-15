package com.manhwaviewer.presentation.screen.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.manhwaviewer.presentation.theme.MangaLensColors
import dev.chrisbanes.haze.HazeState
import manhwa_viewer.presentation.generated.resources.Res
import manhwa_viewer.presentation.generated.resources.file_create_folder_confirm
import manhwa_viewer.presentation.generated.resources.file_create_folder_title
import manhwa_viewer.presentation.generated.resources.ic_close_circle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun CreateFolderDialog(
    defaultName: String,
    siblingNames: Set<String>,
    hazeState: HazeState,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = defaultName,
                selection = TextRange(0, defaultName.length),
            ),
        )
    }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val value = textFieldValue.text
    val hasInvalidChars = value.contains('/') || value.contains('\\') || value.contains("..")
    val isConfirmEnabled = value.isNotBlank()
        && !hasInvalidChars
        && siblingNames.none { it.equals(value, ignoreCase = true) }

    GlassConfirmDialog(
        hazeState = hazeState,
        onDismiss = onDismiss,
        confirmText = stringResource(Res.string.file_create_folder_confirm),
        confirmColor = MangaLensColors.blueHi,
        confirmEnabled = isConfirmEnabled,
        onConfirm = { onConfirm(value) },
    ) {
        Text(
            text = stringResource(Res.string.file_create_folder_title),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MangaLensColors.onSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
                modifier = Modifier.padding(
                    vertical = 16.dp,
                ),
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
