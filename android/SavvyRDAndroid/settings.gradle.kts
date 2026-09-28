pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.google\\.android.*")
                includeGroup("com.google.testing.platform")
            }
        }
    }
    // Versions resolved only when a module applies the plugin, so :core builds
    // on machines without Google's Maven (AGP) or the Android SDK.
    plugins {
        kotlin("jvm") version "2.1.21"
        kotlin("android") version "2.1.21"
        id("com.android.application") version "8.10.1"
    }
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.google\\.android.*")
                includeGroup("com.google.testing.platform")
            }
        }
    }
}
rootProject.name = "SavvyRDAndroid"

// core = pure Kotlin decision logic, builds and tests on any JVM.
include(":core")

// app = Android app. Needs the Android SDK (ANDROID_HOME or local.properties sdk.dir).
// Skipped automatically where the SDK is not installed, so `gradle :core:test` still runs.
val localProps = file("local.properties")
val hasSdk = System.getenv("ANDROID_HOME") != null || System.getenv("ANDROID_SDK_ROOT") != null ||
    (localProps.exists() && localProps.readText().contains("sdk.dir"))
if (hasSdk) include(":app")
// The Robolectric suite (compilecheck) can also run next to :app with -Psavvy.compilecheck=true.
if (!hasSdk || providers.gradleProperty("savvy.compilecheck").isPresent) {
    if (!hasSdk) println("Android SDK not found: building :core and :compilecheck (app sources vs android-all)")
    include(":compilecheck")
    include(":androidxtest")
}
