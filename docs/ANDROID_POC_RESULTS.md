# Savvy Android POC Results and Device Test Plan

Status date: 28 September 2026 (device run 1 added). Original plan: 25 September 2026.

## Device run 1: Google Pixel 4, Android 13 (API 33), 28 September 2026

**Evidence level L5 (real device) for the rows below.**

Setup:
- The APK was built with AGP 8.10.1 / Gradle 8.14.5, targetSdk 36, and installed with `./gradlew :app:installDebug`.
- The backend ran on the Mac and was reached over USB (`adb reverse tcp:3000`).
  - The LAN route (192.168.1.27) was blocked: the phone could not connect.
  - Removing the reverse route plus Wi-Fi/data off was used as "offline".
- Actions were driven by adb:
  - Instagram was launched with `monkey -c LAUNCHER`.
  - Card taps were delivered as the `NDEF_DISCOVERED` intent that Android's tag dispatch sends. **No physical tag was used.** A real-tag read (A-NFC-1, A-NFC-6) is still open.
  - Permissions were granted with appops/settings. The disclosure dialog was not exercised.
  - Timings come from `ActivityTaskManager` and Savvy log timestamps.
- The phone is a shared dev device with two other blocking-style accessibility services enabled (childsafetyimpl, safescreen). They did not visibly interfere.
- **Test-method caveat:** `uiautomator dump` suppresses other accessibility services while it runs (Savvy logged destroyed/connected). It was not used during blocking measurements.

### Blocking

| Test | Result | Detail |
| --- | --- | --- |
| A-BLOCK-1 launcher, Accessibility path | **Pass** | Detection 37 to 98 ms after the event. The block screen is on top in every run (5/5 after the fix below) |
| A-BLOCK-2 launcher, UsageStats only (a11y off) | **Pass** | About 420 ms to detect; block screen displayed about 640 ms after Instagram's start. Instagram's splash is briefly visible |
| A-BLOCK-4 Recent apps | **Pass** | Blocked. The recents thumbnail shows only Instagram's splash, no feed content |
| A-BLOCK-5 deep link `https://www.instagram.com/instagram/` | **Pass** | Instagram's UrlHandlerActivity blocked |
| A-BAL-1 background launch of BlockActivity (Android 13) | **Pass** | From both the accessibility service and the UsageStats FGS |
| Doze (forced deep idle, Savvy not battery-whitelisted) | **Pass** | Blocked while `deviceidle` reported IDLE |
| A-BLOCK-3 notification, 6 web, 7 Assistant, 8 split screen, 9 PiP | Not run | Need manual steps |

**Bug found and fixed (a11y path).**
- **Problem:** The service started BlockActivity and *then* sent GLOBAL_ACTION_HOME. The launcher covered the block screen, and the user saw only the fallback overlay. NFC reader mode never ran, so "hold the card" did nothing.
- **Fix:** Home first, then the block screen after 250 ms. The overlay now appears only if BlockActivity is not resumed after 1.5 s.
- **Also fixed:** One launch produced 2 to 3 block events; a 1.5 s debounce was added.

### Card unlock (NFC dispatch intent) and QR

| Test | Result | Detail |
| --- | --- | --- |
| Right card, online | **Pass** | Released about 330 ms after the intent. Instagram opens afterwards |
| Another account's genuine Savvy card | **Pass** | Rejected `card_not_owned_by_user` |
| Forged signature | **Pass** | Rejected `bad_signature` |
| Static URL form of my own signed card | **Pass** | Rejected `format_mismatch` |
| Random non-Savvy URL | **Pass** | Never reaches Savvy (intent filter only matches `go.savvy.test/c/`) |
| A-NFC-9 offline, own signed card | **Pass** | Released offline and queued. Synced on reconnect: the server recorded `released_by_card` |
| Offline, someone else's genuine card | **Pass** | Rejected `offline_card_check_failed` |
| A-CRYPTO-1 Ed25519 on API 33 (BouncyCastle provider) | **Pass** | Grants and offline card signatures verified on the device. API 26 to 32 still open |
| QR camera scan | Not run | Needs a person to point the camera at a printed or on-screen QR |

**Gap found and fixed:** the app had no way to register a card. `registerCard` was only called from tests, so every device unlock would have failed. The fix adds "Register my card" (NFC, QR or card link, through BlockActivity) and a debug "DEV: create + register a test card".

### Commitment, to-do, emergency, insights

| Test | Result |
| --- | --- |
| 6 h commitment | **Pass** |
| 24 h (1440 min) commitment | **Pass** |
| Second commitment while one is active | Refused `commitment_already_active` (by design) |
| A-SELF-4 clock moved +7 h in the same boot | **Pass**: still blocked (monotonic clock) |
| A-SELF-3 offline (no route, Wi-Fi and data off) | **Pass**: still blocked |
| To-do, card-protected: Start blocks Instagram; Done → wrong card | **Pass**: refused `card_not_owned_by_user` |
| To-do: Done → right card | **Pass**: released, task marked completed |
| Free Sleep session: "End session" | **Pass**: released |
| Card-required session: "End session" | **Pass**: refused ("needs the card") |
| Emergency exit from the block screen | **Pass**: released |
| Emergency exit on a **locked** commitment | Released (backend rule allows it; product decision C4) |
| Emergency limit: 3rd exit in 7 days | **Pass**: `emergency_exit_limit_reached` |
| Self screen-time summary (UsageStats) | **Pass**: total plus per-app minutes shown |
| Focus time and streak | **Pass**: `streak_days 1`, focus seconds counted |

**Gaps found and fixed:**
- **Task completion:** "Done" on a card-protected task sent no card, so the backend always refused it. Done now opens the card screen in task mode.
- **Free sessions:** these had no end-early action.
- **Emergency exit:** it was not on the block screen.
- **Self screen-time:** it was not shown anywhere.
- **Notifications:** POST_NOTIFICATIONS was never requested.

**Still open:** a card unlock of a *task* commitment from the normal block screen releases the restriction but leaves the task "active".

### Self-mode bypasses

| Test | Result |
| --- | --- |
| A-SELF-8 revoke Usage access (a11y on) | Still blocked (Accessibility path) |
| A-SELF-7 turn off Accessibility (Usage access on) | Still blocked (UsageStats path) |
| A-SELF-1 **Force stop** | **Full bypass.** Force stop removes Savvy from the enabled accessibility services (it stays off until re-enabled in Settings) and kills the FGS. Instagram opens. An app *update* does not remove the a11y service |
| Reopen Savvy after force stop | **Bug fixed.** Enforcement did not restart. Opening Savvy now restarts the FGS when a commitment or parent rule is active; the UsageStats path then blocks again. Accessibility still needs re-enabling by the user |
| A-SELF-10 Clear data | Same as Force stop, plus state lost. Accessibility is removed |
| A-SELF-5/6 uninstall → reinstall → log in with the same email | **Bug fixed, then Pass.** The server restored the commitment, but with an empty app list (packages were never sent), so nothing was blocked. The selection is now sent as `selection_ref` and restored; Instagram was blocked again after reinstall. Permissions must be granted again, and the card must be re-registered before offline unlock works |
| A-SELF-2 reboot (6 h commitment active, secure lock screen) | **Pass.** Accessibility rebound by itself before unlock. `BOOT_COMPLETED` arrived only after the user unlocked (about 90 s later in this run) and the FGS restarted. Instagram was blocked after reboot and the commitment was intact |

### Parent / child (one phone as the child; parent driven through the API with curl)

| Test | Result |
| --- | --- |
| Link with parent code | **Pass** |
| Parent sees the child's launchable apps | **Pass**: 208 apps with real names |
| Parent always-on rule (Instagram) → child | **Pass** after the child syncs. Sync happens on app open or every 15 min in the FGS. **There is no push**, so a rule can take up to 15 min to arrive |
| Parent status shows tamper flags | **Pass**: `device_admin_off`, `adb_enabled_on` |
| Device admin active → `pm uninstall` | **Pass**: `DELETE_FAILED_DEVICE_POLICY_MANAGER` |
| Device admin active → App info "Force stop" | **Pass**: button disabled (Pixel / AOSP) |
| Uninstall dialog, Accessibility settings, Savvy App info under an always-on rule only | **Bug fixed.** They opened freely because the guard only ran during a focus commitment. After the fix: blocked in 34 to 69 ms |
| Settings → Security → More security settings → Device admin apps → Savvy (deactivate) | **Pass**: blocked in 132 ms (`DeviceAdminAdd`) |
| Usage access list | Added to the guard list (`UsageAccessSettingsActivity`) |
| `adb shell dpm remove-active-admin` | Refused (not a test-only admin) |
| **`adb shell pm clear` with admin active** | **Succeeds.** All Savvy state is wiped and a11y disabled; Instagram opens. The admin stays active, so uninstall is still blocked. The parent sees only `adb_enabled_on` until the heartbeat is stale (30 min). A parent should be alerted when USB debugging is on |

---

**Automated state (25 September 2026, before the device run):**
- `core` module: **21 JVM tests pass** (`gradle :core:test`), including the cross-implementation tests against backend-issued cards and grants.
- `app` module: **compiles against the real Android 16 (API 36) framework** (`gradle :compilecheck:compileKotlin`, Robolectric android-all), with documented stubs for AndroidX / ZXing / R / BuildConfig.
- **9 Robolectric end-to-end tests pass** (`gradle :compilecheck:test`). They run the real app classes on Google's Android 16 framework with Robolectric shadows, against the real backend. They are mutation-checked.
- Not yet built into an APK or run on a phone: the Android SDK and Google Maven were blocked in the R&D environment.
- Backend: 22 tests pass.
- Device results below read **Not run yet** until a tester records them.

## Test devices (minimum)

| Device | Why |
| --- | --- |
| Google Pixel, Android 16 or 17 | AOSP baseline; Android 17 AAPM test |
| Samsung Galaxy (One UI 7/8) | Largest market share; sleeping apps |
| Xiaomi / Redmi (HyperOS) | Autostart, aggressive killing; large India share |
| OnePlus | Battery optimization reverts |
| Oppo or Vivo | Kills accessibility services on screen off |
| One Android 8 to 12 device | minSdk 26 path, BouncyCastle Ed25519 |

---

## Automated results (this environment)

| Test | Result |
| --- | --- |
| TimeIntegrity: trusted, clock moved forward ignored in same boot, server end time after reboot, offline reboot gap documented | **Pass** (4 tests) |
| RestrictionPolicy: no commitment allows, blocks selected app, never blocks Savvy / launcher / dialer, expires by monotonic time, pause and reboot, tamper screens only in PARENT mode | **Pass** (6 tests) |
| CrossImplementation: parse static / signed / SUN formats; backend-signed card verifies offline only for bound card; forged card rejected; SUN not verifiable offline; backend grant verifies; pause grant; expiry, wrong device, wrong commitment, tampering and wrong key rejected | **Pass** (4 tests) |
| Robolectric: repository and offline queue | **Pass** |
| Robolectric: block Instagram, BlockActivity launched, wrong card refused, right card releases (A-BLOCK-1 logic, A-NFC-8) | **Pass** |
| Robolectric: offline start + offline signed-card unlock + sync (A-NFC-9) | **Pass** |
| Robolectric: offline emergency limit | **Pass** |
| Robolectric: parent always-on + UsageStats totals reach parent (A-USAGE-1 logic) | **Pass** |
| Robolectric: card App Link opens BlockActivity and unlocks (A-NFC-2 logic) | **Pass** |
| Robolectric: BOOT_COMPLETED restarts enforcement (A-SELF-2 logic) | **Pass** |
| Robolectric: tamper flags reach parent | **Pass** |
| Robolectric: parent mode guards DeviceAdminAdd, self mode does not (A-PARENT-2 logic) | **Pass** |
| Core: OfflinePolicy (same rules as iOS), parent always-on, UsageAggregator midnight split | **Pass** (7 tests) |

---

## POC status blocks (section 59 format)

```text
POC: AND-POC-2/3 Restriction
Platform: Android
Mode: Self and Parent
Requirement: Selected apps become inaccessible during focus
Status: Decision logic unit-tested; app code written; device test pending
Exact End Goal: Instagram cannot be used while commitment is active, from any launch path
Approaches Investigated: UsageStats polling, AccessibilityService, overlay, block Activity, FGS, device admin, Device Owner
Approach Implemented: AccessibilityService (primary) + UsageStats FGS (fallback) -> BlockingEngine -> BlockActivity
Code Location: android/SavvyRDAndroid/app/.../service, block; core/RestrictionPolicy.kt
Hardware Tested: None yet
OS Version: Not tested
Permissions: Accessibility, Usage access, Display over other apps, Notifications
Entitlements: Play Accessibility declaration, FGS specialUse declaration
Actual Result: Not run yet
Limitations: Force stop disables accessibility; OEM killers
Known Bypasses: Safe mode, disable service in Settings (self mode)
Alternative Approaches: UsageStats-only build if Play refuses Accessibility
Store Policy Risk: Medium (declaration review)
Additional Test Required: A-BLOCK-*, A-OEM-*
Final Recommendation: Ship both paths, Accessibility preferred
Evidence: core unit tests; developer.android.com references in ANDROID_FEASIBILITY.md
```

```text
POC: AND-POC-8 Parent anti-uninstall
Platform: Android
Mode: Parent
Requirement: Child cannot remove Savvy
Status: Code written; device test pending
Approach Implemented: Device admin + Accessibility guard of tamper screens (PARENT only) + heartbeat flags
Code Location: admin/SavvyDeviceAdminReceiver.kt, core/RestrictionPolicy.TAMPER_SCREENS, service/TamperMonitor.kt
Actual Result: Not run yet
Known Bypasses: Safe mode, factory reset, Android 17 AAPM (accessibility revoked), OEM screen names not in list
Store Policy Risk: Allowed for parental control (Official-excerpt); monitoring policy obligations
Final Recommendation: Use; describe as "strong protection with parent alerts", not "cannot be removed"
```

```text
POC: AND-POC-9/10 NFC unlock
Platform: Android
Status: Card and grant verification cross-tested; app code written; device and card test pending
Approach Implemented: Reader mode on BlockActivity + NDEF_DISCOVERED / App Link intent filter
Actual Result: Not run yet
Final Recommendation: Add AAR as second NDEF record where chip memory allows
```

---

## Device tests

### Blocking (A-BLOCK)
Record each test at 60 fps. Measure the frames from Instagram's first frame to the Savvy screen.

| Test | Launch path | Path under test | Expected | Visible time | Actual |
| --- | --- | --- | --- | --- | --- |
| A-BLOCK-1 | Launcher | Accessibility | Home + block screen | < 300 ms | Not run yet |
| A-BLOCK-2 | Launcher | UsageStats only (a11y off) | Block screen | about 1 s | Not run yet |
| A-BLOCK-3 | Notification | Both | Blocked | | Not run yet |
| A-BLOCK-4 | Recent apps | Both | Blocked | | Not run yet |
| A-BLOCK-5 | Deep link from Chrome / WhatsApp | Both | Blocked | | Not run yet |
| A-BLOCK-6 | instagram.com in Chrome | Both | **Not blocked** (browser is not Instagram). Record; website blocking is a separate feature | | Not run yet |
| A-BLOCK-7 | Assistant / search "open Instagram" | Both | Blocked | | Not run yet |
| A-BLOCK-8 | Split screen with Instagram in second pane | Both | Blocked; record behaviour | | Not run yet |
| A-BLOCK-9 | Picture-in-picture (Instagram video) | Both | Record | | Not run yet |
| A-BLOCK-10 | Savvy, launcher, dialer, emergency call | Both | Never blocked | | Unit-tested **Pass**; device Not run yet |
| A-BAL-1 | Accessibility path launches BlockActivity from background on Android 14, 15, 16, 17 | - | Launch succeeds; else use accessibility overlay | | Not run yet |

### OEM reliability (A-OEM)
Run the following on every device in the list:
1. Start a 6-hour commitment.
2. Lock the screen for 30 minutes, 2 hours and overnight.
3. After each, open Instagram.

| Test | Pixel | Samsung | Xiaomi | OnePlus | Oppo/Vivo |
| --- | --- | --- | --- | --- | --- |
| A-OEM-1 30 min idle | | | | | |
| A-OEM-2 2 h idle | | | | | |
| A-OEM-3 overnight | | | | | |
| A-OEM-4 after swipe from recents | | | | | |
| A-OEM-5 after OEM "clean memory" button | | | | | |
| A-OEM-6 accessibility still enabled after 24 h | | | | | |
| A-OEM-7 settings screen class names (for TAMPER_SCREENS) | | | | | |

### Anti-bypass (A-SELF, A-PARENT)
See ANDROID_BYPASS_MATRIX.md for the expected result of each row.

### NFC (A-NFC)
| Test | State | Expected actions | Actual |
| --- | --- | --- | --- |
| A-NFC-1 | Block screen visible, hold card | 1 | Not run yet |
| A-NFC-2 | Savvy background, tap card | 1, Savvy opens and unlocks | Not run yet |
| A-NFC-3 | Savvy force-stopped, tap card | 1, app launches (stopped state is cleared by user action) | Not run yet |
| A-NFC-4 | Phone locked | Not read until unlocked | Not run yet |
| A-NFC-5 | NFC off | Block screen offers NFC settings / QR | Not run yet |
| A-NFC-6 | Vendor card on each OEM | Record read time, distance | Not run yet (needs card) |
| A-NFC-7 | Android 16+, tag intents disabled for Savvy | Background tap ignored, reader mode works, TamperMonitor reports | Not run yet |
| A-NFC-8 | Wrong card, random tag, another user's card | Rejected | Backend **Pass**; device Not run yet |
| A-NFC-9 | Offline, signed card | Offline unlock | Kotlin core **Pass**; device Not run yet |

### QR (A-QR)
These are the same cases as iOS T-QR-1 to 6, run on Android.

### Crypto (A-CRYPTO)
| Test | Expected | Actual |
| --- | --- | --- |
| A-CRYPTO-1 | Ed25519 grant verification works on API 26 to 32 (BouncyCastle) and 33+ | Not run yet |

### Insights (A-USAGE)
| Test | Expected | Actual |
| --- | --- | --- |
| A-USAGE-1 | Daily per-app totals match Digital Wellbeing within 5 percent | Not run yet |
| A-USAGE-2 | Events older than about 7 days unavailable (store our own totals) | Not run yet |
