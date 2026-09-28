# Savvy R&D Master Status

Last updated: 28 September 2026. **Android device run 1** (Pixel 4, Android 13) raised Android to L5 for blocking, card unlock (dispatch intent), offline, commitments, to-do, emergency, reboot, reinstall restore and parent-mode tamper guard. It also found and fixed 8 app bugs; see ANDROID_POC_RESULTS.md.

## 1. Overall state

### Evidence levels used in this repository

| Level | Meaning |
| --- | --- |
| L1 Researched | Official documentation checked and cited |
| L2 Written | POC code exists |
| L3 Compiled | Compiled against the real platform API (Android) or type-checked against documented signatures (iOS) |
| L4 Logic tested | Automated tests run the POC code end to end against the real backend. Android: Robolectric (Google's Android 16 framework with test shadows). iOS: functional fakes of Apple frameworks written by us |
| L5 Device tested | Run on a real phone with a real card and recorded. **Nothing is at L5 yet** |

### Automated test totals (all passing, 25 September 2026)

| Suite | Tests | Command |
| --- | --- | --- |
| Backend (Node.js) | 22 | `cd backend && npm test` |
| iOS SavvyCore (Swift, Linux) | 12 | `cd ios/SavvyCore && swift test` |
| iOS app logic end to end (Swift, Linux, framework fakes, real backend) | 8 | `cd ios/AppHarness && swift test` |
| Android core (Kotlin, JVM) | 21 | `cd android/SavvyRDAndroid && gradle :core:test` |
| Android app end to end (Robolectric, Android 16, real backend) | 9 | `gradle :compilecheck:test` |
| **Total** | **72** | |

Mutation checks were run to prove the end-to-end suites are not vacuous. Disabling the Android parent always-on rule failed its test, and disabling the iOS Monitor clock-tamper re-arm failed its test.

### Where each part stands

| Area | Level | Evidence |
| --- | --- | --- |
| Research (Apple, Google, Play, NXP) | L1 complete | Sources cited in each feasibility document |
| Backend POC | L4 | 22 tests |
| Card cryptography (Ed25519, AES-CMAC, NTAG 424 DNA SUN and live AuthenticateEV2First) | L4 | RFC 4493 and NXP AN12196 vectors; simulated chip for live proof |
| iOS shared logic (SavvyCore) | L4 | 12 tests, incl. backend-issued card and grant verification |
| iOS app non-UI code + Monitor and ShieldAction extensions | L4 (with our fakes) | 8 end-to-end tests. The code has not been built with the real iOS SDK |
| iOS SwiftUI views, ShieldConfiguration, Report extension, App Intents | L2 plus syntax check | Needs Xcode |
| Android core | L4 | 21 tests |
| Android app | **L5 on Pixel 4 / Android 13** for the flows listed in ANDROID_POC_RESULTS.md device run 1; L4 Robolectric (11 tests) | Real APK (AGP). Not yet: physical NFC tag, QR camera, other OEMs, Android 14+ |
| Real-device tests | Android: Pixel 4 run done (28 Sep). iOS: not run | No devices, Apple account or cards in the R&D environment. Test plans are in IOS_POC_RESULTS.md and ANDROID_POC_RESULTS.md |

**Rule followed.** Following section 8 of the brief, nothing is marked Not Feasible because of an environment limitation. Items marked H were concluded only after the full approach search, and the reason is written down.

## 2. POC register (section 59, short form; detail in the platform results files)

| POC | Platform | Mode | Level | Code | Final recommendation |
| --- | --- | --- | --- | --- | --- |
| Authorization | iOS | Self / Child | L2, L3 | App/Authorization | .individual for self, .child for parent mode |
| App selection | iOS | Both | L2 (UI) | ContentView, SharedState | FamilyActivityPicker; re-select after reinstall; token refresh on iOS 26.5+ |
| Shield apply / clear | iOS | Both | L4 (fakes) | Shared/ShieldEngine | Named stores; commitment guard during commitments |
| Shield handoff (button opens Savvy) | iOS | Both | L4 (fakes) | Extensions/ShieldAction | openParentalControlsApp on 26.5+, notification before |
| Commitment scheduling and clock tamper | iOS | Both | L4 (fakes) | Shared/ScheduleEngine, Extensions/Monitor | DeviceActivity + monotonic re-arm + server reconcile |
| NFC foreground / background | iOS | Both | L2, L3 | App/NFC | Tag session (UID + URL); background as bonus path |
| NFC card to shield removal | iOS | Both | L4 (fakes) | App/Unlock | Shield button + hold card; wrong card refused |
| NTAG 424 DNA live proof | All | Both | L4 (backend, simulated chip); relay L3 | ntag424Auth.js, LiveProofRelay | Optional anti-copy upgrade |
| Card identity | All | Both | L4 | backend/src/crypto | Signed URL (MVP) or NTAG 424 DNA (anti-copy) |
| QR | All | Both | L4 (backend); scanner L2 | App/QR, block/BlockActivity | Same URL + delay or limit |
| Offline start / unlock / sync | All | Both | L4 (iOS fakes, Robolectric) | OfflinePolicy, /v1/sync | Signed cards offline; replay on reconnect |
| Self anti-bypass / uninstall | iOS | Self | L1 concluded; near-goal L4 | ShieldEngine.setRemovalGuard, restore | Commitment lock + restore + detect |
| Reinstall restore | All | Both | L4 (iOS fakes) | AppModel.restoreOnLaunch | Same end time after reinstall |
| Parent-child rules | iOS | Child | L4 (fakes) | AppModel.syncParentRules | Parent-device picker blob + always-on store |
| To-do | All | Both | L4 | Tasks, commitments API | Card optional per policy |
| Emergency exit | All | Both | L4 | commitmentService, OfflinePolicy | Configurable limit; offline 1 per week |
| Screen-time summary | iOS | Both | L2 | Extensions/Report | On-device only |
| Focus / streak | All | Both | L4 | insightsService | Done |
| App selection | Android | Both | L3 | picker | Launcher queries |
| Blocking | Android | Both | L4 (Robolectric) | service/*, core | Accessibility + UsageStats fallback + overlay fallback |
| OEM reliability | Android | Both | Test plan ready | - | Onboarding + detection |
| Self uninstall delay | Android | Self | L1 concluded | - | Restore + detect (no Play-compliant prevention) |
| Parent anti-uninstall | Android | Parent | L4 (guard decision, Robolectric) | admin, RestrictionPolicy | Admin + guard + alerts |
| Parent remote rules and usage | Android | Parent | L4 (Robolectric) | SavvyActions, ChildAppPickerActivity | Package names + always-on + usage upload |
| NFC unlock | Android | Both | L4 (App Link path, Robolectric) | nfc, block | Reader mode + App Link, 1 action |
| Tamper flags to parent | Android | Parent | L4 (Robolectric) | TamperMonitor, heartbeat | Detect and show to parent |
| Reboot persistence | Android | Both | L4 (Robolectric boot receiver) | BootReceiver | Restart enforcement |

---

## 3. iOS executive result (section 62)

**Fully achievable:**
- app selection;
- shielding;
- focus modes;
- 6-hour and 24-hour commitments (server-backed);
- to-do restriction;
- emergency exit;
- focus time and streaks;
- parent rules from the parent's iPhone (Family Sharing).

**OS-mandated UX differences:**
- The card must be read by the Savvy app in the foreground. Unlocking takes 2 actions on iOS 26.5+ (shield button, then hold card) and 3 on older iOS.
- Background NFC always needs a notification tap.
- The minimum session is 15 minutes.

**Limitations:**
- In self-use, the owner can revoke Screen Time access with Face ID, which removes all restrictions.
- The screen-time summary cannot be stored, uploaded or shown to the parent.
- A static QR is copyable.
- DeviceActivity has community-reported reliability issues.
- App tokens are opaque and must be re-selected after a reinstall.

**Needs entitlement approval:** Family Controls distribution for the app and 4 extensions.

**Cannot be guaranteed:**
- self-use uninstall prevention;
- self-use permission lock;
- silent one-tap NFC unlock.

**Strongest alternatives:**
- commitment lock (`denyAppRemoval` plus forced automatic time);
- server-side commitment restore;
- revoke detection;
- the shield-to-app handoff.

**Recommended iOS production architecture:**
1. The main app does authorization, picker, NFC, QR and backend communication.
2. The DeviceActivityMonitor extension ends commitments and pauses, and re-arms if the clock was changed.
3. The ShieldConfiguration and ShieldAction extensions show the Savvy shield and hand off to the app.
4. The DeviceActivityReport extension shows the summary.
5. The App Group holds the shared state.
6. The backend holds the commitment end times, card binding, signed grants and parent rules.

**Evidence so far:**
- The non-UI app code and the Monitor and ShieldAction extensions run end to end against the real backend on Linux (8 tests, with our framework fakes).
- The shared logic is unit-tested (12 tests).
- The SwiftUI screens, ShieldConfiguration, the Report extension and App Intents are syntax-checked only.
- Optional anti-copy upgrade: the NTAG 424 DNA live proof works end to end in backend tests, and the iOS relay is built.

**Remaining real-device tests:**
- all tests in IOS_POC_RESULTS.md;
- the most important are T-SHIELD-3/5, T-SHIELD-ACTION-1/2, T-SCHED-1..3, T-TIME-2, T-UNLOCK-1, T-NFC-BG-5, T-SELF-1..3 and T-PC-2..6.

## 4. Android executive result (section 62)

**Fully achievable:**
- app selection with real app names;
- blocking through Accessibility (instant) or UsageStats (about 1 s);
- commitments without the 15-minute limit;
- NFC unlock with 1 action;
- to-do restriction;
- emergency exit;
- usage insights that can be stored and shared with a parent (with disclosure).

**Platform differences compared with iOS:**
- A card tap opens Savvy directly.
- The block screen is Savvy's own screen.
- Usage data is available to Savvy.

**Limitations:**
- Force stop disables the accessibility service until Savvy is reopened.
- OEM battery killers.
- In self mode, the user can switch off services in Settings.
- Android 17 Advanced Protection revokes non-tool accessibility services.
- Web versions of apps are not blocked in the MVP.

**Needs store declarations:**
- Accessibility (with video);
- foreground service specialUse (with video);
- the monitoring policy for parent mode.

**Cannot be guaranteed:**
- self-use uninstall prevention (Play prohibits it through Accessibility, and safe mode defeats it anyway);
- parent-mode protection against safe mode and factory reset (these need Device Owner).

**Strongest alternatives:**
- two detection paths;
- server restore;
- tamper flags to the parent;
- device admin plus the Accessibility guard in parent mode.

**Recommended Android production architecture:**
- AccessibilityService (window events only) and a UsageStats foreground service, both feeding one policy engine;
- BlockActivity with NFC reader mode;
- NDEF_DISCOVERED / App Link for background taps;
- the boot receiver;
- device admin in parent mode only;
- the same backend as iOS.

**Evidence so far:**
- The app compiles against the real Android 16 framework.
- 9 Robolectric end-to-end tests pass: block, card unlock, offline unlock and sync, parent rules and usage, tamper flags, reboot and the parent-mode guard.
- The core logic is unit-tested (21 tests).
- Not yet an APK.

**Remaining real-device tests:**
- all tests in ANDROID_POC_RESULTS.md;
- the most important are A-BLOCK-1..8, A-BAL-1, A-OEM-1..7 on 5 brands, A-NFC-1..7 and A-CRYPTO-1.

---

## 5. Answers to the 33 main questions (section 68)

| # | Question | Answer | Confidence |
| --- | --- | --- | --- |
| 1 | Can Savvy reliably shield selected apps on iOS? | Yes, with ManagedSettings (Apple's core feature). "Reliably" is to be confirmed on device across launch paths | Official + device test pending |
| 2 | Does the iOS restriction survive backgrounding or termination? | Expected yes: the shield is held by the system, not the app | Community + test T-SHIELD-3 |
| 3 | Does it survive restart? | Expected yes | Community + test T-SHIELD-5 |
| 4 | Can to-do completion reliably remove restrictions? | Yes: completion goes to the backend, which returns a signed grant, and the app clears the shield. Backend-tested | Backend-tested |
| 5 | Can DeviceActivity support commitment periods reliably? | Yes by design (15 min to 1 week). There are reports of missed intervalDidEnd, so Savvy also reconciles with the server on every launch; the failure direction is "stays locked longer", never "unlocks early" | Official + Community |
| 6 | Can NFC physically take part in unlocking? | Yes on both platforms | Official; backend-tested |
| 7 | Minimum taps for NFC unlock on iOS? | 2 on iOS 26.5+ (shield button, hold card) or background (tap card, tap notification). 3 on older iOS via the shield | Official; device test pending |
| 8 | Can Savvy handle NFC when not foregrounded? | iOS: yes via background tag reading, after the notification tap (iPhone XS+). Android: yes, the tag opens Savvy directly | Official |
| 9 | Best card format for both? | An NDEF URI record with Savvy's HTTPS card URL. NTAG215/216 (signed URL) or NTAG 424 DNA (SUN). Not MIFARE Classic | Official + backend-tested |
| 10 | Can card identity be validated securely enough for MVP? | Yes: server binding (1 card per account) plus Ed25519 signature or AES-CMAC SUN | Backend-tested |
| 11 | How copyable is the NFC card? | NTAG21x: fully copyable (URL, UID, even originality signature). NTAG 424 DNA: copies and replays rejected; pre-harvested taps remain possible | Backend-tested + vendor docs |
| 12 | How copyable is the QR? | Fully: a photo works. It proves identity only | Backend-tested |
| 13 | Can self-use iOS prevent uninstall for 6 or 24 hours? | No. Apple states `.individual` can always be revoked in Settings | Official |
| 14 | Strongest near-goal? | Commitment lock (denyAppRemoval + forced auto time), server restore after reinstall, revoke detection, streak consequence | Official + backend-tested |
| 15 | Can parent-authorized iOS prevent child deletion? | Yes. "Only a parent or guardian can delete your app" | Official |
| 16 | Is Family Sharing mandatory for parent-child? | Yes, for `.child` authorization and its protections | Official |
| 17 | How should parent rules sync? | Parent device, then Savvy backend (versioned rules), then child Savvy (push + pull + ack), which applies locally | WWDC21 model + backend-tested |
| 18 | Can a parent select the child's apps? | Yes, from the parent's iPhone picker if the child is `.child` authorized. Tokens work within the family (token transfer to be device tested) | Official + Community |
| 19 | Child device offline? | The last rule keeps being enforced locally. New rules apply on reconnect. The parent sees "not reporting" | Backend-tested |
| 20 | Child revokes authorization? | iOS: needs parent approval. Android: guarded in parent mode, safe mode remains; the parent is flagged | Official / written |
| 21 | What Screen Time data can Savvy show? | iOS: totals, per app, per category, pickups, notifications, display only. Android: per-app durations from UsageStats | Official |
| 22 | Can the parent see the child's Screen Time summary? | iOS: no (Apple DTS). Android: yes, with monitoring disclosure | Official |
| 23 | Can that data be stored remotely? | iOS: no (outside the EU). Android: yes, with disclosure and Data safety | Official |
| 24 | Which Apple entitlements? | `com.apple.developer.family-controls` (app + 4 extensions), NFC `TAG`, Associated Domains, App Groups | Official |
| 25 | App Store risk remaining? | Entitlement approval time, standard review. Future: 4.10 monetization and "organizational settings" | Official |
| 26 | Best Android restriction architecture? | Accessibility (primary) + UsageStats FGS (fallback), one policy engine, Savvy block Activity | Official + unit-tested |
| 27 | Can Android self mode prevent uninstall? | Not on Play (Accessibility prohibited for this in self mode), and safe mode defeats it anyway. Only Device Owner can, which is unsuitable | Official-excerpt + Official |
| 28 | Can Android parent mode be stronger? | Yes: device admin + Accessibility guard (allowed for parental control) + alerts. Safe mode and factory reset remain | Official-excerpt + AOSP |
| 29 | Does Android need AccessibilityService? | Not strictly: UsageStats works with about 1 s delay and a brief flash of the app. Accessibility gives instant blocking on all launch paths and parent-mode guarding | Official |
| 30 | Will Accessibility comply with Play? | Likely, with declaration, disclosure, consent and video, and no uninstall blocking in self mode. Not guaranteed | Official-excerpt |
| 31 | Behaviour across Pixel, Samsung and others? | Unknown until tested. Known risks on Xiaomi, Oppo/Vivo and OnePlus (killers, accessibility disabled) | Community; test plan ready |
| 32 | Unavoidable platform differences? | NFC action count, iOS 15-minute minimum, iOS Screen Time data privacy, iOS opaque app tokens, Android web blocking, Android OEM behaviour | - |
| 33 | Proposal wording to change? | "Uninstall protection" becomes "commitment lock" (self mode). "Tap card to unlock" becomes platform-specific wording. "Screen-time report to parent" becomes "focus report to parent" (iOS). "QR fallback" gets a delay or limit | See CLIENT_SAFE_SUMMARY.md |

---

## 6. Architecture answers (sections 50 to 54)

**Minimum backend (section 50):**
- users;
- devices (one per install);
- cards;
- card scans;
- commitments (server clock);
- emergency exits;
- focus sessions;
- family link codes;
- family links;
- child rule sets (versioned);
- heartbeat protections.

All are built in the POC. The schema is in `backend/db/schema.sql`. There is no admin panel in the R&D.

**Offline-first (section 51):**
- Enforcement is fully local on both platforms. Signed cards verify offline with the public key. The offline emergency exit is limited to 1 per 7 days.
- Queued actions sync later.
- The trade-off: an offline card unlock accepts a copied URL, which is the same risk as online for static and signed cards.

**Time manipulation (section 52).** Three layers:
1. forced automatic time (iOS, to be tested under `.individual`);
2. monotonic clock within the same boot (iOS CLOCK_MONOTONIC + boot session; Android elapsedRealtime + BOOT_COUNT, unit-tested);
3. the server end time when online.

The known gap: reboot, plus offline, plus a changed clock, all together.

**Reinstall (section 53):**
- The commitment is restored from the server with the same end time, and only on the same platform (backend-tested).
- The card stays bound to the account, so it does not need re-linking.
- Screen Time authorization (iOS) or Accessibility and Usage access (Android) must be granted again. On iOS the apps must be re-selected.
- The OS restriction itself does disappear on deletion (Official for iOS).

**Card validation (section 54):**

| Option | iOS | Android | Cloning | Cost | Vendor dependency | Offline |
| --- | --- | --- | --- | --- | --- | --- |
| A static id + backend | Yes | Yes | Trivial | Lowest | Low | No |
| B signed payload | Yes | Yes | URL copy works | Lowest | Low | **Yes** |
| C device/account binding | Applied to all | Applied to all | Limits a stolen card to one account | None | None | Yes (cached bound card) |
| D NTAG 424 DNA SUN | Yes | Yes | Copy and replay rejected; pre-play possible | Higher | Key configuration by vendor | No |

**Recommendation:** B + C for the MVP by default, or D + C if anti-copy is a selling point. The backend supports both at the same time.
