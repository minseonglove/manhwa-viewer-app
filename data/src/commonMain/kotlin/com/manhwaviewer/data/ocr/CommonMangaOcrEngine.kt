package com.manhwaviewer.data.ocr

import com.manhwaviewer.data.ocr.image.NormRange
import com.manhwaviewer.data.ocr.image.decodeImage
import com.manhwaviewer.data.ocr.image.resizeAndPadToFloatArray
import com.manhwaviewer.data.ocr.inference.OcrDecoderRunner
import com.manhwaviewer.data.ocr.inference.OcrEncoderRunner

class CommonMangaOcrEngine(
    private val encoder: OcrEncoderRunner,
    private val decoder: OcrDecoderRunner,
    private val vocabLoader: () -> List<String>,
) : MangaOcrEngine {

    private var tokenizer: MangaOcrTokenizer? = null
    private var initialized = false

    override suspend fun recognizeText(imageBytes: ByteArray): String {
        if (!initialized) {
            encoder.initialize()
            decoder.initialize()
            tokenizer = MangaOcrTokenizer(vocabLoader())
            initialized = true
        }

        val image = decodeImage(imageBytes)
        val pixelValues = resizeAndPadToFloatArray(
            image,
            targetSize = MangaOcrConfig.IMAGE_SIZE,
            normRange = NormRange.MINUS_ONE_ONE,
            padColor = 1.0f,
        )

        val encoderOutput = encoder.run(pixelValues)

        val tokenIds = try {
            val initialKvCache = decoder.beginSequence(encoderOutput)
            beamSearchDecodeWithKvCache(
                beamWidth = MangaOcrConfig.BEAM_WIDTH,
                maxLength = MangaOcrConfig.MAX_LENGTH,
                vocabSize = MangaOcrConfig.VOCAB_SIZE,
                startTokenId = MangaOcrConfig.DECODER_START_TOKEN_ID,
                eosTokenId = MangaOcrConfig.EOS_TOKEN_ID,
                initialKvCache = initialKvCache,
            ) { tokenId, kvCache ->
                decoder.stepWithKvCache(tokenId, kvCache)
            }
        } catch (_: UnsupportedOperationException) {
            // Fallback for platforms without KV cache (iOS)
            beamSearchDecode(
                beamWidth = MangaOcrConfig.BEAM_WIDTH,
                maxLength = MangaOcrConfig.MAX_LENGTH,
                vocabSize = MangaOcrConfig.VOCAB_SIZE,
                startTokenId = MangaOcrConfig.DECODER_START_TOKEN_ID,
                eosTokenId = MangaOcrConfig.EOS_TOKEN_ID,
            ) { inputIds, seqLen ->
                decoder.run(inputIds, seqLen, encoderOutput)
            }
        }

        return tokenizer!!.decode(tokenIds)
    }

    override fun close() {
        encoder.close()
        decoder.close()
        tokenizer = null
        initialized = false
    }
}
