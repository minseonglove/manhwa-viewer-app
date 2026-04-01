package com.manhwaviewer.data.translation

/**
 * Google Cloud Translation API 서비스 (미구현)
 *
 * 번역 파이프라인: Google 번역 → Papago 번역
 * 현재는 Papago만 사용하며, Google 번역은 향후 구현 예정입니다.
 */
class GoogleTranslationService {
    // TODO: Google Cloud Translation API 연동
    // - API 키 설정
    // - REST API 호출 구현
    // - 에러 처리

    suspend fun translate(
        text: String,
        source: String = "ja",
        target: String = "ko",
    ): String? {
        // TODO: Google Cloud Translation API 구현
        return null // null 반환 시 파이프라인에서 Papago로 폴백
    }
}
