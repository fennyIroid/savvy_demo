# Savvy Android POC Results and Device Test Plan

Status date: 25 September 2026.

**Current state:**
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
