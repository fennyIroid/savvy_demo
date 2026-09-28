# SavvyRDAndroid: Android R&D proof of concept

| Module | State |
| --- | --- |
| `core` | Pure Kotlin decision logic (block policy incl. parent always-on, time integrity, card and grant verification, offline policy, usage aggregation). **21 tests pass on the JVM** (`gradle :core:test`), including verification of cards and grants produced by the Node backend. |
| `app` | Android app. **Compiles against the real Android 16 (API 36) framework** via `:compilecheck` (Robolectric `android-all` from Maven Central) with hand-written stubs for AndroidX, ZXing, R and BuildConfig. Not yet built into an APK or run on a phone: the Android SDK / AGP (dl.google.com) was blocked in the R&D environment. Build it in Android Studio. |
| `compilecheck` | Only included when no Android SDK is installed. `gradle :compilecheck:compileKotlin` |

## Build

```bash
# core only (any machine with JDK 17+ and Gradle 8.x)
gradle :core:test

# full app (Android Studio or command line with the SDK installed)
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew :app:assembleDebug   # generate the wrapper first with: gradle wrapper
```

Set `BACKEND_URL` and `CARD_DOMAIN` in `app/build.gradle.kts`. For App Link
verification, host `https://<card-domain>/.well-known/assetlinks.json` with the
debug and release certificate SHA-256 fingerprints.

## What the app contains

| Approach | Class | Notes |
| --- | --- | --- |
| A UsageStats | `service/UsageMonitorService` | Polls ACTIVITY_RESUMED every 750 ms in a specialUse foreground service |
| B Accessibility | `service/SavvyAccessibilityService` | TYPE_WINDOW_STATE_CHANGED only, no content access, isAccessibilityTool=false |
| C Block screen | `block/BlockActivity` | Savvy screen over the blocked app, NFC reader mode while visible, QR, go Home |
| D Foreground service | `service/UsageMonitorService` | Persistent "focus on" notification, restarted by `BootReceiver` |
| E Device admin | `admin/SavvyDeviceAdminReceiver` | Parent mode only; no policies used |
| F Device Owner | Not implemented | Researched: needs factory-reset provisioning; unsuitable for Play consumer app (see ANDROID_FEASIBILITY.md) |

Both detection paths feed `service/BlockingEngine`, which uses `core/RestrictionPolicy`,
so they can be compared on the same device. Tamper state is reported by
`service/TamperMonitor` in the heartbeat.

## Tests
See `docs/ANDROID_POC_RESULTS.md` for the device and OEM test plan.
