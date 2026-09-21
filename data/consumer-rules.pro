# ============================================================
# Manhwa Viewer - Consumer ProGuard Rules (Data Module)
# ============================================================
# consumer-rules.pro: 이 모듈을 사용하는 앱 모듈에 자동 적용됨

# ---- Room Database ----
-keep class com.manhwaviewer.data.db.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    abstract <methods>;
}
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }
-dontwarn androidx.room.**

# ---- JNI / Native Bridge ----
-keep class com.manhwaviewer.data.archive.LibArchiveBridge { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# ---- Kotlinx Serialization ----
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.manhwaviewer.data.**$$serializer { *; }
-keepclassmembers class com.manhwaviewer.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.manhwaviewer.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# @Serializable 클래스 보호
-keep @kotlinx.serialization.Serializable class com.manhwaviewer.data.** { *; }

# ---- Ktor Client ----
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-keep class okio.** { *; }
-dontwarn okio.**

# ---- ONNX Runtime ----
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# ---- DataStore ----
-keep class androidx.datastore.** { *; }
-keep class com.manhwaviewer.data.preferences.** { *; }

# ---- SQLite ----
-keep class androidx.sqlite.** { *; }
-dontwarn androidx.sqlite.**

# ---- BuildConfig ----
-keep class com.manhwaviewer.data.BuildConfig { *; }
