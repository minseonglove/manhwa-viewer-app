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
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        val localFile = rootProject.file("local.properties")
        val papagoClientId = if (localFile.exists()) {
            localFile.readLines().find { it.startsWith("papago.client.id=") }?.substringAfter("=") ?: ""
        } else ""
        val papagoClientSecret = if (localFile.exists()) {
            localFile.readLines().find { it.startsWith("papago.client.secret=") }?.substringAfter("=") ?: ""
        } else ""
        buildConfigField("String", "PAPAGO_CLIENT_ID", "\"$papagoClientId\"")
        buildConfigField("String", "PAPAGO_CLIENT_SECRET", "\"$papagoClientSecret\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
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
