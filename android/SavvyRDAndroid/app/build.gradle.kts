plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.iroid.savvy.rd"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.iroid.savvy.rd"
        minSdk = 26          // Android 8.0; see ANDROID_FEASIBILITY.md for why
        targetSdk = 36       // Play requires API 36 for new apps from 31 Aug 2026
        versionCode = 1
        versionName = "0.1-rd"
        buildConfigField("String", "BACKEND_URL", "\"http://192.168.1.10:3000\"")
        buildConfigField("String", "CARD_DOMAIN", "\"go.savvy.test\"")
    }
    buildFeatures { buildConfig = true; viewBinding = false }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core"))
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    // QR scanner (live camera only, no gallery import).
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    // Ed25519 below API 33 (platform provider has it from API 33).
    implementation("org.bouncycastle:bcprov-jdk18on:1.81")
}
