plugins {
    kotlin("jvm") version "1.9.23"
    application
}

repositories {
    mavenCentral()
}

application {
    mainClass.set("com.spike.torrentstream.MainKt")
}

dependencies {
    // libtorrent4j JVM (desktop) - same version as Android app
    implementation("org.libtorrent4j:libtorrent4j:2.1.0-30")
    // platform-specific native (pick one; CI can test linux-x86)
    implementation("org.libtorrent4j:libtorrent4j-linux-x86_64:2.1.0-30")
    // Minimal HTTP server (stdlib only - no extra deps)
    implementation(kotlin("stdlib"))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}
