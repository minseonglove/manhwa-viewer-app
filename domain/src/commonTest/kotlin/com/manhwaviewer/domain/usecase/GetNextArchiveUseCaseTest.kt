package com.manhwaviewer.domain.usecase

import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.domain.entity.ArchiveType
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GetNextArchiveUseCaseTest {

    private fun archive(
        fileName: String,
        type: ArchiveType = ArchiveType.ZIP,
        hasImages: Boolean = true,
    ) = ArchiveFile(
        id = 0,
        filePath = "/manga/$fileName",
        fileName = fileName,
        type = type,
        totalPages = 10,
        addedAt = Instant.fromEpochMilliseconds(0),
        hasImages = hasImages,
    )

    private fun createUseCase(siblings: List<ArchiveFile>): GetNextArchiveUseCase {
        val repo = FakeArchiveFileRepository().apply {
            scanResult = siblings
        }
        return GetNextArchiveUseCase(repo)
    }

    @Test
    fun returnsNextArchiveInNaturalOrder() = runTest {
        val siblings = listOf(
            archive("vol1.zip"),
            archive("vol2.zip"),
            archive("vol3.zip"),
        )
        val useCase = createUseCase(siblings)
        val result = useCase("/manga/vol1.zip")
        assertEquals("vol2.zip", result?.fileName)
    }

    @Test
    fun returnsNullForLastArchive() = runTest {
        val siblings = listOf(
            archive("vol1.zip"),
            archive("vol2.zip"),
        )
        val useCase = createUseCase(siblings)
        assertNull(useCase("/manga/vol2.zip"))
    }

    @Test
    fun folderTakesPriorityOverSameBasenameArchive() = runTest {
        val siblings = listOf(
            archive("vol1.zip"),
            archive("vol2", type = ArchiveType.FOLDER),
            archive("vol2.zip"),
        )
        val useCase = createUseCase(siblings)
        val result = useCase("/manga/vol1.zip")
        assertEquals("vol2", result?.fileName)
        assertEquals(ArchiveType.FOLDER, result?.type)
    }

    @Test
    fun returnsNullWhenCurrentNotFound() = runTest {
        val siblings = listOf(archive("vol1.zip"))
        val useCase = createUseCase(siblings)
        assertNull(useCase("/manga/notexist.zip"))
    }

    @Test
    fun skipsArchivesWithoutImages() = runTest {
        val siblings = listOf(
            archive("vol1.zip"),
            archive("vol2.zip", hasImages = false),
            archive("vol3.zip"),
        )
        val useCase = createUseCase(siblings)
        val result = useCase("/manga/vol1.zip")
        assertEquals("vol3.zip", result?.fileName)
    }
}
