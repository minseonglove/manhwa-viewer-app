plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "com.manhwaviewer.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.manhwaviewer.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.0"

        val localFile = rootProject.file("local.properties")
        val googleTranslateApiKey = if (localFile.exists()) {
            localFile.readLines().find { it.startsWith("google.translate.api.key=") }?.substringAfter("=") ?: ""
        } else ""
        buildConfigField("String", "GOOGLE_TRANSLATE_API_KEY", "\"$googleTranslateApiKey\"")

        fun localProperty(name: String): String =
            if (localFile.exists()) localFile.readLines().find { it.startsWith("$name=") }?.substringAfter("=")?.trim() ?: "" else ""
        buildConfigField("String", "TRANSLATION_PROXY_URL", "\"${localProperty("translation.proxy.url")}\"")
        // 플랫폼 증명이 붙기 전까지 쓰는 개발용 증명. 릴리스 빌드에는 아래에서 빈 값으로 덮어쓴다.
        buildConfigField("String", "TRANSLATION_PROXY_DEV_SECRET", "\"${localProperty("proxy.dev.secret")}\"")
        // Play Integrity 토큰을 요청할 Cloud 프로젝트 번호. 없으면 0 이고, 그때 앱은 Play Integrity 를 부르지 않는다.
        buildConfigField("long", "PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER", "${localProperty("play.integrity.cloud.project.number").ifBlank { "0" }}L")
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            val keystoreFile = rootProject.file("keystore.properties")
            if (keystoreFile.exists()) {
                val lines = keystoreFile.readLines()
                fun prop(key: String) = lines.find { it.startsWith("$key=") }?.substringAfter("=") ?: ""
                storeFile = file(prop("storeFile"))
                storePassword = prop("storePassword")
                keyAlias = prop("keyAlias")
                keyPassword = prop("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            buildConfigField("String", "TRANSLATION_PROXY_DEV_SECRET", "\"\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseConfig = signingConfigs.findByName("release")
            signingConfig = if (releaseConfig?.storeFile?.exists() == true) {
                releaseConfig
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    packaging {
        jniLibs {
            excludes += listOf("lib/x86/**", "lib/x86_64/**")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(projects.domain)
    implementation(projects.data)
    implementation(projects.presentation)
    implementation(compose.material3)
    implementation(compose.ui)
    implementation(compose.foundation)
    implementation(compose.components.resources)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.room.runtime)
    implementation(libs.datastore.preferences.core)
}
