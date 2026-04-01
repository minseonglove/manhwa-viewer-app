package com.manhwaviewer.presentation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import com.manhwaviewer.domain.usecase.GetImageBytesUseCase
import com.manhwaviewer.presentation.navigation.NavGraph
import com.manhwaviewer.presentation.screen.viewer.ViewerImageFetcher
import com.manhwaviewer.presentation.screen.viewer.ViewerImageKeyer
import com.manhwaviewer.presentation.theme.MangaLensTheme
import org.koin.compose.koinInject

@Composable
fun App() {
    val getImageBytesUseCase: GetImageBytesUseCase = koinInject()

    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components {
                add(ViewerImageKeyer())
                add(ViewerImageFetcher.Factory(getImageBytesUseCase))
            }
            .build()
    }

    MangaLensTheme {
        val navController = rememberNavController()
        NavGraph(navController = navController)
    }
}
