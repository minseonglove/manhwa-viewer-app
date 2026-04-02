plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.compilations.getByName("main") {
            cinterops {
                create("libarchive") {
                    defFile = file("src/nativeInterop/cinterop/libarchive.def")
                }
                create("onnxruntime") {
                    defFile = file("src/nativeInterop/cinterop/onnxruntime.def")
                    compilerOpts("-I${project.rootDir}/libs/onnxruntime/include")
                }
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.domain)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.koin.core)
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            implementation(libs.datastore.preferences.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(kotlin("test"))
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.onnxruntime.android)
            implementation(libs.mlkit.text.recognition.japanese)
            implementation(libs.kotlinx.coroutines.play.services)
            implementation(libs.mlkit.text.recognition.japanese)
            implementation(libs.kotlinx.coroutines.play.services)
        }
    }
}

android {
    namespace = "com.manhwaviewer.data"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val localFile = rootProject.file("local.properties")
        val papagoClientId = if (localFile.exists()) {
            localFile.readLines().find { it.startsWith("papago.client.id=") }?.substringAfter("=") ?: ""
        } else ""
        val papagoClientSecret = if (localFile.exists()) {
            localFile.readLines().find { it.startsWith("papago.client.secret=") }?.substringAfter("=") ?: ""
        } else ""
        buildConfigField("String", "PAPAGO_CLIENT_ID", "\"$papagoClientId\"")
        buildConfigField("String", "PAPAGO_CLIENT_SECRET", "\"$papagoClientSecret\"")

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    externalNativeBuild {
        cmake {
            path = file("src/androidMain/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    ndkVersion = "27.0.12077973"
}

dependencies {
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("junit:junit:4.13.2")
    add("kspAndroid", libs.room.compiler)
    add("kspIosX64", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}

room {
    schemaDirectory("$projectDir/schemas")
}
