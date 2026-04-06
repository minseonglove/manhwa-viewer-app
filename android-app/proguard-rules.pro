# ============================================================
# Manhwa Viewer - ProGuard Rules (Android App Module)
# ============================================================

# ---- 기본 설정 ----
-optimizationpasses 5
-dontusemixedcaseclassnames
-verbose
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepattributes SourceFile,LineNumberTable

# ---- Kotlin ----
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}
-keep class kotlin.reflect.jvm.internal.** { *; }
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Lazy {
    public *;
}
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ---- Compose ----
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
-keep @androidx.compose.runtime.Composable class * { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# ---- Application Entry Point ----
-keep class com.manhwaviewer.android.ManhwaViewerApp { *; }
-keep class com.manhwaviewer.android.MainActivity { *; }

# ---- Koin DI ----
-keep class org.koin.** { *; }
-dontwarn org.koin.**
-keepclassmembers class * {
    public <init>(...);
}
# Koin module definitions (lambda)
-keep class com.manhwaviewer.android.di.** { *; }

# ---- BuildConfig ----
-keep class com.manhwaviewer.android.BuildConfig { *; }

# ---- AndroidX ----
-keep class androidx.lifecycle.** { *; }
-dontwarn androidx.lifecycle.**
-keep class androidx.activity.** { *; }
-keep class androidx.core.** { *; }

# ---- Navigation Compose ----
-keep class androidx.navigation.** { *; }
-dontwarn androidx.navigation.**

# ---- DataStore ----
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# ---- Domain Module (순수 KMP, consumerProguardFiles 미지원) ----
-keep class com.manhwaviewer.domain.model.** { *; }
-keep interface com.manhwaviewer.domain.repository.** { *; }
-keep class com.manhwaviewer.domain.usecase.** { *; }
