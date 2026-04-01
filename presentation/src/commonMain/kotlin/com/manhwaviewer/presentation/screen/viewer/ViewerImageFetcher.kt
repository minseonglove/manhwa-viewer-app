package com.manhwaviewer.presentation.screen.viewer

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import com.manhwaviewer.domain.usecase.GetImageBytesUseCase
import com.manhwaviewer.presentation.util.mergeImagesHorizontally
import okio.Buffer
import okio.FileSystem

class ViewerImageFetcher(
    private val data: ViewerPageModel,
    private val options: Options,
    private val getImageBytesUseCase: GetImageBytesUseCase,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val bytes = when (data.entryNames.size) {
            1 -> {
                getImageBytesUseCase(data.archivePath, data.entryNames[0])
                    ?: error("Failed to load image: ${data.entryNames[0]}")
            }
            else -> {
                val first = getImageBytesUseCase(data.archivePath, data.entryNames[0])
                    ?: error("Failed to load first image: ${data.entryNames[0]}")
                val second = data.entryNames.getOrNull(1)?.let {
                    getImageBytesUseCase(data.archivePath, it)
                }
                if (second != null) mergeImagesHorizontally(first, second) else first
            }
        }
        return SourceFetchResult(
            source = ImageSource(Buffer().apply { write(bytes) }, FileSystem.SYSTEM),
            mimeType = null,
            dataSource = DataSource.DISK,
        )
    }

    class Factory(
        private val getImageBytesUseCase: GetImageBytesUseCase,
    ) : Fetcher.Factory<ViewerPageModel> {
        override fun create(
            data: ViewerPageModel,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher {
            return ViewerImageFetcher(data, options, getImageBytesUseCase)
        }
    }
}

class ViewerImageKeyer : Keyer<ViewerPageModel> {
    override fun key(data: ViewerPageModel, options: Options): String {
        val entries = data.entryNames.joinToString("+")
        return "viewer://${data.archivePath}/$entries?rtl=${data.isRtl}"
    }
}
