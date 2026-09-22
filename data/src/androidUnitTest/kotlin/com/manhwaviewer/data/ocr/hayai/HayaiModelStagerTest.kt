package com.manhwaviewer.data.ocr.hayai

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HayaiModelStagerTest {

    private val root: File = Files.createTempDirectory("hayai-stager").toFile()
    private val target = File(root, "hayai")
    private val opened = mutableListOf<String>()

    private fun content(name: String) = "bytes of $name".toByteArray()

    private val assets: (String) -> InputStream = { path ->
        opened += path
        ByteArrayInputStream(content(path.substringAfterLast('/')))
    }

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun stagesEveryModelFileSideBySide() {
        val dir = HayaiModelStager.stage(assets, target, version = "v1")

        assertEquals(target, dir)
        for (name in HayaiModelStager.FILES) {
            assertContentEquals(content(name), File(target, name).readBytes(), name)
        }
        assertEquals(HayaiModelStager.FILES.map { "hayai/$it" }, opened)
    }

    @Test
    fun doesNotCopyAgainOnceStaged() {
        HayaiModelStager.stage(assets, target, version = "v1")
        opened.clear()

        HayaiModelStager.stage(assets, target, version = "v1")

        assertTrue(opened.isEmpty(), "reopened $opened")
    }

    @Test
    fun finishesACopyThatWasInterrupted() {
        HayaiModelStager.stage(assets, target, version = "v1")
        val lost = HayaiModelStager.FILES.first()
        File(target, lost).delete()
        File(target, "$lost.tmp").writeBytes(byteArrayOf(1, 2, 3))
        File(target, HayaiModelStager.VERSION_FILE).delete()
        opened.clear()

        HayaiModelStager.stage(assets, target, version = "v1")

        assertContentEquals(content(lost), File(target, lost).readBytes())
        assertFalse(File(target, "$lost.tmp").exists())
    }

    @Test
    fun replacesFilesStagedFromAnotherModelVersion() {
        HayaiModelStager.stage(assets, target, version = "v1")
        val stale = File(target, HayaiModelStager.FILES.first()).apply { writeBytes(byteArrayOf(9)) }
        opened.clear()

        HayaiModelStager.stage(assets, target, version = "v2")

        assertContentEquals(content(stale.name), stale.readBytes())
        assertEquals(HayaiModelStager.FILES.size, opened.size)
        assertEquals("v2", File(target, HayaiModelStager.VERSION_FILE).readText())
    }

    @Test
    fun removesFilesTheNewModelSetNoLongerHas() {
        HayaiModelStager.stage(assets, target, version = "v1")
        val leftover = File(target, "hayai_encoder.onnx").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        HayaiModelStager.stage(assets, target, version = "v2")

        assertFalse(leftover.exists(), "an 87 MB graph from the previous set would stay in the cache forever")
        assertEquals(HayaiModelStager.FILES.toSet() + HayaiModelStager.VERSION_FILE, target.list()!!.toSet())
    }
}
