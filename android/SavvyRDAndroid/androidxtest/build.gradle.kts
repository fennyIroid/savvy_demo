// Test-only: builds androidx.test "monitor" + espresso "idling_resource" (Apache 2.0,
// github.com/android/android-test) from source, because Robolectric needs them at
// runtime and Google Maven (dl.google.com) is unreachable in the R&D environment.
// Sources are fetched with git at build time (not committed). Only used by
// :compilecheck tests; irrelevant when building with Android Studio.
plugins { `java-library` }

val srcDir = layout.buildDirectory.dir("android-test-src")
val androidTestCommit = "92d1b17c6a8cc0c85a7a25f6cb7ad4ee46b93d79"

val fetchSources by tasks.registering(Exec::class) {
    val dir = srcDir.get().asFile
    onlyIf { !dir.resolve("runner").exists() }
    commandLine("bash", "-c", """
        set -e
        rm -rf '$dir' && git clone -q --filter=blob:none --sparse https://github.com/android/android-test.git '$dir'
        cd '$dir' && git checkout -q $androidTestCommit
        git sparse-checkout set runner/monitor/java/androidx/test espresso/idling_resource/java/androidx/test/espresso
    """.trimIndent())
}

sourceSets["main"].java.srcDirs(
    "src/stubs/java",
    srcDir.map { it.dir("runner/monitor/java") },
    srcDir.map { it.dir("espresso/idling_resource/java") },
)
tasks.compileJava {
    dependsOn(fetchSources)
    // Not used by Robolectric; they need hidden framework APIs or extra libraries.
    exclude("**/runner/MonitoringInstrumentation.java", "**/tracing/AndroidXTracer.java",
        "**/io/FileTestStorage.java", "**/internal/runner/hidden/**", "**/internal/runner/InstrumentationConnection.java",
        "**/intercepting/DefaultInterceptingActivityFactory.java", "**/io/PlatformTestStorageRegistry.java", "**/tracing/Tracing.java")
    options.compilerArgs.addAll(listOf("-nowarn", "-Xlint:none"))
}
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }

dependencies {
    compileOnly("org.robolectric:android-all:16-robolectric-13921718")
    implementation("com.google.errorprone:error_prone_annotations:2.36.0")
}
