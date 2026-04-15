package com.manhwaviewer.presentation.screen.home

import com.manhwaviewer.domain.usecase.GetDeviceImagesUseCase
import com.manhwaviewer.domain.usecase.GetImageEntriesUseCase
import com.manhwaviewer.domain.usecase.GetRecentArchivesUseCase
import com.manhwaviewer.presentation.fake.FakeArchiveFileRepository
import com.manhwaviewer.presentation.fake.FakeImageFileRepository
import com.manhwaviewer.presentation.fake.makeArchive
import com.manhwaviewer.presentation.fake.makeImageFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeArchiveRepo: FakeArchiveFileRepository
    private lateinit var fakeImageRepo: FakeImageFileRepository
    private lateinit var viewModel: HomeViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeArchiveRepo = FakeArchiveFileRepository()
        fakeImageRepo = FakeImageFileRepository()
        viewModel = HomeViewModel(
            GetRecentArchivesUseCase(fakeArchiveRepo),
            GetDeviceImagesUseCase(fakeImageRepo),
            GetImageEntriesUseCase(fakeArchiveRepo),
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has zero counts and empty lists`() = runTest {
        val state = viewModel.state.value
        assertEquals(0, state.imageCount)
        assertEquals(emptyList(), state.recentArchives)
        assertNull(state.continueReading)
    }

    @Test
    fun `state updates recentArchives when repository emits`() = runTest {
        val archives = listOf(
            makeArchive(id = 1, lastReadAt = Instant.fromEpochMilliseconds(1000)),
            makeArchive(id = 2, lastReadAt = Instant.fromEpochMilliseconds(2000)),
        )
        fakeArchiveRepo.recentArchivesFlow.value = archives
        advanceUntilIdle()
        assertEquals(2, viewModel.state.value.recentArchives.size)
    }

    @Test
    fun `state updates imageCount when repository emits`() = runTest {
        fakeImageRepo.allImagesFlow.value = listOf(makeImageFile(id = 1), makeImageFile(id = 2))
        advanceUntilIdle()
        assertEquals(2, viewModel.state.value.imageCount)
    }

    @Test
    fun `continueReading is set for unfinished archive`() = runTest {
        val unfinished = makeArchive(
            id = 1,
            lastReadAt = Instant.fromEpochMilliseconds(1000),
        ).copy(totalPages = 100, lastReadIndex = 50)
        fakeArchiveRepo.recentArchivesFlow.value = listOf(unfinished)
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.continueReading)
        assertEquals(1L, viewModel.state.value.continueReading?.id)
    }

    @Test
    fun `continueReading is null for fully read archive`() = runTest {
        val finished = makeArchive(
            id = 1,
            lastReadAt = Instant.fromEpochMilliseconds(1000),
        ).copy(totalPages = 100, lastReadIndex = 99)
        fakeArchiveRepo.recentArchivesFlow.value = listOf(finished)
        advanceUntilIdle()
        assertNull(viewModel.state.value.continueReading)
    }

    @Test
    fun `NavigateToFiles intent sends NavigateToFiles side effect`() = runTest {
        viewModel.onIntent(HomeIntent.NavigateToFiles)
        advanceUntilIdle()
        val effect = viewModel.sideEffect.first()
        assertIs<HomeSideEffect.NavigateToFiles>(effect)
    }

    @Test
    fun `NavigateToRecent intent sends NavigateToRecent side effect`() = runTest {
        viewModel.onIntent(HomeIntent.NavigateToRecent)
        advanceUntilIdle()
        val effect = viewModel.sideEffect.first()
        assertIs<HomeSideEffect.NavigateToRecent>(effect)
    }

    @Test
    fun `NavigateToSettings intent sends NavigateToSettings side effect`() = runTest {
        viewModel.onIntent(HomeIntent.NavigateToSettings)
        advanceUntilIdle()
        val effect = viewModel.sideEffect.first()
        assertIs<HomeSideEffect.NavigateToSettings>(effect)
    }

    @Test
    fun `NavigateToImages intent sends NavigateToImages side effect`() = runTest {
        viewModel.onIntent(HomeIntent.NavigateToImages)
        advanceUntilIdle()
        val effect = viewModel.sideEffect.first()
        assertIs<HomeSideEffect.NavigateToImages>(effect)
    }

    @Test
    fun `NavigateToVault intent sends NavigateToVault side effect`() = runTest {
        viewModel.onIntent(HomeIntent.NavigateToVault)
        advanceUntilIdle()
        val effect = viewModel.sideEffect.first()
        assertIs<HomeSideEffect.NavigateToVault>(effect)
    }

    @Test
    fun `OpenArchive intent sends NavigateToViewer side effect`() = runTest {
        viewModel.onIntent(HomeIntent.OpenArchive(42L))
        advanceUntilIdle()
        val effect = viewModel.sideEffect.first()
        assertIs<HomeSideEffect.NavigateToViewer>(effect)
        assertEquals(42L, effect.archiveId)
    }
}
