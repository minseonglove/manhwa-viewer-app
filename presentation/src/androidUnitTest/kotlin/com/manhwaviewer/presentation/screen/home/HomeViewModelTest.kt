package com.manhwaviewer.presentation.screen.home

import com.manhwaviewer.domain.usecase.GetDeviceImagesUseCase
import com.manhwaviewer.domain.usecase.GetRecentArchivesCountUseCase
import com.manhwaviewer.domain.usecase.GetVaultFilesUseCase
import com.manhwaviewer.presentation.fake.FakeArchiveFileRepository
import com.manhwaviewer.presentation.fake.FakeImageFileRepository
import com.manhwaviewer.presentation.fake.makeImageFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

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
            GetRecentArchivesCountUseCase(fakeArchiveRepo),
            GetDeviceImagesUseCase(fakeImageRepo),
            GetVaultFilesUseCase(fakeArchiveRepo),
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has zero counts`() = runTest {
        val state = viewModel.state.value
        assertEquals(0, state.recentCount)
        assertEquals(0, state.imageCount)
        assertEquals(0, state.vaultFolderCount)
    }

    @Test
    fun `state updates recentCount when repository emits`() = runTest {
        fakeArchiveRepo.recentArchivesCountFlow.value = 3
        advanceUntilIdle()
        assertEquals(3, viewModel.state.value.recentCount)
    }

    @Test
    fun `state updates imageCount when repository emits`() = runTest {
        fakeImageRepo.allImagesFlow.value = listOf(makeImageFile(id = 1), makeImageFile(id = 2))
        advanceUntilIdle()
        assertEquals(2, viewModel.state.value.imageCount)
    }

    @Test
    fun `state updates vaultFolderCount when repository emits`() = runTest {
        fakeArchiveRepo.vaultFilesFlow.value = listOf(com.manhwaviewer.presentation.fake.makeArchive(id = 10))
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.vaultFolderCount)
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
}
