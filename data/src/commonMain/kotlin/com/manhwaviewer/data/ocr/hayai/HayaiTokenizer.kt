package com.manhwaviewer.data.ocr.hayai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Byte-level BPE detokeniser for Hayai OCR, matching the `tokenizer.json` beside the graphs.
 *
 * Ordinary tokens are not text: each character stands for one byte of the UTF-8 encoding, so a
 * single character can span two tokens. Decoding therefore collects bytes across the whole run
 * and converts once at the end — decoding token by token and concatenating produces mojibake.
 * Special tokens are literal and bypass that mapping.
 *
 * Construction takes the parsed vocabulary rather than a file, mirroring [MangaOcrTokenizer],
 * so reading the asset stays in the platform layer.
 */
class HayaiTokenizer(
    private val idToToken: Map<Int, String>,
    private val specialIds: Set<Int>,
) {

    private val tokenToId: Map<String, Int> by lazy { idToToken.entries.associate { (id, t) -> t to id } }

    /** The id a token string has, or null when the vocabulary does not carry it. */
    fun tokenIdOf(token: String): Int? = tokenToId[token]

    fun decode(ids: List<Int>, skipSpecialTokens: Boolean = true): String {
        val text = StringBuilder()
        var pending = ByteArray(0)
        var pendingSize = 0

        fun flush() {
            if (pendingSize > 0) {
                text.append(pending.copyOf(pendingSize).decodeToString())
                pendingSize = 0
            }
        }

        for (id in ids) {
            // Dropped by id: a skipped special token never has to resolve to a piece.
            if (id in specialIds && skipSpecialTokens) continue
            val token = idToToken[id]
                ?: throw IllegalArgumentException("token id $id is not in the vocabulary")
            if (id in specialIds) {
                flush()
                text.append(token)
                continue
            }
            if (pendingSize + token.length > pending.size) {
                pending = pending.copyOf(maxOf(pendingSize + token.length, pending.size * 2, 64))
            }
            for (ch in token) {
                val byte = BYTE_OF_CHAR[ch.code]
                    ?: throw IllegalArgumentException(
                        "token id $id contains ${ch.code}, which is not a byte-level character",
                    )
                pending[pendingSize++] = byte
            }
        }
        flush()
        return text.toString()
    }

    companion object {
        /**
         * The GPT-2 byte-level alphabet, inverted.
         *
         * Printable ASCII and two Latin-1 runs stand for themselves; every other byte is moved
         * above U+0100 so the vocabulary contains no control characters.
         */
        private val BYTE_OF_CHAR: Map<Int, Byte> = buildMap {
            val direct = (0x21..0x7E) + (0xA1..0xAC) + (0xAE..0xFF)
            for (b in direct) put(b, b.toByte())
            var next = 0x100
            for (b in 0..0xFF) {
                if (b !in direct) put(next++, b.toByte())
            }
        }

        fun fromTokenizerJson(json: String): HayaiTokenizer {
            val root = Json.parseToJsonElement(json).jsonObject
            val vocab = root["model"]?.jsonObject?.get("vocab")?.jsonObject
            require(vocab != null && vocab.isNotEmpty()) { "tokenizer.json has no model.vocab" }
            val added = root["added_tokens"]?.jsonArray.orEmpty().map { it.jsonObject }
            val idToToken = HashMap<Int, String>(vocab.size + added.size)
            for ((token, id) in vocab) idToToken[id.jsonPrimitive.int] = token
            // hayai's <bos>/<eos>/<pad> live only here, above the vocabulary's range.
            for (entry in added) {
                val id = entry["id"]?.jsonPrimitive?.int ?: continue
                val content = entry["content"]?.jsonPrimitive?.content ?: continue
                idToToken[id] = content
            }
            val specialIds = added
                .filter { it["special"]?.jsonPrimitive?.boolean == true }
                .mapNotNull { it["id"]?.jsonPrimitive?.int }
                .toSet()
            return HayaiTokenizer(idToToken, specialIds)
        }
    }
}
