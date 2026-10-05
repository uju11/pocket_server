plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

layout.buildDirectory.set(file("C:/Users/cotton candy/AppData/Local/Temp/remote_media_build"))

android {
    namespace = "com.remotemedia"
    compileSdk = 35
    buildToolsVersion = "34.0.0"

    defaultConfig {
        applicationId = "com.remotemedia"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("com.hierynomus:smbj:0.13.0")

    // Ktor HTTP Server
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.gson)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.partial.content)
    implementation(libs.ktor.server.auto.head.response)

    // Ktor Client for Telegram & API requests
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Torrent engine + Native C++ architecture binaries
    implementation(libs.libtorrent4j)
    implementation("org.libtorrent4j:libtorrent4j-android-arm:2.1.0-30")
    implementation("org.libtorrent4j:libtorrent4j-android-arm64:2.1.0-30")
    implementation("org.libtorrent4j:libtorrent4j-android-x86:2.1.0-30")
    implementation("org.libtorrent4j:libtorrent4j-android-x86_64:2.1.0-30")

    // Native yt-dlp & FFmpeg engine for direct on-device video extraction (YouTube, Insta, etc.)
    implementation("io.github.junkfood02.youtubedl-android:library:0.17.0")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.17.0")

    // Native TDLib (Telegram Database Library) engine for 2GB+ direct MTProto Telegram downloads
    implementation("io.github.tdlib-android:core:0.1.1")

    debugImplementation(libs.androidx.ui.tooling)
}
