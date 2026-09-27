package com.manhwaviewer.presentation.screen.recent

import com.manhwaviewer.domain.usecase.ClearAllRecentHistoryUseCase
import com.manhwaviewer.domain.usecase.ClearRecentHistoryUseCase
import com.manhwaviewer.domain.usecase.GetImageEntriesUseCase
import com.manhwaviewer.domain.usecase.GetRecentArchivesUseCase
import com.manhwaviewer.presentation.fake.FakeArchiveFileRepository
import com.manhwaviewer.presentation.fake.makeArchive
import com.manhwaviewer.presentation.screen.common.ListViewMode
import com.manhwaviewer.presentation.screen.common.SortField
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

@OptIn(ExperimentalCoroutinesApi::class)
class RecentViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepo: FakeArchiveFileRepository
    private lateinit var viewModel: RecentViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeArchiveFileRepository()
        viewModel = buildViewModel(fakeRepo)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(repo: FakeArchiveFileRepository): RecentViewModel = RecentViewModel(
        GetRecentArchivesUseCase(repo),
        GetImageEntriesUseCase(repo),
        ClearRecentHistoryUseCase(repo),
        ClearAllRecentHistoryUseCase(repo),
    )

    @Test
    fun `archives are loaded from use case when repository emits`() = runTest {
        fakeRepo.recentArchivesFlow.value = listOf(
            makeArchive(id = 1, fileName = "manga1.zip"),
            makeArchive(id = 2, fileName = "manga2.zip"),
        )
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(2, state.archives.size)
    }

    @Test
    fun `initial state has isLoading true and empty archives`() {
        // Fresh ViewModel before any emission
        val vm = buildViewModel(FakeArchiveFileRepository())
        val state = vm.state.value
        assertEquals(true, state.isLoading)
        assertEquals(emptyList(), state.archives)
    }

    @Test
    fun `isLoading becomes false after archives emitted`() = runTest {
        fakeRepo.recentArchivesFlow.value = listOf(makeArchive(id = 1))
        advanceUntilIdle()

        assertEquals(false, viewModel.state.value.isLoading)
    }

    @Test
    fun `OpenArchive sends NavigateToViewer side effect`() = runTest {
        val archive = makeArchive(id = 7, fileName = "read.zip")
        viewModel.onIntent(RecentIntent.OpenArchive(archive))
        advanceUntilIdle()

        val effect = viewModel.sideEffect.first()
        assertIs<RecentSideEffect.NavigateToViewer>(effect)
        assertEquals(7L, effect.archiveId)
    }

    @Test
    fun `SetSortField to NAME sorts archives alphabetically ascending`() = runTest {
        fakeRepo.recentArchivesFlow.value = listOf(
            makeArchive(id = 1, fileName = "Zebra.zip"),
            makeArchive(id = 2, fileName = "Apple.zip"),
        )
        advanceUntilIdle()

        // Default sort is DATE descending — switch to NAME ascending
        viewModel.onIntent(RecentIntent.SetSortField(SortField.NAME))
        // Also set ascending
        viewModel.onIntent(RecentIntent.ToggleSortDirection)
        advanceUntilIdle()

        val files = viewModel.state.value.archives
        assertEquals("Apple.zip", files[0].fileName)
        assertEquals("Zebra.zip", files[1].fileName)
    }

    @Test
    fun `SetSortField to DATE sorts by lastReadAt`() = runTest {
        fakeRepo.recentArchivesFlow.value = listOf(
            makeArchive(id = 1, fileName = "Newer.zip", lastReadAt = Instant.fromEpochMilliseconds(2000)),
            makeArchive(id = 2, fileName = "Older.zip", lastReadAt = Instant.fromEpochMilliseconds(1000)),
        )
        advanceUntilIdle()

        // Default is DATE descending — Newer should be first
        assertEquals("Newer.zip", viewModel.state.value.archives[0].fileName)
    }

    @Test
    fun `ToggleSortDirection reverses current order`() = runTest {
        fakeRepo.recentArchivesFlow.value = listOf(
            makeArchive(id = 1, fileName = "Newer.zip", lastReadAt = Instant.fromEpochMilliseconds(2000)),
            makeArchive(id = 2, fileName = "Older.zip", lastReadAt = Instant.fromEpochMilliseconds(1000)),
        )
        advanceUntilIdle()

        // Default: DATE descending → Newer first
        assertEquals("Newer.zip", viewModel.state.value.archives[0].fileName)

        viewModel.onIntent(RecentIntent.ToggleSortDirection)
        advanceUntilIdle()

        // After toggle: DATE ascending → Older first
        assertEquals("Older.zip", viewModel.state.value.archives[0].fileName)
    }

    @Test
    fun `ToggleViewMode switches from LIST to GRID`() = runTest {
        assertEquals(ListViewMode.LIST, viewModel.state.value.sortState.viewMode)

        viewModel.onIntent(RecentIntent.ToggleViewMode)
        advanceUntilIdle()

        assertEquals(ListViewMode.GRID, viewModel.state.value.sortState.viewMode)
    }

    @Test
    fun `ToggleViewMode switches from GRID back to LIST`() = runTest {
        viewModel.onIntent(RecentIntent.ToggleViewMode)
        advanceUntilIdle()
        assertEquals(ListViewMode.GRID, viewModel.state.value.sortState.viewMode)

        viewModel.onIntent(RecentIntent.ToggleViewMode)
        advanceUntilIdle()
        assertEquals(ListViewMode.LIST, viewModel.state.value.sortState.viewMode)
    }
}
