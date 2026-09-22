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

# ---- LiteRT ----
# liblitert_jni.so 가 LiteRtException, TensorType, TensorBufferRequirements 를 이름으로 찾아 만든다.
# 라이브러리가 싣는 규칙은 예전 org.tensorflow.lite 어노테이션만 지켜서, 없으면 R8 이 이들을 지우고
# GPU 컴파일 실패처럼 네이티브가 예외를 던지는 순간 CPU 폴백 대신 앱이 죽는다.
-keep class com.google.ai.edge.litert.** { *; }
-dontwarn com.google.ai.edge.litert.**

# ---- DataStore ----
-keep class androidx.datastore.** { *; }
-keep class com.manhwaviewer.data.preferences.** { *; }

# ---- SQLite ----
-keep class androidx.sqlite.** { *; }
-dontwarn androidx.sqlite.**

# ---- BuildConfig ----
-keep class com.manhwaviewer.data.BuildConfig { *; }
