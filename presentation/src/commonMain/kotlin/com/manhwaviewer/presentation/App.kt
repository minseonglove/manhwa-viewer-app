package com.manhwaviewer.presentation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController
import com.manhwaviewer.presentation.navigation.NavGraph
import com.manhwaviewer.presentation.theme.MangaLensTheme

@Composable
fun App() {
    MangaLensTheme {
        val navController = rememberNavController()
        NavGraph(navController = navController)
    }
}
