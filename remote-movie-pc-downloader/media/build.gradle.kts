plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.remotemedia.media"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        targetSdk = 35
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":storage"))
    implementation(project(":network"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.gson)
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Native yt-dlp & FFmpeg engine
    implementation("io.github.junkfood02.youtubedl-android:library:0.17.0")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.17.0")
}
