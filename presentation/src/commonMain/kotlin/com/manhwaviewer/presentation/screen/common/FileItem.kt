package com.manhwaviewer.presentation.screen.common

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.domain.entity.ArchiveType
import com.manhwaviewer.presentation.screen.viewer.ViewerPageModel
import com.manhwaviewer.presentation.theme.MangaLensColors
import manhwa_viewer.presentation.generated.resources.Res
import manhwa_viewer.presentation.generated.resources.file_type_cbr
import manhwa_viewer.presentation.generated.resources.file_type_cbz
import manhwa_viewer.presentation.generated.resources.file_type_folder
import manhwa_viewer.presentation.generated.resources.file_type_folder_with_images
import manhwa_viewer.presentation.generated.resources.file_type_has_images
import manhwa_viewer.presentation.generated.resources.file_type_image
import manhwa_viewer.presentation.generated.resources.file_type_zip
import manhwa_viewer.presentation.generated.resources.ic_archive
import manhwa_viewer.presentation.generated.resources.ic_check_off
import manhwa_viewer.presentation.generated.resources.ic_check_on
import manhwa_viewer.presentation.generated.resources.ic_chevron_right
import manhwa_viewer.presentation.generated.resources.ic_file_folder
import manhwa_viewer.presentation.generated.resources.ic_image_gallery
import manhwa_viewer.presentation.generated.resources.ic_lock
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

fun ArchiveFile.displayName(): String = when (type) {
    ArchiveType.FOLDER -> filePath.trimEnd('/').substringAfterLast('/')
    else -> fileName
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileItem(
    archive: ArchiveFile,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    coverModel: ViewerPageModel? = null,
    showProgress: Boolean = false,
    showFileType: Boolean = true,
    showFolderCover: Boolean = false,
    showLockBadge: Boolean = false,
    insideArchive: Boolean = false,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onLongClick != null) {
                        Modifier.combinedClickable(
                            onClick = onClick,
                            onLongClick = onLongClick,
                        )
                    } else {
                        Modifier.clickable(onClick = onClick)
                    },
                )
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingContent != null) {
                leadingContent()
            }

            // Icon area (48x48) with optional lock badge
            Box {
                FileIconArea(archive, coverModel, showFolderCover)
                if (showLockBadge) {
                    LockBadge(modifier = Modifier.align(Alignment.BottomEnd))
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = archive.displayName(),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showFileType) {
                        Text(
                            text = fileSubtitle(archive, insideArchive),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (showProgress && archive.totalPages > 0) {
                        val progress = ((archive.lastReadIndex + 1).toFloat() / archive.totalPages).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(width = 60.dp, height = 6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            trackColor = MaterialTheme.colorScheme.outlineVariant,
                        )
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // Trailing content: custom or default
            if (trailingContent != null) {
                Spacer(modifier = Modifier.width(14.dp))
                trailingContent()
            } else if (archive.type == ArchiveType.FOLDER) {
                Spacer(modifier = Modifier.width(14.dp))
                Icon(
                    painter = painterResource(Res.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(start = 62.dp),
        )
    }
}

@Composable
private fun fileSubtitle(archive: ArchiveFile, insideArchive: Boolean = false): String {
    if (insideArchive) return stringResource(Res.string.file_type_image)
    return when (archive.type) {
        ArchiveType.FOLDER -> if (archive.hasImages) {
            stringResource(Res.string.file_type_folder_with_images)
        } else {
            stringResource(Res.string.file_type_folder)
        }
        ArchiveType.ZIP, ArchiveType.CBZ, ArchiveType.CBR -> {
            val typeName = when (archive.type) {
                ArchiveType.CBZ -> stringResource(Res.string.file_type_cbz)
                ArchiveType.CBR -> stringResource(Res.string.file_type_cbr)
                else -> stringResource(Res.string.file_type_zip)
            }
            if (archive.hasImages) {
                "$typeName · ${stringResource(Res.string.file_type_has_images)}"
            } else {
                typeName
            }
        }
        ArchiveType.IMAGE -> stringResource(Res.string.file_type_image)
    }
}

@Composable
private fun FileIconArea(archive: ArchiveFile, coverModel: ViewerPageModel?, showFolderCover: Boolean = false) {
    when {
        // Folder with cover thumbnail (opt-in)
        archive.type == ArchiveType.FOLDER && showFolderCover && coverModel != null -> {
            AsyncImage(
                model = coverModel,
                contentDescription = archive.fileName,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
        }
        // Archive with cover thumbnail
        archive.type in setOf(ArchiveType.ZIP, ArchiveType.CBZ, ArchiveType.CBR) && coverModel != null -> {
            AsyncImage(
                model = coverModel,
                contentDescription = archive.fileName,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
        }
        // Image file
        archive.type == ArchiveType.IMAGE -> {
            AsyncImage(
                model = archive.filePath,
                contentDescription = archive.fileName,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
        }
        // Folder
        archive.type == ArchiveType.FOLDER -> {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MangaLensColors.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_file_folder),
                    contentDescription = null,
                    tint = MangaLensColors.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        // Archive without cover
        else -> {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x24787880)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_archive),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun LockBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MangaLensColors.tilePurple),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_lock),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(12.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGridItem(
    archive: ArchiveFile,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    coverModel: ViewerPageModel? = null,
    showLockBadge: Boolean = false,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier.clickable(onClick = onClick)
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                // Archive with cover
                archive.type in setOf(ArchiveType.ZIP, ArchiveType.CBZ, ArchiveType.CBR) && coverModel != null -> {
                    AsyncImage(
                        model = coverModel,
                        contentDescription = archive.fileName,
                        modifier = Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    ArchiveTypeBadge(archive.type, Modifier.align(Alignment.BottomEnd))
                }
                // Image file
                archive.type == ArchiveType.IMAGE -> {
                    AsyncImage(
                        model = archive.filePath,
                        contentDescription = archive.fileName,
                        modifier = Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
                // Folder with cover
                archive.type == ArchiveType.FOLDER && coverModel != null -> {
                    AsyncImage(
                        model = coverModel,
                        contentDescription = archive.fileName,
                        modifier = Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
                // Folder without cover
                archive.type == ArchiveType.FOLDER -> {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(MangaLensColors.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_file_folder),
                            contentDescription = null,
                            tint = MangaLensColors.primary,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                // Archive without cover
                else -> {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color(0x24787880)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_archive),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    ArchiveTypeBadge(archive.type, Modifier.align(Alignment.BottomEnd))
                }
            }

            if (showLockBadge) {
                LockBadge(modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp))
            }

            // Selection checkbox (top-start)
            val checkboxScale by animateFloatAsState(
                targetValue = if (isSelectionMode) 1f else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            )
            if (checkboxScale > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .graphicsLayer {
                            scaleX = checkboxScale
                            scaleY = checkboxScale
                            alpha = checkboxScale
                        },
                ) {
                    GridSelectionCheckbox(isSelected = isSelected)
                }
            }
        }

        Text(
            text = archive.displayName(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun GridSelectionCheckbox(isSelected: Boolean) {
    Icon(
        painter = painterResource(
            if (isSelected) Res.drawable.ic_check_on else Res.drawable.ic_check_off,
        ),
        contentDescription = null,
        tint = if (isSelected) Color.Unspecified else Color.White.copy(alpha = 0.5f),
        modifier = Modifier.size(22.dp),
    )
}

@Composable
private fun ArchiveTypeBadge(type: ArchiveType, modifier: Modifier = Modifier) {
    if (type !in setOf(ArchiveType.ZIP, ArchiveType.CBZ, ArchiveType.CBR)) return
    val label = when (type) {
        ArchiveType.CBZ -> "CBZ"
        ArchiveType.CBR -> "CBR"
        else -> "ZIP"
    }
    Box(
        modifier = modifier
            .padding(4.dp)
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(
            text = label,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}
