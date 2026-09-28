# Savvy iOS POC Results and Device Test Plan

Status date: 25 September 2026.

**Current state:**
- **Not yet built with Xcode or run on an iPhone.** The R&D environment had no Mac, Xcode, iPhone, Apple Developer account or NFC card.
- **What does run already (all passing):**
  - Backend: 22 tests.
  - SavvyCore Swift package: 12 tests.
  - `ios/AppHarness`: 8 end-to-end tests. They run the app's non-UI code and the Monitor and ShieldAction extensions against the real backend, with **our fakes** of Apple's frameworks.
- The fakes prove Savvy's own logic, protocol and crypto. They cannot prove how iOS itself behaves, for example whether a shield survives a reboot. Only the device tests below can do that.
- Every device "Actual" below reads **Not run yet** until a tester fills it in.

## Automated results so far

| Automated test | Covers device test | Result |
| --- | --- | --- |
| AppHarness testOnlineCardUnlockClearsShield | T-UNLOCK-1, T-UNLOCK-2 logic; T-SELF-1 guard set | **Pass (fakes)** |
| AppHarness testOfflineStartOfflineCardThenSync | T-UNLOCK-4/6 logic, offline sync | **Pass (fakes)** |
| AppHarness testLockedCommitmentRefusesCardButEmergencyExitWorks | T-UNLOCK-10, T-EMERG logic | **Pass (fakes)** |
| AppHarness testOfflineEmergencyLimitAndTaskRules | T-EMERG-2, T-TASK-1 logic | **Pass (fakes)** |
| AppHarness testMonitorReArmsOnClockTamperAndReleasesWhenReallyOver | T-TIME-1 logic (Savvy's re-arm), T-SCHED release logic | **Pass (fakes)**; mutation-checked |
| AppHarness testShieldActionHandsOffToAppAndClosesLockedApps | T-SHIELD-ACTION-1 logic (iOS 26.5 path) | **Pass (fakes)** |
| AppHarness testParentSelectionBlobAppliedOnChild | T-PC-3 logic | **Pass (fakes)** |
| AppHarness testReinstallRestoresCommitmentWithSameEndTime | T-SELF-3 logic | **Pass (fakes)** |
| SavvyCore (12) | Card parsing, backend-issued card and grant verification, time integrity, offline policy | **Pass** |
| Backend (22) | Server side of every flow, incl. NTAG 424 DNA SUN and live proof | **Pass** |

Run with `ios/scripts/check-linux.sh` (Linux) or `swift test` in `ios/SavvyCore` and `ios/AppHarness` (macOS).

**How to record a result:**
1. Build `ios/SavvyRDiOS` (see its README).
2. Start the screen recording.
3. Run the steps.
4. Export the lifecycle log (Diagnostics > Show lifecycle log).
5. Fill in Actual Result, Device, iOS version and Evidence (recording file name).

---

## POC index

| POC | Requirement | Status |
| --- | --- | --- |
| iOS-POC-1 | Authorization (.individual / .child) | Code written, device test pending |
| iOS-POC-2 | App selection | Code written, device test pending |
| iOS-POC-3 | Shield selected apps and persistence | Apply / clear logic tested (fakes); persistence needs device |
| iOS-POC-4 | Custom shield and handoff to Savvy | Handoff logic tested (fakes); shield UI needs device |
| iOS-POC-5 | Commitment scheduling (6 h, 24 h, task, duration) | Release and clock-tamper re-arm logic tested (fakes); DeviceActivity timing needs device |
| iOS-POC-6 | NFC foreground read | Code written, device and card test pending |
| iOS-POC-7 | Background NFC | Code written, needs HTTPS domain + device + card |
| iOS-POC-8 | NFC to shield removal end-to-end | Card to shield removal tested (fakes, real backend); NFC read needs device + card |
| iOS-POC-9 | Card identity and security | Backend-tested (signed, SUN, replay, clone, live proof); vendor card pending |
| iOS-POC-9b | NTAG 424 DNA live proof (T-NFC-LIVE-1..4) | Backend + simulated chip tested; iOS relay built in harness; needs real card |
| iOS-POC-10 | QR fallback | Code written; backend part tested |
| iOS-POC-11 | Self-use anti-bypass | Reinstall restore and guard logic tested (fakes); OS behaviour needs device |
| iOS-POC-12 | Self-use uninstall delay | Researched to conclusion; near-goal code written |
| iOS-POC-13/14/15 | Parent-child authorization, remote rules, anti-bypass | Rule blob + always-on applied on child (fakes); needs 2 iPhones + Family Sharing |
| iOS-POC-16 | To-do restriction | Backend-tested; device test pending |
| iOS-POC-17 | Emergency exit | Backend-tested; device test pending |
| iOS-POC-18 | Screen-time summary | Code written, device test pending |
| iOS-POC-19 | Focus time and streaks | Backend-tested |

---

## iOS-POC-1 Authorization

```text
POC: iOS-POC-1
Platform: iOS
Mode: Self-use and Parent-child
Requirement: Savvy must be allowed to use Screen Time APIs
Status: Code written, device test pending
Exact End Goal: Obtain authorization, observe system UI, observe revoke effects
Approaches Investigated: .individual (iOS 16+), .child (Family Sharing), legacy iOS 15 child-only API (rejected, too old)
Approach Implemented: AuthorizationController with status logging
Code Location: ios/SavvyRDiOS/App/Authorization/AuthorizationController.swift
Hardware Tested: None yet
OS Version: Not tested
Permissions: Face ID / Touch ID / passcode (.individual); parent Apple Account (.child)
Entitlements: com.apple.developer.family-controls (app + 4 extensions)
Actual Result: Not run yet
Limitations: Owner can revoke .individual in Settings; one .child app per device
Known Bypasses: Revoke in Settings (.individual)
Alternative Approaches: None needed
Store Policy Risk: Entitlement approval for 5 bundle IDs
Additional Test Required: T-AUTH-1..6
Final Recommendation: Use .individual for self-use, .child for parent-child
Evidence: Apple docs listed in IOS_FEASIBILITY.md R&D 1
```

| Test | Steps | Expected | Actual |
| --- | --- | --- | --- |
| T-AUTH-1 | Tap "Request .individual" | Alert, then Face ID or passcode, status approved | Not run yet |
| T-AUTH-2 | Kill and reopen Savvy | Status still approved | Not run yet |
| T-AUTH-3 | Settings > Screen Time > Apps with Screen Time Access > Savvy off | Face ID or passcode asked; shields and denyAppRemoval removed; log shows status change on next launch | Not run yet |
| T-AUTH-4 | Same as T-AUTH-3 via Settings > Apps > Savvy | Same as T-AUTH-3 | Not run yet |
| T-AUTH-5 | On child iPhone: tap "Request .child" | Parent Apple Account sheet on the child device; approved | Not run yet |
| T-AUTH-6 | On child iPhone: try to turn Savvy off in Screen Time settings | Parent approval required; record exact UI | Not run yet |

**GO:** T-AUTH-1, 2 and 5 pass. **NO-GO:** `.individual` fails on a supported device with a passcode set.

---

## iOS-POC-2 App selection

| Test | Steps | Expected | Actual |
| --- | --- | --- | --- |
| T-SEL-1 | Choose Instagram only | apps count 1, Label shows Instagram | Not run yet |
| T-SEL-2 | Choose 3 apps + Social category | Counts match | Not run yet |
| T-SEL-3 | Kill app, reopen | Selection restored from App Group | Not run yet |
| T-SEL-4 | Reboot, reopen | Selection restored; shield still applies with restored tokens | Not run yet |
| T-SEL-5 | Delete Savvy, reinstall | Selection gone (expected); must choose again | Not run yet |
| T-SEL-6 | Revoke authorization, re-authorize, apply old selection | Old tokens void (expected per Apple); record behaviour | Not run yet |
| T-SEL-7 | Update iOS minor version with an active selection | Tokens still valid, or TokenExpiryMessage seen on 26.5+ | Not run yet |

**GO:** T-SEL-1..4 pass.

---

## iOS-POC-3 Shield and persistence

| Test | Steps | Expected | Actual |
| --- | --- | --- | --- |
| T-SHIELD-1 | Select Instagram, Start (Free, 15 min), open Instagram from Home | Savvy shield | Not run yet |
| T-SHIELD-2 | Savvy in background, open Instagram | Shield | Not run yet |
| T-SHIELD-3 | Force-close Savvy (swipe up), open Instagram | Shield | Not run yet |
| T-SHIELD-4 | Lock and unlock phone, open Instagram | Shield | Not run yet |
| T-SHIELD-5 | Restart phone, unlock, open Instagram (do NOT open Savvy) | Shield | Not run yet |
| T-SHIELD-6a | Open Instagram via Spotlight search | Shield | Not run yet |
| T-SHIELD-6b | "Hey Siri, open Instagram" | Shield | Not run yet |
| T-SHIELD-6c | Tap an Instagram notification | Shield | Not run yet |
| T-SHIELD-6d | Tap an instagram.com link in Messages / Notes (universal link) | Shield | Not run yet |
| T-SHIELD-6e | App Switcher (recent apps) to Instagram opened before focus started | Shield | Not run yet |
| T-SHIELD-6f | Shortcuts "Open App" Instagram | Shield | Not run yet |
| T-SHIELD-6g | instagram.com in Safari (web domain selected) | Web shield | Not run yet |
| T-SHIELD-6h | instagram.com in Chrome / Firefox | Record: shielded or not (depends on browser reporting) | Not run yet |
| T-SHIELD-7 | Wi-Fi and mobile data off, open Instagram | Shield | Not run yet |
| T-SHIELD-8 | Airplane mode, open Instagram | Shield | Not run yet |

**GO:** T-SHIELD-1 to 5 and 7 to 8 pass. Any failure in 6a to 6f is a documented bypass that must go into the client wording.

---

## iOS-POC-4 Custom shield and handoff

| Test | Steps | Expected | Actual |
| --- | --- | --- | --- |
| T-SHIELD-UI-1 | Open shielded Instagram | Savvy title, subtitle with mode and end time, 2 buttons | Not run yet |
| T-SHIELD-ACTION-1 | iOS 26.5+: tap "Unlock with Savvy card" | Savvy opens directly, NFC sheet appears | Not run yet |
| T-SHIELD-ACTION-2 | iOS 18 or earlier: tap "Unlock with Savvy card" | Instagram closes, notification appears; tap it; Savvy opens, NFC sheet appears | Not run yet |
| T-SHIELD-ACTION-3 | Notifications disabled for Savvy, iOS < 26.5 | Record: no handoff. User must open Savvy manually | Not run yet |
| T-SHIELD-ACTION-4 | Focus mode (Do Not Disturb) on, iOS < 26.5 | Time-sensitive notification should break through; record | Not run yet |
| T-SHIELD-ACTION-5 | Tap "Emergency exit" on shield | Savvy opens (26.5+) or notification (older) and emergency dialog shows | Not run yet |

**GO:** T-SHIELD-ACTION-1 on iOS 26.5+, T-SHIELD-ACTION-2 on older iOS.

---

## iOS-POC-5 Commitment scheduling

**Test design.** Use the lifecycle log to see when `intervalDidEnd` ran. Run each test once with each `schedule.strategy`: `timeOfDay` (the default) and `fullDate`. Switch strategy by setting App Group key `schedule.strategy` from the debugger or a debug button.

| Test | Steps | Expected | Actual |
| --- | --- | --- | --- |
| T-SCHED-1 | Start 15 min Free focus; kill Savvy; wait 20 min; unlock phone | Shield gone; log shows Monitor intervalDidEnd | Not run yet |
| T-SCHED-2 | Same with fullDate strategy | Same | Not run yet |
| T-SCHED-3 | Start 6 h card_required; restart phone at 1 h; check at 6 h 5 min | Shield on until 6 h, gone after | Not run yet |
| T-SCHED-4 | Start 24 h; leave phone idle overnight | Shield gone at first use after 24 h | Not run yet |
| T-SCHED-5 | Start 6 h; keep Savvy never opened for 6 h | intervalDidEnd still runs | Not run yet |
| T-PAUSE-1 | Emergency exit in pause mode (15 min) | Shield lifts, returns after 15 min | Not run yet |
| T-PAUSE-2 | Pause of 5 min using back-dated window | Shield returns after 5 min | Not run yet |
| T-TIME-1 | Start 6 h; turn off automatic time (if allowed); move clock +7 h | Record whether iOS blocks the change (T-TIME-2); if not, shield stays (Monitor re-arms) | Not run yet |
| T-TIME-2 | With commitment guard on, open Settings > General > Date & Time | "Set Automatically" locked on (requireAutomaticDateAndTime) | Not run yet |
| T-TIME-3 | Start 6 h, reboot, move clock +7 h, open Instagram | Record; monotonic check cannot help after reboot; app reconciles with server when online | Not run yet |

**GO:** T-SCHED-1 or 2 pass (choose the passing strategy), T-SCHED-3 passes, and T-TIME-2 passes (or T-TIME-1 passes).

---

## iOS-POC-6 NFC foreground read

| Test | Steps | Expected | Actual |
| --- | --- | --- | --- |
| T-NFC-1 | Tag session with NTAG215 signed card | URL + UID logged, read time < 2 s | Not run yet |
| T-NFC-2 | NDEF session with same card | URL logged | Not run yet |
| T-NFC-3 | NTAG 424 DNA SUN card, tag session | iso7816 family, URL with e and c, new values each tap | Not run yet |
| T-NFC-4 | Random blank or non-NDEF tag | "This is not a Savvy card" | Not run yet |
| T-NFC-5 | MIFARE Classic card | Not detected or rejected (not supported on iPhone) | Not run yet |
| T-NFC-6 | Client vendor card | Record chip family, UID, URL, read time | Not run yet (needs card) |
| T-NFC-7 | Measure 10 scans | Record success rate and average time | Not run yet |
| T-NFC-LIVE-1 | NTAG 424 DNA card with key 3 set by vendor, "Scan card with live proof" | Presence token, release with proof_of_presence | Not run yet |
| T-NFC-LIVE-2 | Same card, backend policy requireLiveProofForSunCards on, plain tap | Rejected live_proof_required | Not run yet |
| T-NFC-LIVE-3 | Measure total time card must be held (2 round trips) | Under 3 s on Wi-Fi and 4G | Not run yet |
| T-NFC-LIVE-4 | Remove card mid-relay | Clear failure, no unlock, retry works | Not run yet |

---

## iOS-POC-7 Background NFC

**Prerequisites:** a real HTTPS card domain with the AASA file. For development, the entitlement uses `applinks:<domain>?mode=developer`. The card's first record must be the URI.

| Test | State | Steps | Expected | Actual | User actions |
| --- | --- | --- | --- | --- | --- |
| T-NFC-BG-1 | Savvy foreground, not scanning | Tap card to phone | Notification or direct delivery; record | Not run yet | |
| T-NFC-BG-2 | Savvy background | Tap card | Notification; tap opens Savvy with URL; unlock | Not run yet | |
| T-NFC-BG-3 | Savvy terminated | Tap card | Same as BG-2, app launches | Not run yet | |
| T-NFC-BG-4 | Phone locked | Tap card | Notification; unlock needed; then Savvy | Not run yet | |
| T-NFC-BG-5 | Shield visible | Tap card | Notification expected (not documented) | Not run yet | |
| T-NFC-BG-6 | Another app active | Tap card | Notification | Not run yet | |
| T-NFC-BG-7 | Airplane mode | Tap card; then try in-app scan | Background: nothing (Apple doc). In-app scan: record | Not run yet | |
| T-NFC-BG-8 | Camera open | Tap card | Nothing (Apple doc) | Not run yet | |
| T-NFC-BG-9 | Wallet open | Tap card | Nothing (Apple doc) | Not run yet | |
| T-NFC-BG-10 | Savvy not installed | Tap card | Safari opens card web page | Not run yet | |

---

## iOS-POC-8 NFC to shield removal (end-to-end)

**Setup:**
- Backend running.
- Card A registered to the tester's account.
- Card B registered to another account.
- A blank tag.
- An active 6 h card_required commitment with Instagram shielded.

| Test | Steps | Expected | Actual | Actions / time |
| --- | --- | --- | --- | --- |
| T-UNLOCK-1 | Open Instagram, tap shield button, hold card A | Savvy opens, scan, released, Instagram opens normally | Not run yet | |
| T-UNLOCK-2 | Same with card B | Rejected card_not_owned_by_user; shield stays | Not run yet | |
| T-UNLOCK-3 | Same with blank or random tag | Rejected not_a_savvy_card | Not run yet | |
| T-UNLOCK-4 | Backend stopped, signed card A | Offline path releases (if enabled) and queues sync | Not run yet | |
| T-UNLOCK-5 | Backend stopped, SUN card | Rejected sun_card_needs_internet | Not run yet | |
| T-UNLOCK-6 | Internet off, signed card A | Same as T-UNLOCK-4 | Not run yet | |
| T-UNLOCK-7 | Scan card A twice quickly | Second scan: no_active_commitment, no crash | Not run yet | |
| T-UNLOCK-8 | Card revoked on backend | Rejected card_revoked | Not run yet | |
| T-UNLOCK-9 | Savvy killed, tap card (background NFC), tap notification | Released | Not run yet | |
| T-UNLOCK-10 | Locked commitment, card A | Rejected locked_commitment_no_early_unlock | Not run yet | |

**Record for each test:** the number of user actions, and the seconds from the shield tap to Instagram being usable. The log prints `total=`.

**GO:** T-UNLOCK-1, 2, 3, 9 and 10 pass, with an average under 10 seconds and 3 or fewer actions.

---

## iOS-POC-9 Card identity (backend-tested)

| Test | Result | Evidence |
| --- | --- | --- |
| AES-CMAC RFC 4493 vectors | **Pass** | backend/test/crypto.test.js |
| NTAG 424 DNA SUN, NXP AN12196 example (UID 04DE5F1EACC040, counter 61, MAC 94EED9EE65337086) | **Pass** | same |
| Tampered SUN MAC rejected | **Pass** | same |
| Signed card forgery rejected | **Pass** | same |
| Fresh SUN tap accepted, replay rejected, cloned UID without key rejected | **Pass** | backend/test/api.test.js |
| One account per card, one card per account | **Pass** | same |
| Client vendor card | Not run yet (needs card) | |

---

## iOS-POC-10 QR fallback

| Test | Steps | Expected | Actual |
| --- | --- | --- | --- |
| T-QR-1 | Scan printed card QR with in-app scanner | Released | Not run yet |
| T-QR-2 | Scan a random QR | not_a_savvy_card | Not run yet |
| T-QR-3 | Scan another user's card QR | card_not_owned_by_user | Not run yet |
| T-QR-4 | Scan a photo of the QR shown on another phone | **Released** (documented limitation) | Not run yet |
| T-QR-5 | Scan with system Camera app | Banner opens Savvy via universal link; released | Not run yet |
| T-QR-6 | Offline, signed card QR | Offline path | Not run yet |
| T-QR-7 | Camera restricted by Screen Time content settings | Record; QR path unavailable | Not run yet |

Backend-tested: a copied QR is accepted with `proof_of_presence=false`.

---

## iOS-POC-11 and 12 Self-use anti-bypass and uninstall delay

See `IOS_BYPASS_MATRIX.md` for the full matrix. Tests:

| Test | Steps | Expected | Actual |
| --- | --- | --- | --- |
| T-SELF-1 | Commitment on, long-press Savvy > Remove App | Blocked (denyAppRemoval) | Not run yet |
| T-SELF-2 | Commitment on, revoke Screen Time access in Settings | Face ID asked; shields drop; Savvy deletable | Not run yet |
| T-SELF-3 | After T-SELF-2, delete and reinstall Savvy, log in | Server returns active commitment, same end time; app asks to re-authorize and re-select | Not run yet (backend part tested) |
| T-SELF-4 | Try to delete any other app during commitment | Blocked (denyAppRemoval is device-wide); note UX impact | Not run yet |
| T-SELF-5 | Log out of Savvy (clear account) during commitment | Shield stays (local); log in again restores | Not run yet |
| T-SELF-6 | Offload Savvy (Settings > General > iPhone Storage > Offload App) | Record: is offload blocked by denyAppRemoval? Does the shield stay? | Not run yet |

---

## iOS-POC-13 to 15 Parent-child

**Needs:**
- 2 iPhones;
- a Family Sharing group with one adult and one child account;
- Savvy on both devices;
- the backend.

| Test | Steps | Expected | Actual |
| --- | --- | --- | --- |
| T-PC-1 | Child iPhone: Request .child, parent approves | approved | Not run yet |
| T-PC-2 | Parent iPhone: open picker | Shows apps from child device | Not run yet |
| T-PC-3 | Parent picks Instagram, sends rule via backend; child syncs | Instagram shielded on child | Not run yet |
| T-PC-4 | Child tries to delete Savvy | Blocked, parent approval needed | Not run yet |
| T-PC-5 | Child tries to turn off Savvy in Screen Time settings | Parent approval needed | Not run yet |
| T-PC-6 | Child tries to sign out of iCloud | Blocked | Not run yet |
| T-PC-7 | Child offline; parent changes rule | Parent sees latest_rules_not_applied; child applies when online | Not run yet (backend part tested) |
| T-PC-8 | Child reboots | Shield stays | Not run yet |
| T-PC-9 | Child changes time | Record (requireAutomaticDateAndTime) | Not run yet |
| T-PC-10 | Child taps parent's card / own card | Record agreed product rule | Not run yet |

---

## iOS-POC-16 to 19

| Test | Steps | Expected | Actual |
| --- | --- | --- | --- |
| T-TASK-1 | Create task, Start, open Instagram, mark Done (Free policy) | Shield then released | Not run yet (backend tested) |
| T-TASK-2 | 30-minute task, wait | Released at 30 min by Monitor | Not run yet |
| T-TASK-3 | Card-required task, Done without card | Refused | Not run yet (backend tested) |
| T-EMERG-1 | Emergency exit twice, third time | Third refused (limit) | Not run yet (backend tested) |
| T-EMERG-2 | Offline emergency exit | Allowed once, queued | Not run yet |
| T-REPORT-1 | Open test lab report section | Today's total, top apps, pickups | Not run yet |
| T-REPORT-2 | Try to read report values from app code | Not possible (by design) | Confirmed by Apple docs |
| T-INSIGHT-1 | Complete 3 daily sessions | Streak 3 | Backend tested: **Pass** |
