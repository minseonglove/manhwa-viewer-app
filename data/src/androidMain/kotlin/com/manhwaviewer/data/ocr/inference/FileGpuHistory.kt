package com.manhwaviewer.data.ocr.inference

import java.io.File
import java.io.IOException

/**
 * [GpuHistory] kept in one directory per model and per app-and-OS build — the directory LiteRT also
 * writes that model's GPU program cache into.
 *
 * Keying the directory by build is what keeps every verdict recoverable: an app update can bring a
 * new model and an OS update a new GPU driver, and each deserves a fresh look, with a fresh compile,
 * since programs cached for the old model or driver are exactly what would be wrong.
 *
 * Writes are best effort. A full disk must not stop OCR; it only costs the next launch what this
 * one learned.
 */
class FileGpuHistory(
    val dir: File,
    private val pid: Int,
    /** Whether process [pid] ended in a crash; true when that cannot be told. */
    private val diedOfCrash: (pid: Int) -> Boolean,
) : GpuHistory {

    private var hadProgramCache = false

    override fun skipReason(): String? {
        if (File(dir, REJECTED).exists()) return "the GPU was rejected earlier on this app and OS build"
        val unfinished = unfinishedAttempts()
        val crashes = unfinished.count(diedOfCrash)
        if (crashes >= MAX_CRASHES || unfinished.size >= MAX_UNFINISHED) {
            return "${unfinished.size} earlier GPU attempts never returned, $crashes of them in a crash"
        }
        return null
    }

    override fun attemptStarted() {
        hadProgramCache = programCache().isNotEmpty()
        write(ATTEMPTS, (unfinishedAttempts() + pid).joinToString("\n"))
    }

    override fun attemptReturned() {
        File(dir, ATTEMPTS).delete()
    }

    /**
     * Rejected with programs an earlier launch cached: those programs are the likelier culprit (a
     * write cut short, a driver they were not built for), so they go and the next launch compiles
     * afresh. Rejected on a fresh compile: the GPU itself gets it wrong, so it is not tried again
     * on this build.
     */
    override fun rejected() {
        if (hadProgramCache) programCache().forEach(File::deleteRecursively) else write(REJECTED, "")
    }

    private fun unfinishedAttempts(): List<Int> =
        try {
            File(dir, ATTEMPTS).takeIf(File::isFile)?.readLines()?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()
        } catch (e: IOException) {
            emptyList()
        }

    private fun programCache(): List<File> = dir.listFiles()?.filter { it.name !in OWN_FILES }.orEmpty()

    private fun write(name: String, text: String) {
        try {
            dir.mkdirs()
            File(dir, name).writeText(text)
        } catch (e: IOException) {
            println("OCR | could not record GPU history in $dir: ${e.message}")
        }
    }

    companion object {
        /** A crash in compile is deterministic enough that a second one settles it. */
        const val MAX_CRASHES = 2

        /**
         * Attempts the user or the low-memory killer cut short are not held against the GPU, but a
         * GPU that hangs is only ever stopped that way, so past this many they count too.
         */
        const val MAX_UNFINISHED = 4

        private const val ATTEMPTS = "attempts"
        private const val REJECTED = "rejected"
        private val OWN_FILES = setOf(ATTEMPTS, REJECTED)

        /**
         * The directory for model [name] on [build] under [root]. Directories of the same model on
         * earlier builds are deleted, since each holds a program cache as large as the model's
         * weights, and so are files directly under [root], left from before models had directories.
         */
        fun directory(root: File, name: String, build: String): File {
            val current = "$name.$build"
            root.listFiles()
                ?.filter { it.isFile || (it.name.startsWith("$name.") && it.name != current) }
                ?.forEach { it.deleteRecursively() }
            return File(root, current).apply { mkdirs() }
        }
    }
}
