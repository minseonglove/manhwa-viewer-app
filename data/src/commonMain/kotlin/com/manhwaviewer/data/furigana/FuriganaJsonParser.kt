package com.manhwaviewer.data.furigana

import com.manhwaviewer.domain.entity.FuriganaToken
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class FuriganaTokenDto(
    val surface: String,
    val reading: String,
    @SerialName("is_kanji") val isKanji: Boolean,
)

object FuriganaJsonParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(jsonString: String): List<FuriganaToken> {
        if (jsonString.isBlank()) return emptyList()
        return try {
            json.decodeFromString<List<FuriganaTokenDto>>(jsonString)
                .map { FuriganaToken(it.surface, it.reading, it.isKanji) }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
