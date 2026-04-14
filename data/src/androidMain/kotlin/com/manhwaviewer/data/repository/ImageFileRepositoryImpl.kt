package com.manhwaviewer.data.repository

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.manhwaviewer.domain.entity.ImageFile
import com.manhwaviewer.domain.repository.ImageFileRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.time.Instant

class ImageFileRepositoryImpl(
    private val context: Context,
) : ImageFileRepository {

    override fun getAllImages(): Flow<List<ImageFile>> = callbackFlow {
        val contentResolver = context.contentResolver

        fun query(): List<ImageFile> = queryAllImages(contentResolver)

        trySend(query())

        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(query())
            }
        }

        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            observer,
        )

        awaitClose {
            contentResolver.unregisterContentObserver(observer)
        }
    }

    private fun queryAllImages(contentResolver: ContentResolver): List<ImageFile> {
        val images = mutableListOf<ImageFile>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val bucketColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val filePath = cursor.getString(dataColumn) ?: continue
                val fileName = cursor.getString(nameColumn) ?: continue
                val mimeType = cursor.getString(mimeColumn) ?: "image/*"
                val size = cursor.getLong(sizeColumn)
                val dateModified = cursor.getLong(dateColumn)
                val folderName = cursor.getString(bucketColumn) ?: ""

                images.add(
                    ImageFile(
                        id = id,
                        filePath = filePath,
                        fileName = fileName,
                        mimeType = mimeType,
                        size = size,
                        dateModified = Instant.fromEpochSeconds(dateModified),
                        folderName = folderName,
                    ),
                )
            }
        }

        return images
    }
}
