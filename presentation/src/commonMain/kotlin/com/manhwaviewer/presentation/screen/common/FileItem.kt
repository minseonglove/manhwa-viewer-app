package com.manhwaviewer.presentation.screen.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.domain.entity.ArchiveType
import com.manhwaviewer.presentation.theme.MangaLensColors
import manhwa_viewer.presentation.generated.resources.Res
import manhwa_viewer.presentation.generated.resources.ic_archive
import manhwa_viewer.presentation.generated.resources.ic_check_badge
import manhwa_viewer.presentation.generated.resources.ic_chevron_right
import manhwa_viewer.presentation.generated.resources.ic_folder
import manhwa_viewer.presentation.generated.resources.ic_folder_open
import manhwa_viewer.presentation.generated.resources.ic_image_gallery
import org.jetbrains.compose.resources.painterResource

fun ArchiveFile.displayName(): String = when (type) {
    ArchiveType.FOLDER -> filePath.trimEnd('/').substringAfterLast('/')
    else -> fileName
}

@Composable
fun FileItem(
    archive: ArchiveFile,
    onClick: () -> Unit,
    showProgress: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (archive.type == ArchiveType.IMAGE) {
                AsyncImage(
                    model = archive.filePath,
                    contentDescription = archive.fileName,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                FileIcon(archive)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = archive.displayName(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val subtitle = buildFileSubtitle(archive)
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (showProgress && archive.totalPages > 0) {
                        val progress = ((archive.lastReadIndex + 1).toFloat() / archive.totalPages).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(width = 60.dp, height = 4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            trackColor = MaterialTheme.colorScheme.outlineVariant,
                        )
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Icon(
                painter = painterResource(Res.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(14.dp),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

private fun buildFileSubtitle(archive: ArchiveFile): String {
    val typeName = when (archive.type) {
        ArchiveType.FOLDER -> "폴더"
        ArchiveType.ZIP -> "ZIP"
        ArchiveType.CBZ -> "CBZ"
        ArchiveType.CBR -> "CBR"
        ArchiveType.IMAGE -> "이미지"
    }
    return if (archive.hasImages) "$typeName · 이미지 포함" else typeName
}

@Composable
private fun FileIcon(archive: ArchiveFile) {
    val hasImage = archive.hasImages
    val bgColor = if (hasImage) {
        MangaLensColors.primary.copy(alpha = 0.1f)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(10.dp)),
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
                drawRect(bgColor)
            }
        }

        val iconRes = when (archive.type) {
            ArchiveType.FOLDER -> {
                if (hasImage) Res.drawable.ic_folder_open
                else Res.drawable.ic_folder
            }
            ArchiveType.ZIP, ArchiveType.CBZ, ArchiveType.CBR -> Res.drawable.ic_archive
            ArchiveType.IMAGE -> Res.drawable.ic_image_gallery
        }

        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (hasImage) MangaLensColors.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )

        if (archive.type in listOf(ArchiveType.ZIP, ArchiveType.CBZ, ArchiveType.CBR) && hasImage) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .align(Alignment.BottomEnd)
                    .clip(RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
                    drawRect(MangaLensColors.primary)
                }
                Icon(
                    painter = painterResource(Res.drawable.ic_check_badge),
                    contentDescription = null,
                    tint = MangaLensColors.onPrimary,
                    modifier = Modifier.size(8.dp),
                )
            }
        }
    }
}

@Composable
fun FileGridItem(
    archive: ArchiveFile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (archive.type == ArchiveType.IMAGE) {
            AsyncImage(
                model = archive.filePath,
                contentDescription = archive.fileName,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            val hasImage = archive.hasImages
            val bgColor = if (hasImage) {
                MangaLensColors.primary.copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
            val iconRes = when (archive.type) {
                ArchiveType.FOLDER -> {
                    if (hasImage) Res.drawable.ic_folder_open
                    else Res.drawable.ic_folder
                }
                ArchiveType.ZIP, ArchiveType.CBZ, ArchiveType.CBR -> Res.drawable.ic_archive
                ArchiveType.IMAGE -> Res.drawable.ic_image_gallery
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
                    drawRect(bgColor)
                }
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = if (hasImage) MangaLensColors.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        Text(
            text = archive.displayName(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
