plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    compilerOptions {
        optIn.addAll("kotlinx.cinterop.ExperimentalForeignApi", "kotlinx.cinterop.BetaInteropApi")
    }
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    val xcfBase = "${project.rootDir}/libs/lindera/LinderaFFI.xcframework"
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        val linderaLibPath = when (target.name) {
            "iosArm64" -> "$xcfBase/ios-arm64"
            else -> "$xcfBase/ios-arm64_x86_64-simulator"
        }
        target.compilations.getByName("main") {
            cinterops {
                create("libarchive") {
                    defFile = file("src/nativeInterop/cinterop/libarchive.def")
                    compilerOpts("-I${project.rootDir}/libs/libarchive/libarchive")
                }
                create("lindera") {
                    defFile = file("src/nativeInterop/cinterop/lindera.def")
                    compilerOpts("-I${project.rootDir}/libs/lindera")
                    extraOpts("-libraryPath", linderaLibPath)
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

        consumerProguardFiles("consumer-rules.pro")
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
