// Plugin versions are in settings.gradle.kts (pluginManagement). Declared here once
// (apply false) so every module shares one Kotlin plugin class loader.
plugins {
    kotlin("jvm") apply false
    kotlin("android") apply false
    id("com.android.application") apply false
}
