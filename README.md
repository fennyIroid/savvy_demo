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

Backend and card domain: `savvy.backendUrl` / `savvy.cardDomain` in `local.properties`
(or `-P`). For a USB-connected phone the simplest route is `adb reverse tcp:3000 tcp:3000`
with `savvy.backendUrl=http://127.0.0.1:3000`. Robolectric suite next to the app:
`./gradlew -Psavvy.compilecheck=true :compilecheck:test`.
Run the backend with persistent keys (a restart with new keys breaks registered phones):
`SAVVY_DEV=1 SAVVY_CARD_SIGNING_KEY="$(cat card.pem)" SAVVY_GRANT_SIGNING_KEY="$(cat grant.pem)" npm start`. For App Link
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
| F Device Owner | `admin/DeviceOwnerController` | **R&D alternative only.** Inert unless provisioned with `adb shell dpm set-device-owner`. Suspends blocked apps, blocks uninstall and safe mode during a session. Unsuitable for a Play consumer app (factory-reset provisioning) |

Both detection paths feed `service/BlockingEngine`, which uses `core/RestrictionPolicy`,
so they can be compared on the same device. Tamper state is reported by
`service/TamperMonitor` in the heartbeat.

Also in the logic layer (see `docs/BRIEF_COMPLIANCE.md` for the brief-by-brief check):

| Class | Purpose |
| --- | --- |
| `service/BootReceiver` (+ `TimeChangeReceiver`) | Direct-boot record of the boot wall clock and a checkpoint on every clock change, so "reboot, go offline, move the clock forward" no longer ends a commitment (core `TimeIntegrity`) |
| `service/SyncJobService` | 15-minute JobScheduler housekeeping: child rules arrive without the FGS, heartbeat continues after an OEM kill, enforcement re-armed |
| `service/Alerts` | Notification when the accessibility service is turned off during a session (user, force stop, OEM killer) |
| `data/OemBackground` | Samsung / Xiaomi / Oppo / Vivo / OnePlus / Huawei keep-running screen, shown in Permissions |
| `nfc/NfcTagWriter` | Debug: write the test card URL (+ Android Application Record) to a blank NTAG21x tag |
| `core/ParentPin` | Parent PIN (hash in the rules) that a child phone needs to leave parent mode |

## UI

Jetpack Compose + Material 3, in its own source folder `app/src/main/ui` (the logic in
`app/src/main/java` is unchanged and is what `:compilecheck` / Robolectric compiles and tests).

- Layout follows the Brick app: a sheet whose bottom corners curve up, a text-only tab bar
  under it (Focus, Tasks, Activity, Settings), grouped rounded cards, pill buttons, sub-screens
  as modal pages with a round back button.
- Colours: the "Frozen lake" palette (#6D8196 slate, #ADD8E6 icy, #FFFAFA snow, #000080 navy),
  light and dark (`ui/theme/Theme.kt`). Font: Outfit (SIL OFL, `third_party/Outfit-OFL.txt`).
- `MainActivity` is the single app activity (`ui/SavvyRoot.kt`, navigation-compose).
  `ui/SavvyViewModel.kt` calls the same `SavvyActions` the old test lab called.
- `BlockActivity` keeps all card / task / emergency logic and pushes a `BlockUiState` to
  `BlockScreen` (Compose in the app, a no-op stub in `compilecheck/src/main/kotlin/stubs4`).
- Every old test-lab action is still reachable: Settings › Diagnostics has status /
  heartbeat, sync, restore, text reports, show and share log; Settings › Card has the
  debug test card with its QR.

## Tests
See `docs/ANDROID_POC_RESULTS.md` for the device and OEM test plan.
