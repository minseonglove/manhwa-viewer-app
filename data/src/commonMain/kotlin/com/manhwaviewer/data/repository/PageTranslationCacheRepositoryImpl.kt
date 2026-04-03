package com.manhwaviewer.data.repository

import com.manhwaviewer.data.db.PageTranslationCacheDao
import com.manhwaviewer.data.db.PageTranslationCacheEntity
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
private data class BlockDto(
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
    val o: String,
    val t: String,
)

class PageTranslationCacheRepositoryImpl(
    private val dao: PageTranslationCacheDao,
) : PageTranslationCacheRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getCache(
        archiveId: Long,
        page: Int,
    ): PageTranslationCache? = withContext(Dispatchers.IO) {
        val entity = dao.findCache(archiveId, page) ?: return@withContext null
        entity.toDomain()
    }

    override suspend fun saveCache(cache: PageTranslationCache) = withContext(Dispatchers.IO) {
        dao.insertCache(cache.toEntity())
    }

    override suspend fun deleteByArchive(archiveId: Long) = withContext(Dispatchers.IO) {
        dao.deleteByArchive(archiveId)
    }

    private fun PageTranslationCacheEntity.toDomain(): PageTranslationCache {
        val blocks: List<BlockDto> = json.decodeFromString(blocksJson)
        return PageTranslationCache(
            id = id,
            archiveId = archiveId,
            page = page,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            blocks = blocks.map {
                TranslationBlock(it.x, it.y, it.w, it.h, it.o, it.t)
            },
            createdAt = createdAt,
        )
    }

    private fun PageTranslationCache.toEntity(): PageTranslationCacheEntity {
        val blockDtos = blocks.map {
            BlockDto(it.x, it.y, it.width, it.height, it.originalText, it.translatedText)
        }
        return PageTranslationCacheEntity(
            id = id,
            archiveId = archiveId,
            page = page,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            blocksJson = json.encodeToString(blockDtos),
            createdAt = createdAt,
        )
    }
}
