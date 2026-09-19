package com.manhwaviewer.data.ocr.eval

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtProvider
import android.util.Log
import org.junit.Test

/** What execution providers this device's ONNX Runtime build actually offers. */
class OrtProviderProbe {
    @Test
    fun listProviders() {
        val available = OrtEnvironment.getAvailableProviders()
        Log.i("ORT_PROBE", "available=${available.joinToString(",") { it.name }}")
        for (p in OrtProvider.entries) {
            Log.i("ORT_PROBE", "  ${p.name} present=${p in available}")
        }
    }
}
