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
                .flatMap(::splitOkurigana)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * 한자+送り仮名 혼합 토큰을 한자 부분과 가나 부분으로 분리.
     *
     * 예: 出す(だす) → [出(だ), す(す)]
     *     食べる(たべる) → [食(た), べる(べる)]
     *     お父さん(おとうさん) → [お(お), 父(とう), さん(さん)]
     *     東京(とうきょう) → [東京(とうきょう)]  (순수 한자는 분리 안함)
     */
    internal fun splitOkurigana(token: FuriganaToken): List<FuriganaToken> {
        if (!token.isKanji || token.surface == token.reading) return listOf(token)

        val surface = token.surface
        val reading = token.reading

        // surface에서 선행 가나 글자 수
        var leadingKana = 0
        for (c in surface) {
            if (isKana(c)) leadingKana++ else break
        }

        // surface에서 후행 가나 글자 수 (送り仮名)
        var trailingKana = 0
        for (i in surface.length - 1 downTo leadingKana) {
            if (isKana(surface[i])) trailingKana++ else break
        }

        // 분리할 가나가 없으면 (순수 한자: 東京, 絶対 등) 그대로 반환
        if (leadingKana == 0 && trailingKana == 0) return listOf(token)

        // reading 길이 검증: reading이 surface의 가나 부분보다 짧으면 안전하게 그대로 반환
        if (reading.length < leadingKana + trailingKana) return listOf(token)

        val result = mutableListOf<FuriganaToken>()

        // 선행 가나 (예: お父さん의 "お")
        if (leadingKana > 0) {
            result.add(
                FuriganaToken(
                    surface = surface.substring(0, leadingKana),
                    reading = reading.substring(0, leadingKana),
                    isKanji = false,
                ),
            )
        }

        // 한자 부분 (예: "父", "出")
        val kanjiSurface = surface.substring(leadingKana, surface.length - trailingKana)
        val kanjiReading = reading.substring(leadingKana, reading.length - trailingKana)
        if (kanjiSurface.isNotEmpty()) {
            result.add(
                FuriganaToken(
                    surface = kanjiSurface,
                    reading = kanjiReading,
                    isKanji = true,
                ),
            )
        }

        // 후행 가나 — 送り仮名 (예: "す", "べる", "さん")
        if (trailingKana > 0) {
            result.add(
                FuriganaToken(
                    surface = surface.substring(surface.length - trailingKana),
                    reading = reading.substring(reading.length - trailingKana),
                    isKanji = false,
                ),
            )
        }

        return result
    }

    private fun isKana(c: Char): Boolean =
        c in '\u3040'..'\u309F' ||  // 히라가나
        c in '\u30A0'..'\u30FF' ||  // 가타카나
        c == 'ー'                   // 장음
}
