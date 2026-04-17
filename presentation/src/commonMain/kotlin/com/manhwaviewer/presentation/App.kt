package com.manhwaviewer.presentation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclassesOfSealed
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.memory.MemoryCache
import com.manhwaviewer.domain.usecase.GetImageBytesUseCase
import com.manhwaviewer.presentation.coil.newImageDiskCache
import com.manhwaviewer.presentation.navigation.NavGraph
import com.manhwaviewer.presentation.navigation.Route
import com.manhwaviewer.presentation.screen.viewer.ViewerImageFetcher
import com.manhwaviewer.presentation.screen.viewer.ViewerImageKeyer
import com.manhwaviewer.presentation.theme.MangaLensTheme
import org.koin.compose.koinInject

@Composable
fun App() {
    val getImageBytesUseCase: GetImageBytesUseCase = koinInject()

    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.15)
                    .build()
            }
            .diskCache { newImageDiskCache(context) }
            .components {
                add(ViewerImageKeyer())
                add(ViewerImageFetcher.Factory(getImageBytesUseCase))
            }
            .build()
    }

    MangaLensTheme {
        val backStack = rememberNavBackStack(
            SavedStateConfiguration {
                serializersModule = SerializersModule {
                    polymorphic(NavKey::class) {
                        subclassesOfSealed<Route>()
                    }
                }
            },
            Route.Home,
        )
        NavGraph(backStack = backStack)
    }
}
