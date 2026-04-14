package com.manhwaviewer.presentation.screen.filebrowser

import com.manhwaviewer.domain.entity.ArchiveType
import com.manhwaviewer.domain.usecase.GetImageEntriesUseCase
import com.manhwaviewer.domain.usecase.ScanArchiveEntriesUseCase
import com.manhwaviewer.domain.usecase.ScanLocalFilesUseCase
import com.manhwaviewer.domain.usecase.UpsertArchiveUseCase
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
class FileBrowserViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepo: FakeArchiveFileRepository
    private lateinit var viewModel: FileBrowserViewModel

    private val defaultPath = "/storage/emulated/0/Comics"

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeArchiveFileRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(): FileBrowserViewModel = FileBrowserViewModel(
        scanLocalFilesUseCase = ScanLocalFilesUseCase(fakeRepo),
        upsertArchiveUseCase = UpsertArchiveUseCase(fakeRepo),
        scanArchiveEntriesUseCase = ScanArchiveEntriesUseCase(fakeRepo),
        getImageEntriesUseCase = GetImageEntriesUseCase(fakeRepo),
        defaultScanPath = defaultPath,
        ioDispatcher = testDispatcher,
    )

    @Test
    fun `initial path loads files from default scan path`() = runTest {
        fakeRepo.scannedFiles = listOf(
            makeArchive(id = 1, fileName = "a.zip"),
            makeArchive(id = 2, fileName = "b.zip"),
        )
        viewModel = buildViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(defaultPath, state.currentPath)
        assertEquals(2, state.files.size)
    }

    @Test
    fun `NavigateToFolder updates currentPath and files`() = runTest {
        viewModel = buildViewModel()
        advanceUntilIdle()

        val newPath = "/storage/emulated/0/Comics/Volume1"
        fakeRepo.scannedFiles = listOf(makeArchive(id = 3, fileName = "ch1.zip"))
        viewModel.onIntent(FileBrowserIntent.NavigateToFolder(newPath))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(newPath, state.currentPath)
        assertEquals(1, state.files.size)
        assertEquals("ch1.zip", state.files[0].fileName)
    }

    @Test
    fun `GoBack navigates to parent folder`() = runTest {
        viewModel = buildViewModel()
        advanceUntilIdle()

        val childPath = "$defaultPath/Volume1"
        fakeRepo.scannedFiles = emptyList()
        viewModel.onIntent(FileBrowserIntent.NavigateToFolder(childPath))
        advanceUntilIdle()
        assertEquals(childPath, viewModel.state.value.currentPath)

        viewModel.onIntent(FileBrowserIntent.GoBack)
        advanceUntilIdle()
        assertEquals(defaultPath, viewModel.state.value.currentPath)
    }

    @Test
    fun `SetSortField re-sorts files by name`() = runTest {
        fakeRepo.scannedFiles = listOf(
            makeArchive(fileName = "Zebra.zip", addedAt = Instant.fromEpochMilliseconds(2000)),
            makeArchive(fileName = "Apple.zip", addedAt = Instant.fromEpochMilliseconds(1000)),
        )
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.onIntent(FileBrowserIntent.SetSortField(SortField.NAME))
        advanceUntilIdle()

        val files = viewModel.state.value.files
        assertEquals("Apple.zip", files[0].fileName)
        assertEquals("Zebra.zip", files[1].fileName)
    }

    @Test
    fun `SetSortField re-sorts files by date`() = runTest {
        fakeRepo.scannedFiles = listOf(
            makeArchive(fileName = "Newer.zip", addedAt = Instant.fromEpochMilliseconds(2000)),
            makeArchive(fileName = "Older.zip", addedAt = Instant.fromEpochMilliseconds(1000)),
        )
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.onIntent(FileBrowserIntent.SetSortField(SortField.DATE))
        advanceUntilIdle()

        val files = viewModel.state.value.files
        assertEquals("Older.zip", files[0].fileName)
        assertEquals("Newer.zip", files[1].fileName)
    }

    @Test
    fun `ToggleSortDirection reverses order`() = runTest {
        fakeRepo.scannedFiles = listOf(
            makeArchive(fileName = "Apple.zip"),
            makeArchive(fileName = "Zebra.zip"),
        )
        viewModel = buildViewModel()
        advanceUntilIdle()

        // Default ascending NAME — Apple first
        assertEquals("Apple.zip", viewModel.state.value.files[0].fileName)

        viewModel.onIntent(FileBrowserIntent.ToggleSortDirection)
        advanceUntilIdle()

        // Descending — Zebra first
        assertEquals("Zebra.zip", viewModel.state.value.files[0].fileName)
    }

    @Test
    fun `ToggleViewMode switches from LIST to GRID`() = runTest {
        viewModel = buildViewModel()
        advanceUntilIdle()

        assertEquals(ListViewMode.LIST, viewModel.state.value.sortState.viewMode)

        viewModel.onIntent(FileBrowserIntent.ToggleViewMode)
        advanceUntilIdle()

        assertEquals(ListViewMode.GRID, viewModel.state.value.sortState.viewMode)
    }

    @Test
    fun `ToggleViewMode switches from GRID back to LIST`() = runTest {
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.onIntent(FileBrowserIntent.ToggleViewMode)
        advanceUntilIdle()
        assertEquals(ListViewMode.GRID, viewModel.state.value.sortState.viewMode)

        viewModel.onIntent(FileBrowserIntent.ToggleViewMode)
        advanceUntilIdle()
        assertEquals(ListViewMode.LIST, viewModel.state.value.sortState.viewMode)
    }

    @Test
    fun `natural sort orders ch2 before ch10`() = runTest {
        fakeRepo.scannedFiles = listOf(
            makeArchive(fileName = "ch10.zip"),
            makeArchive(fileName = "ch2.zip"),
            makeArchive(fileName = "ch1.zip"),
        )
        viewModel = buildViewModel()
        advanceUntilIdle()

        val files = viewModel.state.value.files
        assertEquals("ch1.zip", files[0].fileName)
        assertEquals("ch2.zip", files[1].fileName)
        assertEquals("ch10.zip", files[2].fileName)
    }

    @Test
    fun `OpenFile sends NavigateToViewer side effect with upserted id`() = runTest {
        viewModel = buildViewModel()
        advanceUntilIdle()

        fakeRepo.nextUpsertId = 42L
        val archive = makeArchive(id = 0, fileName = "book.zip")
        viewModel.onIntent(FileBrowserIntent.OpenFile(archive))
        advanceUntilIdle()

        val effect = viewModel.sideEffect.first()
        assertIs<FileBrowserSideEffect.NavigateToViewer>(effect)
        assertEquals(42L, effect.archiveId)
    }

    @Test
    fun `scan error results in empty file list`() = runTest {
        // Override scanLocalFiles to throw — use a special fake
        val errorRepo = object : FakeArchiveFileRepository() {
            override suspend fun scanLocalFiles(directoryPath: String): List<com.manhwaviewer.domain.entity.ArchiveFile> {
                throw RuntimeException("Permission denied")
            }
        }
        viewModel = FileBrowserViewModel(
            scanLocalFilesUseCase = ScanLocalFilesUseCase(errorRepo),
            upsertArchiveUseCase = UpsertArchiveUseCase(errorRepo),
            scanArchiveEntriesUseCase = ScanArchiveEntriesUseCase(errorRepo),
            getImageEntriesUseCase = GetImageEntriesUseCase(errorRepo),
            defaultScanPath = defaultPath,
            ioDispatcher = testDispatcher,
        )
        advanceUntilIdle()

        assertEquals(emptyList(), viewModel.state.value.files)
        assertEquals(false, viewModel.state.value.isLoading)
    }
}
