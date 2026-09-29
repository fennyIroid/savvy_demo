// Compile check of the Android app sources WITHOUT the Android SDK / AGP:
// compiles app/src/main/java against the real Android 16 (API 36) framework
// classes published as Robolectric android-all on Maven Central, plus small
// hand-written stubs for AndroidX, ZXing, R and BuildConfig (src/main/kotlin/stubs).
// Proves the app code is consistent with the real framework API; it is not an APK
// build (no resources, manifest merge or dexing). Only included when no SDK exists.
plugins {
    kotlin("jvm")
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    sourceSets["main"].kotlin.srcDirs("src/main/kotlin", "../app/src/main/java")
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    compileOnly("org.robolectric:android-all:16-robolectric-13921718")
    implementation(project(":core"))
    implementation("org.bouncycastle:bcprov-jdk18on:1.81")
}

// Robolectric: runs the app code on a simulated Android 16 framework on the JVM,
// end to end against a real backend process (backend/src/server.js, SAVVY_DEV=1).
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation(project(":androidxtest"))
    testImplementation("org.robolectric:android-all:16-robolectric-13921718")
    // androidx.test (Google Maven) is unreachable here, so it is excluded; the tests
    // use Robolectric's RuntimeEnvironment instead of ApplicationProvider.
    testImplementation("org.robolectric:robolectric:4.17") {
        exclude(group = "androidx.test"); exclude(group = "androidx.test.espresso")
    }
}
tasks.test {
    useJUnit()
    systemProperty("savvy.backendDir", rootDir.resolve("../../backend").canonicalPath)
    // Robolectric needs these on JDK 17+.
    jvmArgs(
        "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/java.net=ALL-UNNAMED",
    )
    testLogging { events("passed", "failed", "skipped"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
