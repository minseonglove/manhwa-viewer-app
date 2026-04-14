package com.manhwaviewer.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GetRecentArchivesCountUseCaseTest {
    @Test
    fun returnsCountFromRepository() = runTest {
        val repo = FakeArchiveFileRepository()
        repo.recentCount.value = 5
        val useCase = GetRecentArchivesCountUseCase(repo)
        assertEquals(5, useCase().first())
    }

    @Test
    fun returnsZeroWhenEmpty() = runTest {
        val repo = FakeArchiveFileRepository()
        val useCase = GetRecentArchivesCountUseCase(repo)
        assertEquals(0, useCase().first())
    }
}

class GetRecentArchivesUseCaseTest {
    @Test
    fun returnsRecentArchivesFromRepository() = runTest {
        val repo = FakeArchiveFileRepository()
        val archives = listOf(
            FakeArchiveFileRepository.sampleArchive(1L, "recent1.cbz"),
        )
        repo.recentArchives.value = archives
        val useCase = GetRecentArchivesUseCase(repo)
        assertEquals(1, useCase().first().size)
    }
}
