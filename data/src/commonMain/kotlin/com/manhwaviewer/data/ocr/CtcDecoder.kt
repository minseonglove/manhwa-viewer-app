package com.manhwaviewer.data.ocr

object CtcDecoder {

    fun greedyDecode(
        output: FloatArray,
        seqLen: Int,
        vocabSize: Int,
    ): List<Int> {
        val rawIndices = IntArray(seqLen)
        for (t in 0 until seqLen) {
            val offset = t * vocabSize
            var maxIdx = 0
            var maxVal = output[offset]
            for (v in 1 until vocabSize) {
                val value = output[offset + v]
                if (value > maxVal) {
                    maxVal = value
                    maxIdx = v
                }
            }
            rawIndices[t] = maxIdx
        }

        val collapsed = mutableListOf<Int>()
        var prev = -1
        for (idx in rawIndices) {
            if (idx != prev) {
                if (idx != 0) collapsed.add(idx)
                prev = idx
            }
        }
        return collapsed
    }
}
