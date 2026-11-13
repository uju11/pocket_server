plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.remotemedia.enginetorrent"
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
    implementation(project(":media"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.gson)

    // Torrent engine + Native C++ architecture binaries
    implementation(libs.libtorrent4j)
    implementation("org.libtorrent4j:libtorrent4j-android-arm:2.1.0-30")
    implementation("org.libtorrent4j:libtorrent4j-android-arm64:2.1.0-30")
    implementation("org.libtorrent4j:libtorrent4j-android-x86:2.1.0-30")
    implementation("org.libtorrent4j:libtorrent4j-android-x86_64:2.1.0-30")
}
