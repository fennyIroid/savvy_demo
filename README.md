# Savvy R&D

Technical feasibility research and proof-of-concept code for **Savvy**. Savvy is an NFC and QR based focus, screen-time restriction and parental-control app for iOS and Android.

Start with **[docs/RD_MASTER_STATUS.md](docs/RD_MASTER_STATUS.md)**. For the client-facing version, see **[docs/CLIENT_SAFE_SUMMARY.md](docs/CLIENT_SAFE_SUMMARY.md)**.

## Repository layout

| Path | What | State |
| --- | --- | --- |
| `backend/` | Node.js + Express POC: card validation (signed, NTAG 424 DNA SUN and live proof), commitments, emergency exit, offline sync, parent-child rules and usage, streaks. PostgreSQL schema | **22 tests pass** (`npm test`) |
| `ios/SavvyCore/` | Swift package: card parsing, grant verification, time integrity, offline policy | **12 tests pass** (`swift test`, Linux or macOS) |
| `ios/SavvyRDiOS/` | iOS POC: app + DeviceActivityMonitor, ShieldConfiguration, ShieldAction and DeviceActivityReport extensions (XcodeGen) | Needs Xcode build and device tests |
| `ios/AppHarness/` | Runs the iOS app's non-UI code and extensions end to end against the backend, with fakes of Apple frameworks | **8 tests pass** (`swift test`) |
| `android/SavvyRDAndroid/core` | Pure Kotlin decision logic (block policy, time integrity, card and grant verification, offline policy, usage aggregation) | **21 tests pass** (`gradle :core:test`) |
| `android/SavvyRDAndroid/app` | Android POC: Accessibility + UsageStats blocking, block screen with NFC and live proof, parent remote rules, usage, device admin parent mode | Compiles against real Android 16; **9 Robolectric end-to-end tests pass** (`gradle :compilecheck:test`); needs APK build and device tests |
| `docs/` | Feasibility reports, test plans, bypass matrices, alternatives, store risks, open items | Current as of 25 September 2026 |

## Documents

| Document | Purpose |
| --- | --- |
| RD_MASTER_STATUS.md | Overall status, POC register, executive results, answers to the 33 questions |
| IOS_FEASIBILITY.md / ANDROID_FEASIBILITY.md | Per-requirement research, sources and classification |
| IOS_POC_RESULTS.md / ANDROID_POC_RESULTS.md | Device test plans with GO / NO-GO criteria and results |
| IOS_BYPASS_MATRIX.md / ANDROID_BYPASS_MATRIX.md | What each bypass does and how Savvy responds |
| IOS_ALTERNATIVES.md / ANDROID_ALTERNATIVES.md | Near-goal alternatives for items that cannot be done exactly |
| NFC_FINDINGS.md / QR_FINDINGS.md | Card identity, chip choice, cloning, vendor questions |
| STORE_POLICY_RISKS.md | App Store and Google Play requirements and risks |
| FEASIBILITY_MATRIX.md | Cross-platform tables (sections 55, 56, 63 of the brief) |
| CLIENT_SAFE_SUMMARY.md | Client-ready scope recommendation |
| OPEN_ITEMS.md | Decisions, hardware, approvals and follow-ups |

## Run the proven parts (72 automated tests)

```bash
cd backend && npm install && npm test                            # 22
source ios/scripts/install-swift-linux.sh && ios/scripts/check-linux.sh   # 12 + 8 (Linux)
cd android/SavvyRDAndroid && gradle :core:test :compilecheck:test         # 21 + 9 (no Android SDK needed)
```

What these prove and what they do not: see `docs/RD_MASTER_STATUS.md` section 1 (evidence levels). Nothing has been run on a real phone yet.
