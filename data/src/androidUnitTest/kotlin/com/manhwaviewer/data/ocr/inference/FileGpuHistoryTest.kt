package com.manhwaviewer.data.ocr.inference

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileGpuHistoryTest {

    private val root: File = Files.createTempDirectory("litert-gpu").toFile()
    private val dir = File(root, "model.build1")

    /** Pids whose process the platform says crashed; any other ended some other way. */
    private val crashed = mutableSetOf<Int>()

    private fun history(pid: Int, dir: File = this.dir) = FileGpuHistory(dir, pid) { it in crashed }

    /** A process that started a GPU attempt and never came back from it. */
    private fun diedDuringAttempt(pid: Int, crash: Boolean) {
        history(pid).attemptStarted()
        if (crash) crashed += pid
    }

    private fun programCache() = File(dir, "model_mldrift_program_cache.bin")

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun aReturnedAttemptLeavesNothingToHoldAgainstTheGpu() {
        history(1).apply { attemptStarted(); attemptReturned() }
        assertNull(history(2).skipReason())
    }

    @Test
    fun oneCrashStillGetsAnotherTry() {
        diedDuringAttempt(1, crash = true)
        assertNull(history(2).skipReason())
    }

    @Test
    fun twoCrashesSkipTheGpu() {
        diedDuringAttempt(1, crash = true)
        diedDuringAttempt(2, crash = true)
        assertNotNull(history(3).skipReason())
    }

    @Test
    fun attemptsTheUserCutShortAreNotHeldAgainstTheGpuUntilTheyPileUp() {
        diedDuringAttempt(1, crash = false)
        diedDuringAttempt(2, crash = false)
        diedDuringAttempt(3, crash = false)
        assertNull(history(4).skipReason(), "a user closing the app during a slow first compile is not a GPU fault")

        diedDuringAttempt(4, crash = false)
        assertNotNull(history(5).skipReason(), "a GPU that hangs is only ever stopped by the user")
    }

    @Test
    fun aSuccessClearsEarlierCrashes() {
        diedDuringAttempt(1, crash = true)
        history(2).apply { attemptStarted(); attemptReturned() }
        diedDuringAttempt(3, crash = true)
        assertNull(history(4).skipReason())
    }

    @Test
    fun aRejectedFreshCompileSkipsTheGpuOnThisBuild() {
        history(1).apply { attemptStarted(); attemptReturned(); rejected() }
        assertNotNull(history(2).skipReason())
    }

    @Test
    fun aRejectionWithCachedProgramsDropsThemAndTriesAgain() {
        dir.mkdirs()
        programCache().writeBytes(byteArrayOf(1, 2, 3))

        history(1).apply { attemptStarted(); attemptReturned(); rejected() }

        assertFalse(programCache().exists(), "the cached programs are the likelier culprit")
        assertNull(history(2).skipReason(), "the next launch compiles afresh")
    }

    @Test
    fun aNewBuildStartsWithACleanSlate() {
        val old = FileGpuHistory.directory(root, "model", "build1")
        history(1, old).apply { attemptStarted(); attemptReturned(); rejected() }
        diedDuringAttempt(2, crash = true)

        val fresh = FileGpuHistory.directory(root, "model", "build2")

        assertNull(history(3, fresh).skipReason())
        assertFalse(old.exists(), "an old build's program cache is as large as the model's weights")
    }

    @Test
    fun theDirectoryKeepsOtherModelsAndDropsFilesFromBeforeDirectories() {
        val other = File(root, "other.build1").apply { mkdirs() }
        val legacy = File(root, "model-1_mldrift_program_cache.bin").apply { writeBytes(byteArrayOf(1)) }

        val current = FileGpuHistory.directory(root, "model", "build1")

        assertTrue(current.isDirectory)
        assertTrue(other.isDirectory, "another model's cache is not this model's to drop")
        assertFalse(legacy.exists())
        assertEquals(setOf("model.build1", "other.build1"), root.list()!!.toSet())
    }

    @Test
    fun aDirectoryThatCannotBeWrittenDoesNotStopTheAttempt() {
        val blocked = File(root, "blocked").apply { writeText("a file where the directory should be") }
        val history = history(1, blocked)

        history.attemptStarted()
        history.attemptReturned()
        history.rejected()

        assertNull(history.skipReason())
    }
}
