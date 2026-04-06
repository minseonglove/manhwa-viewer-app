# ============================================================
# Manhwa Viewer - Consumer ProGuard Rules (Presentation Module)
# ============================================================

# ---- ViewModels ----
-keep class com.manhwaviewer.presentation.screen.**.* extends com.manhwaviewer.presentation.mvi.MviViewModel { *; }
-keep class com.manhwaviewer.presentation.mvi.MviViewModel { *; }
-keep class com.manhwaviewer.presentation.screen.home.HomeViewModel { *; }
-keep class com.manhwaviewer.presentation.screen.filebrowser.FileBrowserViewModel { *; }
-keep class com.manhwaviewer.presentation.screen.recent.RecentViewModel { *; }
-keep class com.manhwaviewer.presentation.screen.imagegallery.ImageGalleryViewModel { *; }
-keep class com.manhwaviewer.presentation.screen.vault.VaultViewModel { *; }
-keep class com.manhwaviewer.presentation.screen.viewer.ViewerViewModel { *; }
-keep class com.manhwaviewer.presentation.screen.settings.SettingsViewModel { *; }

# ---- MVI State/Intent/SideEffect (data class 직렬화 보호) ----
-keep class com.manhwaviewer.presentation.screen.**.*State { *; }
-keep class com.manhwaviewer.presentation.screen.**.*Intent { *; }
-keep class com.manhwaviewer.presentation.screen.**.*SideEffect { *; }

# ---- Navigation ----
-keep class com.manhwaviewer.presentation.navigation.** { *; }

# ---- Coil Image Loading ----
-keep class coil3.** { *; }
-dontwarn coil3.**

# ---- Telephoto (Zoomable / SubSampling) ----
-keep class me.saket.telephoto.** { *; }
-dontwarn me.saket.telephoto.**

# ---- Compose Resources ----
-keep class com.manhwaviewer.presentation.generated.resources.** { *; }
