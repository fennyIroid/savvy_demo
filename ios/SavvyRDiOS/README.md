# SavvyRDiOS: iOS R&D proof of concept

Status:
- **SavvyCore** (`ios/SavvyCore`, Swift package): card parsing, grant verification,
  time integrity, offline policy. **Compiled and 12 XCTests pass on Linux** (Swift 6.0.3),
  including verification of cards and grants issued by the Node backend.
- **App and extensions**: non-UI sources (Shared, Monitor and ShieldAction extensions,
  AppModel, unlock, backend client, NFC, authorization, live proof relay) **build and run
  end to end on Linux** in `ios/AppHarness`: 8 XCTests drive the real code against the
  real backend, with functional fakes of the Apple frameworks (our model of Apple's
  documented behaviour, not Apple code). SwiftUI / UIKit / AppIntents files are
  syntax-checked only.
- **Not yet built with Xcode or run on an iPhone** (no Mac, Apple account, device or NFC
  card in the R&D environment). Nothing here is "working" until the device tests in
  `docs/IOS_POC_RESULTS.md` are recorded.

Checks you can run on Linux or macOS:

```bash
source ios/scripts/install-swift-linux.sh      # Linux only; macOS uses Xcode's swift
ios/scripts/check-linux.sh                     # SavvyCore (12) + AppHarness end-to-end (8) + syntax
```

## Structure

| Target | Type | Purpose |
| --- | --- | --- |
| SavvyRDiOS | App | Test lab UI, authorization, picker, NFC, QR, unlock coordinator, backend client |
| SavvyMonitor | DeviceActivityMonitor extension | Ends commitments and pauses when the app is not running; clock-tamper check |
| SavvyShieldConfiguration | ShieldConfiguration extension | Savvy-branded shield text and buttons |
| SavvyShieldAction | ShieldAction extension | Shield buttons: opens Savvy (iOS 26.5+) or posts a notification (older iOS) |
| SavvyReport | DeviceActivityReport extension (ExtensionKit) | Renders today's screen time inside the app |
| Shared/ | Source folder compiled into app and extensions | App Group state and offline queue, shield engine, schedule engine, Darwin clock + keys, token refresh (iOS 26.5), log |
| ../SavvyCore | Swift package used by app and extensions | Card payload, grant verifier, commitment model, time integrity, offline policy (unit-tested) |

## What you need

1. Mac with the current Xcode (iOS 26 SDK or later), and XcodeGen (`brew install xcodegen`).
2. Apple Developer Program team. Family Controls **development** works immediately.
   **Distribution** (TestFlight / App Store) needs Apple approval for the app AND
   each of the 4 extension bundle IDs: https://developer.apple.com/contact/request/family-controls-distribution
3. For self-use tests: one iPhone XS or later (background NFC needs XS+), iOS 16+,
   ideally one on iOS 26.5+ and one on iOS 18 or earlier to compare shield handoff.
4. For parent-child tests: two iPhones, a Family Sharing group with one adult
   and one child Apple Account (child signed in on the child iPhone).
5. NFC: generic NTAG215/216 cards for the architecture test, NTAG 424 DNA cards for
   the anti-clone test, then the client's vendor card.
6. The backend in `../../backend` running on a laptop on the same Wi-Fi
   (`npm start`). Set `SavvyBackendURL` in project.yml to the laptop IP.

## Build

```bash
cd ios/SavvyRDiOS
# Edit project.yml: DEVELOPMENT_TEAM, bundle IDs, App Group, card domain.
xcodegen generate
open SavvyRDiOS.xcodeproj
```

Verification keys: "Register this install" fetches them from `GET /v1/keys` (POC).
For production, embed the raw hex keys in Info.plist (`SavvyGrantPublicKey`,
`SavvyCardPublicKey`) so extensions and offline checks never depend on the network.

For universal links (background NFC and the system Camera QR path) you need a real
HTTPS domain hosting `Web/apple-app-site-association`. During development add
`?mode=developer` to the associated domain and enable Associated Domains Development
in the iPhone's Developer settings.

## Writing test cards

Use NXP TagWriter or NFC Tools on any phone:
1. Run `node backend/scripts/provision-cards.js signed 3` (use the same signing key as the backend).
2. Write each `ndef_url` as a single **URI record** (first record on the tag).
3. Print the same URL as a QR code for the QR fallback tests.
4. Register cards on the backend: the POC creates cards with `manufactureCard` (see backend tests).

## Test IDs

Every button in the test lab maps to a test in `docs/IOS_POC_RESULTS.md`. The
lifecycle log (Diagnostics > Show lifecycle log) records app, extension, schedule,
NFC and unlock events with wall clock and monotonic time, so each test result can
be checked afterwards. Record the screen as well.
