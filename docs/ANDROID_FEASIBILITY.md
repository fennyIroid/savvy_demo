# Savvy Android Feasibility Report

Research date: 25 September 2026. Current Android: Android 17 (API 37, stable since 16 June 2026). Android 17 QPR2 is in beta. Google Play requires target API 36 for new apps from 31 August 2026.

POC code: `android/SavvyRDAndroid`.
- `core` builds, and its 21 JVM tests pass.
- `app` compiles against the real Android 16 (API 36) framework (Robolectric android-all), and 9 Robolectric end-to-end tests run it against the real backend.
- It has not yet been built into an APK or run on a phone, because the Android SDK and Google Maven were blocked here.

Classification codes and evidence levels are the same as `IOS_FEASIBILITY.md`.

**Source note:**
- Android platform facts were read directly on developer.android.com (**Official**).
- Google Play policy pages (support.google.com) could not be opened from the R&D environment. Play policy statements below are from official page excerpts (**Official-excerpt**).
- A person must re-read those four pages in a browser before submission: 10964491, 16558241, 10158779 and 12955211 (STORE_POLICY_RISKS.md).

---

## Summary of the Android answer

1. **Blocking works well technically.** Two public mechanisms are both implemented, sharing one decision engine:
   - an AccessibilityService, which reacts in milliseconds to every launch path;
   - UsageStats polling in a foreground service, which reacts in about 1 second.
2. **Google Play is the real constraint, not the API:**
   - Accessibility use for app blocking needs a Play declaration, prominent disclosure, consent and a demo video.
   - **Using Accessibility to stop disabling or uninstalling is only allowed in parent-authorized parental control mode.**
3. **Self-use uninstall prevention is not allowed on Play**, and it is not reliably possible either:
   - Safe mode disables third-party apps.
   - Force stop disables the accessibility service.
   - The near-goal is the same as on iOS: server restore, detection and friction.
4. **Parent mode can be much stronger:**
   - device admin (uninstall must go through deactivation, and AOSP blocks Force stop and Clear storage for admins);
   - an Accessibility guard on the deactivate, uninstall and accessibility screens;
   - tamper alerts.

   Safe mode, factory reset and Android 17 Advanced Protection remain bypasses. Only Device Owner closes them, and Device Owner is not practical for a consumer Play app.
5. **NFC is simpler than iOS:**
   - A card tap opens Savvy directly (1 action) when the phone is unlocked.
   - While the Savvy block screen is showing, holding the card unlocks with no other tap.
6. **Screen-time data is available.** UsageStats gives per-app durations that Savvy can store and show to a parent (with disclosure and the monitoring rules).
7. **OEM behaviour is the biggest delivery risk.** Samsung, Xiaomi, OnePlus, Oppo and Vivo kill background work and sometimes disable accessibility services. Real-device testing on each brand is mandatory.

---

## Android R&D 1. Installed application selection

- Add a `<queries>` launcher intent (ACTION_MAIN plus CATEGORY_LAUNCHER). This makes every launchable app visible without `QUERY_ALL_PACKAGES` (Official). Play restricts `QUERY_ALL_PACKAGES`, and its examples do not name digital wellbeing or parental control (Official-excerpt). **Do not request it.**
- Savvy gets real package names and labels.
  - Unlike iOS, the selection can be stored on the backend.
  - A parent can pick from a list reported by the child device.
- Uninstall and reinstall of Instagram: the package name stays the same, so the selection still works.

**POC:** `picker/AppPickerActivity.kt`.

**Classification:** A.

---

## Android R&D 2. Usage detection and blocking: approach comparison

| Approach | Reaction | Launch paths covered | OEM risk | Play policy | Anti-bypass | Decision |
| --- | --- | --- | --- | --- | --- | --- |
| A. UsageStats `queryEvents` ACTIVITY_RESUMED | Polling, about 0.75 to 1.5 s; app visible briefly | All (it sees the resumed activity) | High (service killed) | Usage access grant; no Play declaration found | User can revoke Usage access | **Fallback** |
| B. AccessibilityService window events | Near instant | All, including split screen and notification launch | Medium (Oppo disables on screen off, force stop disables) | Declaration + disclosure + consent + video; isAccessibilityTool=false | User can turn it off in Settings | **Primary** |
| C. Block Activity / overlay | Shows Savvy screen | - | Low | SYSTEM_ALERT_WINDOW "very few apps should use" (Official) | An app can hide overlays with setHideOverlayWindows | **Used**: Activity, not overlay |
| D. Foreground service | Keeps A alive, shows persistent notification | - | Medium | specialUse FGS declaration + video | Force stop kills it | **Used with A** |
| E. Device admin | Uninstall must go through deactivation | - | Low | Allowed for parental control; self-use unclear | Deactivate in Settings | **Parent mode only** |
| F. Device Owner / Profile Owner | Full control (suspend apps, block uninstall and safe boot) | - | Low | Custom DPC registrations closed; Play Protect limits QR provisioning | Strongest | **Rejected for consumer** |

**Details:**
- The Accessibility binding runs at high priority while the screen is on. The service can go Home at once (`GLOBAL_ACTION_HOME`) and then open the block screen.
  - The public background-activity-launch documents no longer list accessibility services as an exemption. **Test A-BAL-1.**
  - If the launch is blocked, the fallback is an accessibility overlay window (`TYPE_ACCESSIBILITY_OVERLAY`), which needs no SYSTEM_ALERT_WINDOW (Official).
- The UsageStats path launches the block Activity from the background. This is allowed because the user granted SYSTEM_ALERT_WINDOW, a documented exemption (Official).
- Android 15 or later: an app can hide non-system overlays. So an Activity is used rather than a pure overlay.

**Classification:** A + E (Play declaration) + F (OEM). The same engine serves both paths, so they can be measured against each other.

---

## Android R&D 3. Restriction POC

The flow is implemented:
1. Select Instagram.
2. Start the commitment.
3. The engine sees the Instagram window.
4. It goes Home, and the Savvy block screen appears.

**To measure (see ANDROID_POC_RESULTS.md):**
- how long Instagram is visible, from the screen recording at 60 fps;
- whether content flashes;
- false positives;
- launch paths: launcher, notification, recents, deep link, browser, search, split screen, picture-in-picture.

**Classification:** A + F.

---

## Android R&D 4. OEM reliability

From dontkillmyapp.com (Community) and AOSP:

| OEM | Known issue | Savvy mitigation |
| --- | --- | --- |
| Google Pixel | Closest to AOSP | Baseline |
| Samsung | Sleeping and deep-sleeping apps, adaptive battery | Onboarding step: "Never sleeping apps" |
| Xiaomi / Redmi (HyperOS) | Autostart permission, battery saver | Onboarding: Autostart on, "No restrictions" |
| OnePlus | Battery optimization reverted randomly | Lock app in recents, onboarding |
| Oppo / Vivo | Background services killed on screen off, **including accessibility services, which must be re-enabled** | Detect in TamperMonitor, re-prompt user; parent alert |

**Additional points:**
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: Play allows the direct request only where the core function is harmed. The acceptable list includes safety apps for families (Official). Parent mode fits better than self mode. For self mode, open the settings screen instead of the direct dialog.
- The OEM test plan is in ANDROID_POC_RESULTS.md. **Savvy cannot be called reliable on Android from Pixel or emulator results alone.**

**Classification:** C + F.

---

## Android R&D 5. AccessibilityService policy

**Current Play policy (Official-excerpt):**
- Accessibility is allowed for non-accessibility tools with:
  - a Play Console declaration;
  - an in-app prominent disclosure;
  - affirmative consent;
  - a video showing the flow;
  - store listing disclosure.
- `isAccessibilityTool=true` is only for disability tools. Play rejects false claims, and Play Protect blocks them (Official, December 2025 blog).
  - Savvy must keep it **false**.
  - The system will then show a periodic privacy reminder about the service (Official).
- Prohibited: using Accessibility to "change user settings without their permission or prevent the ability for users to disable or uninstall any app or service **unless authorized by a parent or guardian through a parental control app** or by authorized administrators through enterprise management software".
- 2025 policy: Accessibility must not be used for autonomous AI-agent actions. Savvy's deterministic "if the app is blocked, go Home" rule is allowed.

**What Savvy does in each mode:**
- **Self mode:** detects the app and shows the block screen. It does **not** block Settings, deactivation or uninstall. Implemented in `RestrictionPolicy`: tamper screens are blocked only in PARENT mode (unit-tested).
- **Parent mode:** also guards the deactivate-admin, uninstall, accessibility-settings and Savvy app-info screens. Allowed "when authorized by a parent or guardian through a parental control app".

**Other points:**
- The disclosure text is in `strings.xml`. The disclosure dialog is shown before Settings opens (`MainActivity.showDisclosure`).
- There is community evidence that reviewers push blocker apps toward UsageStats when that is enough (Community). Savvy's reason for needing Accessibility: instant reaction on all launch paths without showing blocked content. Keep UsageStats as the working fallback if the declaration is refused.

**Classification:** E. The policy fit is reasonable, but approval is not guaranteed.

---

## Android R&D 6. Self-use anti-bypass

| Bypass | Result | Savvy response |
| --- | --- | --- |
| Disable Accessibility in Settings (no authentication) | Allowed | UsageStats fallback continues if granted; detect; record |
| Disable Usage access | Allowed | Accessibility continues if on; detect |
| Remove overlay permission | Allowed | UsageStats path cannot launch block screen from background; Accessibility path still works |
| Force stop Savvy | Allowed; **permanently disables the accessibility service** (AOSP) and kills FGS; app in "stopped state" until opened (Android 15) | Detect on next open; server keeps commitment |
| Clear app data | Allowed; local commitment gone | Server restores after login |
| Uninstall | Allowed | Server restores after reinstall |
| Restart | Accessibility re-bound by system if still enabled; FGS restarted by BOOT_COMPLETED (specialUse allowed) | Prevent |
| Battery restriction / OEM killer | Services may die | Onboarding and detection |
| Safe mode | All third-party apps off (Official-excerpt) | None |
| Date/time change | `elapsedRealtime` + `BOOT_COUNT` in the same boot (unit-tested); server time after reboot when online | Prevent (same boot), Detect |
| Revoke notifications | FGS still runs; notification hidden | None needed |

**Classification:** C. As on iOS, the commitment holds against impulse, not against a determined user.

---

## Android R&D 7. Self-use uninstall delay (6 or 24 hours)

**Approaches investigated:**

| # | Approach | Technical result | Policy result |
| --- | --- | --- | --- |
| 1 | Accessibility blocks uninstall and deactivation screens | Works on tested AOSP class names (unit-tested policy); OEM names differ | **Prohibited on Play for self-use** |
| 2 | Device admin (no policies) | Uninstall must go through deactivation; user can deactivate at any time | Not explicitly prohibited; risk Medium; deprecated for non-enterprise in spirit |
| 3 | Device Owner `setUninstallBlocked`, `DISALLOW_UNINSTALL_APPS`, `DISALLOW_SAFE_BOOT` | Real prevention | Needs factory reset or ADB provisioning; custom DPC registrations closed; Play Protect restricts provisioning. **Unsuitable for consumer deployment** |
| 4 | Profile Owner (work profile) | Controls apps inside the work profile only, not personal Instagram | Not useful |
| 5 | Lock task mode (`setLockTaskPackages`) | Needs DO | Not useful for consumer |
| 6 | Safe mode prevention | Only `DISALLOW_SAFE_BOOT` (DO/PO) | Not available |

**Conclusion:**
- Real uninstall prevention in self-use is technically possible only under Device Owner, which is special device management and unsuitable for normal Savvy consumer deployment.
- With normal Play distribution, it is **not allowed and not reliable**.

**Near-goal:**
- the same server-side commitment restore as iOS (backend-tested);
- detection of disabled services;
- an optional device-admin "extra step" only if a policy check allows it for self mode (OPEN_ITEMS).

**Classification:** D. The exact requirement is H for Play self-use.

---

## Android R&D 8. Parent / child anti-uninstall

**Design (implemented, device test pending):**
1. **Child device.** A parent sets up Savvy on the child's phone, enables Accessibility, Usage access and overlay, and activates device admin. `controlMode = PARENT`.
2. **Device admin:**
   - uninstall is redirected to the deactivation screen;
   - AOSP Settings disables Force stop and Clear storage for active admins (AOSP source; OEM may differ).
3. **Accessibility guard in PARENT mode.** It blocks these screens:
   - DeviceAdminAdd and device admin settings;
   - the uninstaller;
   - Accessibility settings;
   - Savvy's App info.

   This is allowed by Play for parental control. OEM class names must be collected during testing.
4. **Tamper detection.** The heartbeat reports accessibility, usage access, overlay, admin, NFC, tag intents and Advanced Protection. The parent sees flags (backend-tested).
5. **Parent authorization representation:**
   - the family link on the backend;
   - `controlMode = PARENT`;
   - a parent card or PIN needed to leave parent mode (product rule).

**Remaining bypasses (cannot be closed without Device Owner):**
- **Safe mode:** the child can boot to safe mode, deactivate admin and uninstall. It is detected afterwards (heartbeat stops, admin disabled).
- **Factory reset:** detected (device stops reporting).
- **Android 17 Advanced Protection Mode:** revokes non-tool accessibility services (Community reports from Android 17 Beta 2). This is detected by `TamperMonitor.advancedProtection`, and the UsageStats fallback continues.
- **OEM Settings screens** with different class names: needs a per-OEM list.
- **ADB** (developer options): the heartbeat reports `Settings.Global.ADB_ENABLED` (implemented in TamperMonitor), so the parent can see it.

**Play obligations for this mode:**
- Stalkerware/monitoring policy: a persistent notification while running, and disclosure in the listing.
- `isMonitoringTool=child_monitoring` meta-data if child data is sent to the parent.
- The Families policy only if children are a target audience. List the app for parents.

**Classification:** C + E + F. Much stronger than self mode, but not absolute.

---

## Android R&D 9 and 10. NFC and end-to-end unlock

**Findings (Official):**
- A manifest `NDEF_DISCOVERED` filter with the card's https URL (plus an App Link `VIEW` filter) launches Savvy even when it is not running. There is no notification step.
- An Android Application Record (AAR) as the second NDEF record guarantees Savvy is chosen, or opens Play if Savvy is not installed. iOS ignores it, because iOS uses the first URI record. **Recommended when memory allows** (NTAG215/216 or 424 DNA).
- Reader mode (`enableReaderMode`) while the block screen is visible: the user just holds the card.
- Tags are "usually" read only when the screen is unlocked.
- Android 16+: the user can switch off tag intents per app (`isTagIntentAllowed`). `TamperMonitor` detects this, and reader mode still works on the block screen.
- `Tag.getId()`: some tags give random IDs. Do not use the UID for identity.
- MIFARE Classic works only on phones with NXP controllers. Do not use it.

**End-to-end flows:**

| State | Flow | Actions |
| --- | --- | --- |
| Blocked app opened | Block screen appears automatically, hold card | 1 (hold card) |
| Savvy in background or killed, phone unlocked | Tap card, Savvy opens and unlocks | 1 |
| Phone locked | Unlock phone, then tap card | 2 |
| NFC off | Button to NFC settings, or QR | 2 to 3 |

The same `UnlockCoordinator` logic as iOS is used, and the backend verification is shared (backend-tested). Grants and cards verify in Kotlin (cross-implementation tests pass).

**Classification:** A + F + G. Android is closer to the client's "tap to unlock" than iOS.

---

## Android R&D 11. QR

This uses the same card URL and the same backend checks as iOS (see QR_FINDINGS.md). The scanner is ZXing embedded, live camera only.

**Classification:** A (function), C (copy risk).

---

## Android R&D 12. To-do, commitment and emergency exit

- The same backend logic is used (backend-tested).
- On the device, expiry is computed from `elapsedRealtime` and `BOOT_COUNT` (unit-tested). No alarm is needed, because the policy is checked on every app switch.
- A pause after the card or an emergency exit is stored as elapsed time in the same boot. A reboot ends the pause, which is the safe direction (unit-tested).
- There is no 15-minute minimum on Android. Short tasks are possible.

**Classification:** A + F.

---

## Android R&D 13. Usage insights

**Findings (Official):**
- `UsageStatsManager.queryEvents` and `queryUsageStats(INTERVAL_DAILY)` give per-app foreground time. This needs the Usage access grant.
- Events are kept "only for a few days" by the system. Savvy should compute daily totals and store them.
- The data returns null before the first unlock after boot (Android 11+).
- The new SDK 37.2 `queryAppUsageDuration` is not available to third-party apps (`internal|role`).

**Cross-platform comparison:**

| Item | iOS | Android |
| --- | --- | --- |
| Daily total on device | Yes (report extension) | Yes |
| Per-app minutes | Yes (display only) | Yes |
| Store in Savvy / upload | No (outside EU) | **Yes**, with disclosure and Data safety |
| Parent sees child usage | No | **Yes**, with monitoring policy obligations |

**Classification:** A + E (policy obligations when shared with a parent).

---

## Google Play compliance review (section 46)

See `STORE_POLICY_RISKS.md` for the full list. Required items:
- Accessibility declaration and video.
- Prominent disclosure (implemented).
- Foreground service `specialUse` declaration and video.
- Usage access disclosure.
- Launcher `<queries>` only; no `QUERY_ALL_PACKAGES`.
- Parent mode: monitoring disclosure, persistent notification, `isMonitoringTool`.
- Data safety form.
- Families policy only if children are a target audience.
- Target API 36.

---

## Ed25519 on Android (implementation note)

- `core` uses `java.security` Ed25519. This is available in the JDK and was tested here.
- The Android platform provider is expected to support it from API 33. Below that, the app registers BouncyCastle (`SavvyApp.onCreate`). **Test A-CRYPTO-1** on an API 26 to 32 device.
- If this causes trouble, switch to Google Tink.

---

## Android Final Matrix

| Requirement | Self-Use Android | Parent-Child Android | Evidence | Limitation | Alternative |
| --- | --- | --- | --- | --- | --- |
| App selection | A | A | Official; POC | None significant | - |
| App restriction | A, E, F | A, E, F | Official; core unit-tested | Play declaration; OEM killers | UsageStats fallback |
| Survives Savvy kill | C | C | AOSP: force stop disables a11y | Force stop defeats it until reopened | Detect; admin blocks force stop in parent mode |
| Survives restart | A, F | A, F | Boot receiver; a11y re-bound | OEM autostart | Onboarding |
| 6 / 24-hour commitment | A, F | A, F | Unit-tested timing; backend-tested | Clock change after reboot offline | Server time |
| NFC foreground | A, G | A, G | Official | NFC may be off | QR |
| NFC background | A, F | A, F | Official tag dispatch | Unlocked phone; Android 16 toggle | Reader mode on block screen |
| NFC unlock | A, F, G | A, F, G | Backend + Kotlin cross tests | - | QR |
| QR unlock | A, C | A, C | Backend-tested | Copyable | Delay or limit |
| Prevent uninstall | D (H exact) | C (admin + guard) | Play policy; AOSP | Safe mode, factory reset | Detect |
| Prevent permission revocation | D | C | Play policy | Safe mode, AAPM | Detect |
| Parent remote rules | N/A | A, F | Backend-tested sync | Child offline | Reconcile on reconnect |
| To-do restriction | A | A | Backend-tested | Task done is a tap unless card required | Card required |
| Emergency exit | A | A | Backend-tested | - | - |
| Screen-time summary | A | A, E | Official | Monitoring obligations | - |
| Offline enforcement | A | A | Unit-tested policy | SUN card needs network | Signed card offline |
