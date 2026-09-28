# Savvy R&D Open Items

Status date: 25 September 2026.

## 1. Client decisions needed

| # | Decision | Options | Our recommendation |
| --- | --- | --- | --- |
| C1 | QR fallback policy | (a) Same as card (b) Works after a wait, for example 10 min (c) Limited per week like emergency exit (d) Only when NFC is not available | (b) or (c). A static QR is copyable |
| C2 | Card chip | NTAG215/216 signed URL (cheap, copyable) or NTAG 424 DNA SUN (anti-copy, needs internet, higher cost) | Decide after vendor samples; backend supports both |
| C3 | Does completing a to-do task need the card? | Free tap or card required | Card required when the user chose a card-protected session |
| C4 | Emergency exit rule | Number per week, cooling-off wait, pause for N minutes or full release | Start with 2 per 7 days, 10-minute wait, full release. All configurable |
| C5 | Offline card unlock | Allowed for signed cards, or always needs internet | Allowed (offline is common). Synced later |
| C6 | Task without a set time | Max duration | 24 hours cap |
| C7 | Minimum session length on iPhone | 15 min (Apple limit) or try shorter using back-dated window | 15 min unless device test T-PAUSE-2 passes |
| C8 | Which streak outcomes count | Completed, released by card, task done | As listed; emergency exit breaks the streak |
| C9 | Self-use wording | "Uninstall protection" is not accurate on either platform in self mode | Use "commitment lock" (see CLIENT_SAFE_SUMMARY) |
| C10 | Parent mode card rules | Can the child's card unlock, or only the parent's card / parent approval? | Parent decides per rule. MVP: child card unlocks only if the parent allowed it |
| C11 | What the parent sees | Savvy data only, or also Android app usage minutes | Savvy data on both. Android usage minutes only with monitoring disclosure |
| C12 | Android self mode device admin | Use as an extra uninstall step or not | Not in MVP until the Play policy is checked |
| C13 | Website blocking | Out of MVP scope. iOS can shield Safari domains; Android MVP blocks apps only | Keep out of MVP; state clearly |

## 2. Hardware and vendor input

| # | Item |
| --- | --- |
| H1 | Card vendor name and the answers to the 13 questions in NFC_FINDINGS.md section 9 |
| H2 | 10 sample cards of the planned chip, plus 5 NTAG 424 DNA samples configured with our test keys |
| H3 | Production card domain (for example go.savvy.app) with HTTPS, apple-app-site-association and assetlinks.json |

## 3. External approvals and accounts

| # | Item | Owner | Lead time |
| --- | --- | --- | --- |
| E1 | Apple Family Controls distribution entitlement for 5 bundle IDs | iRoid (client's Apple account) | Days to 4.5+ weeks. **Start now** |
| E2 | Apple Developer account access for development builds | Client / iRoid | - |
| E3 | Family Sharing test group: 1 adult + 1 child Apple Account | iRoid QA | 1 day |
| E4 | Google Play Console: Accessibility declaration, FGS declaration, videos | iRoid | After app build |
| E5 | Human review of Google Play policy pages listed in STORE_POLICY_RISKS.md | iRoid | 1 day |

## 4. Test environment needed

| # | Item |
| --- | --- |
| T1 | Mac with current Xcode, XcodeGen |
| T2 | iPhones: one on iOS 26.5+, one on iOS 18 or earlier, both XS or later. A second iPhone for the child |
| T3 | Android: Pixel, Samsung, Xiaomi/Redmi, OnePlus, Oppo or Vivo, one Android 8 to 12 device |
| T4 | Laptop running the backend on the same Wi-Fi |

## 5. Technical follow-ups

| # | Item | Why |
| --- | --- | --- |
| F1 | Build SavvyRDiOS in Xcode and fix any errors | Non-UI code already runs on Linux (ios/AppHarness, 8 tests) and SavvyCore has 12 tests; SwiftUI / UIKit / AppIntents files are syntax-checked only |
| F2 | Build the Android APK in Android Studio | App sources already compile against real Android 16 (android-all) and 9 Robolectric end-to-end tests pass; AndroidX / ZXing / resources not yet built |
| F3 | Run all device tests in IOS_POC_RESULTS.md and ANDROID_POC_RESULTS.md, record video | Evidence standard (section 7) |
| F4 | Choose the DeviceActivity component strategy (T-SCHED-1/2) and delete the other | Reported reliability issue |
| F5 | Handle iOS 26.5 TokenExpiryMessage and refresh | Tokens can expire or change |
| F6 | Collect OEM class names for Android parent-mode tamper screens | Guard list is AOSP-based |
| F7 | Done: SavvyCore Swift tests verify backend-issued cards and grants | Closed |
| F8 | NTAG 424 DNA live AES challenge-response: POC implemented and backend-tested; test with real cards | Closes the pre-play weakness |
| F9 | Production backend: PostgreSQL repository, real auth, APNs / FCM sender for the push outbox, rate limits, key management (KMS), Redis for live-proof sessions | POC uses memory store and simple tokens |
| F10 | Test Samsung Dual Messenger / Xiaomi Dual Apps and Android private space | Possible Android bypasses |
| F12 | Remove the dev-only card factory (`SAVVY_DEV=1`) from any deployed environment | Test helper only |
| F11 | Confirm the child account age cutoff for Apple Family Sharing in target countries | **Not clear** from Screen Time docs |
