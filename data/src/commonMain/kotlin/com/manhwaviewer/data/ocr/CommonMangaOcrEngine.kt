package com.manhwaviewer.data.ocr

import com.manhwaviewer.data.ocr.image.NormRange
import com.manhwaviewer.data.ocr.image.PlatformImage
import com.manhwaviewer.data.ocr.image.computeBorderAverageColor
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

    private fun ensureInitialized() {
        if (!initialized) {
            encoder.initialize()
            decoder.initialize()
            tokenizer = MangaOcrTokenizer(vocabLoader())
            initialized = true
        }
    }

    override suspend fun recognizeText(imageBytes: ByteArray): String {
        ensureInitialized()
        return recognizeText(decodeImage(imageBytes))
    }

    override suspend fun recognizeText(image: PlatformImage): String {
        ensureInitialized()

        val pixelValues = resizeAndPadToFloatArray(
            image,
            targetSize = MangaOcrConfig.IMAGE_SIZE,
            normRange = NormRange.MINUS_ONE_ONE,
            padColor = computeBorderAverageColor(image),
        )

        val encoderOutput = encoder.runToEncoderOutput(pixelValues)

        val tokenIds = try {
            val initialKvCache = decoder.beginSequence(encoderOutput)
            if (MangaOcrConfig.BEAM_WIDTH <= 1) {
                greedyDecodeWithKvCache(
                    maxLength = MangaOcrConfig.MAX_LENGTH,
                    vocabSize = MangaOcrConfig.VOCAB_SIZE,
                    startTokenId = MangaOcrConfig.DECODER_START_TOKEN_ID,
                    eosTokenId = MangaOcrConfig.EOS_TOKEN_ID,
                    initialKvCache = initialKvCache,
                ) { tokenId, kvCache ->
                    decoder.stepWithKvCache(tokenId, kvCache)
                }
            } else {
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
            }
        } catch (_: UnsupportedOperationException) {
            val fp32Output = encoder.run(pixelValues)
            beamSearchDecode(
                beamWidth = MangaOcrConfig.BEAM_WIDTH,
                maxLength = MangaOcrConfig.MAX_LENGTH,
                vocabSize = MangaOcrConfig.VOCAB_SIZE,
                startTokenId = MangaOcrConfig.DECODER_START_TOKEN_ID,
                eosTokenId = MangaOcrConfig.EOS_TOKEN_ID,
            ) { inputIds, seqLen ->
                decoder.run(inputIds, seqLen, fp32Output)
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
