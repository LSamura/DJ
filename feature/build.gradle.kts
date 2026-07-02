plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.djassistant.feature"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.bundles.coroutines)
    implementation(libs.datastore.preferences)
    implementation(libs.timber)

    // Offline ASR (Vosk) — jna needs the @aar classifier, so it's declared
    // directly rather than through the version catalog.
    implementation(libs.vosk.android)
    implementation("net.java.dev.jna:jna:${libs.versions.jna.get()}@aar")

    // Offline wake word detection (Sprint 4) — replaces the Vosk-based
    // wake-word grammar hack from Sprint 3.x; Vosk now only ever runs full
    // command recognition, never wake-word spotting.
    implementation(libs.porcupine.android)
}
