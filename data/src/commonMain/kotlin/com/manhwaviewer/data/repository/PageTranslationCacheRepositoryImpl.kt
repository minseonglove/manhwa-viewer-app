package com.manhwaviewer.data.repository

import com.manhwaviewer.data.db.PageOcrCacheDao
import com.manhwaviewer.data.db.PageOcrCacheEntity
import com.manhwaviewer.data.db.PageOcrTranslationEntity
import com.manhwaviewer.domain.entity.OcrBlock
import com.manhwaviewer.domain.entity.PageOcrCache
import com.manhwaviewer.domain.entity.PageTranslationCache
import com.manhwaviewer.domain.entity.TranslationBlock
import com.manhwaviewer.domain.repository.PageTranslationCacheRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class OcrBlockDto(
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
    val t: String,
    val c: Int? = null,
)

class PageTranslationCacheRepositoryImpl(
    private val dao: PageOcrCacheDao,
) : PageTranslationCacheRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getCache(
        archiveId: Long,
        entryName: String,
    ): PageTranslationCache? = withContext(Dispatchers.IO) {
        val ocrEntity = dao.findOcrCache(archiveId, entryName) ?: return@withContext null
        val translationEntity = dao.findTranslation(ocrEntity.id)
        val ocrBlocks: List<OcrBlockDto> = json.decodeFromString(ocrEntity.blocksJson)

        if (translationEntity == null) return@withContext null

        val translations: List<String> = json.decodeFromString(translationEntity.translationsJson)

        PageTranslationCache(
            id = ocrEntity.id,
            archiveId = ocrEntity.archiveId,
            entryName = ocrEntity.entryName,
            page = ocrEntity.page,
            imageWidth = ocrEntity.imageWidth,
            imageHeight = ocrEntity.imageHeight,
            blocks = ocrBlocks.mapIndexed { i, block ->
                TranslationBlock(
                    x = block.x,
                    y = block.y,
                    width = block.w,
                    height = block.h,
                    originalText = block.t,
                    translatedText = translations.getOrElse(i) { "" },
                    dominantColorArgb = block.c,
                )
            },
            createdAt = ocrEntity.createdAt,
        )
    }

    override suspend fun saveCache(cache: PageTranslationCache) = withContext(Dispatchers.IO) {
        val ocrBlockDtos = cache.blocks.map {
            OcrBlockDto(it.x, it.y, it.width, it.height, it.originalText, it.dominantColorArgb)
        }
        val ocrEntity = PageOcrCacheEntity(
            id = cache.id,
            archiveId = cache.archiveId,
            entryName = cache.entryName,
            page = cache.page,
            imageWidth = cache.imageWidth,
            imageHeight = cache.imageHeight,
            blocksJson = json.encodeToString(ocrBlockDtos),
            createdAt = cache.createdAt,
        )
        val translations = cache.blocks.map { it.translatedText }
        val translationEntity = PageOcrTranslationEntity(
            ocrCacheId = 0, // @Transaction 메서드 내부에서 실제 ID로 교체됨
            translationsJson = json.encodeToString(translations),
            sourceLanguage = "ja",
            targetLanguage = "ko",
            createdAt = cache.createdAt,
        )
        dao.insertOcrCacheWithTranslation(ocrEntity, translationEntity)
    }

    override suspend fun deleteByArchive(archiveId: Long) = withContext(Dispatchers.IO) {
        dao.deleteByArchive(archiveId)
    }

    override suspend fun getOcrCache(
        archiveId: Long,
        entryName: String,
    ): PageOcrCache? = withContext(Dispatchers.IO) {
        val entity = dao.findOcrCache(archiveId, entryName) ?: return@withContext null
        val blocks: List<OcrBlockDto> = json.decodeFromString(entity.blocksJson)
        PageOcrCache(
            id = entity.id,
            archiveId = entity.archiveId,
            entryName = entity.entryName,
            page = entity.page,
            imageWidth = entity.imageWidth,
            imageHeight = entity.imageHeight,
            blocks = blocks.map {
                OcrBlock(it.x, it.y, it.w, it.h, it.t, it.c)
            },
            createdAt = entity.createdAt,
        )
    }

    override suspend fun saveOcrCache(cache: PageOcrCache): Long = withContext(Dispatchers.IO) {
        val blockDtos = cache.blocks.map {
            OcrBlockDto(it.x, it.y, it.width, it.height, it.text, it.dominantColorArgb)
        }
        dao.insertOcrCache(
            PageOcrCacheEntity(
                id = cache.id,
                archiveId = cache.archiveId,
                entryName = cache.entryName,
                page = cache.page,
                imageWidth = cache.imageWidth,
                imageHeight = cache.imageHeight,
                blocksJson = json.encodeToString(blockDtos),
                createdAt = cache.createdAt,
            )
        )
    }
}
