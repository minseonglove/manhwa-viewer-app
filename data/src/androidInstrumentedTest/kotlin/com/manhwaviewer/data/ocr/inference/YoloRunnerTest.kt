package com.manhwaviewer.data.ocr.inference

import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.litert.CompiledModel
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test

class YoloRunnerTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun initializingAgainKeepsTheModelAlreadyOpen() {
        val yolo = YoloRunner(context)
        try {
            yolo.initialize()
            val first = openModel(yolo)
            assertNotNull(first)

            yolo.initialize()

            assertSame("a second initialize compiled another model and dropped the first unclosed", first, openModel(yolo))
        } finally {
            yolo.close()
        }
    }

    /** Read reflectively: the runner exposes no handle to its model, and should not for this. */
    private fun openModel(yolo: YoloRunner): CompiledModel? =
        YoloRunner::class.java.getDeclaredField("model").apply { isAccessible = true }.get(yolo) as CompiledModel?
}
