# Savvy iOS Alternatives

This file follows the format in section 47 of the brief. It covers every iOS requirement that does not work exactly as requested.

---

## 1. Self-use uninstall prevention for 6 or 24 hours

### Requirement
Once a commitment starts, the user cannot delete Savvy until the commitment ends.

### Primary Approach
FamilyControls `.individual` authorization plus ManagedSettings `application.denyAppRemoval = true` during the commitment.

### Result
- Deleting apps is blocked while the setting is active.
- The user can turn off Savvy's Screen Time access in Settings using their own Face ID or passcode. That removes all Savvy restrictions, including the deletion block, and Savvy can then be deleted.

### Why It Failed
It is Apple's platform design.
- Apple Frameworks Engineer: "`application.denyAppRemoval` isn't guaranteed to prevent your app from being deleted with `.individual` authorization, since `.individual` authorizations can be revoked at any time via Settings. If you'd like for your app to not be deletable on a child device, consider using a `.child` enrollment." (forum 729717)
- Apple docs: under `.individual`, "the system removes any restrictions that prevent the user from bypassing parental controls so the user can delete an authorized app or sign out of iCloud as needed."

### Alternative 1: Commitment lock with restore (implemented, recommended)
- `denyAppRemoval` plus `requireAutomaticDateAndTime` during the commitment.
- The server keeps the commitment. A reinstall restores the same end time (backend-tested).
- Revoke and delete events are detected and recorded, and the streak is broken.
- To end early, the user needs the card, the limited emergency exit, or the deliberate Settings revoke with Face ID.

### Alternative 2: Accountability family setup
The user joins a Family Sharing group as a child account, managed by a trusted adult. The Savvy `.child` protections then apply. This changes the product and is not practical for adults, because child accounts are age-limited by Apple.

### Alternative 3: MDM or supervised device
- `allowAppRemoval=false` needs supervision.
- A managed app with `Removable=false` needs MDM installation, and the user can remove the profile unless the device is supervised through Apple Business Manager.
- Guideline 5.5 restricts MDM.

This is technically possible, but commercially unsuitable for a consumer app.

### End-Goal Comparison
The business goal is to stop impulsive removal.
- Alternative 1 blocks the impulsive path: the user holds the home screen icon and nothing happens.
- It turns removal into a multi-step deliberate act (Settings, find Savvy, Face ID, then delete).
- It makes reinstalling pointless.

About 70 to 80 percent of the behavioural goal is preserved. This is an estimate, to be checked with user testing.

### UX Difference
- While a commitment runs, the user cannot delete any app, not only Savvy. This must be explained at commitment start.
- After a reinstall, the user must re-authorize and re-select apps before the shield returns.

### Security Difference
A determined adult can always end the commitment in about 30 seconds in Settings.

### Store Risk
Low. It uses public APIs for their documented purpose.

### Recommendation
- Use Alternative 1.
- Client wording: "During a commitment, Savvy locks app deletion and keeps your commitment even if Savvy is reinstalled. On iPhone, the phone owner can still turn off Savvy in Settings using Face ID. Savvy records this."
- Do not use the words "uninstall protection" for self-use on iPhone.

---

## 2. Prevent permission revocation (self-use)

### Requirement
The user cannot remove Savvy's Screen Time permission during a commitment.

### Primary Approach
Look for any API that locks the Screen Time access switch for `.individual`.

### Result
None exists. There is no API to shield Settings or to lock the Screen Time pane.

### Why It Failed
The owner's control over their own device is intentional in Apple's design (Official).

### Alternative 1: Detect and record (implemented)
The heartbeat reports the authorization status, and the next launch detects the revoke. The commitment stays active on the server.

### Alternative 2: Explain before the commitment starts
Show an honest confirmation screen before a strong commitment starts.

### Alternative 3: Parent-child mode
Revoking needs parent approval (Official).

### End-Goal Comparison
The business goal is only partly preserved in self-use. It is fully preserved in parent-child mode.

### Recommendation
Detect and record in self-use. Offer the parent-child mode for children.

---

## 3. One-tap NFC unlock

### Requirement
The user taps the Savvy card and Instagram is unlocked.

### Primary Approach
Background NFC tag reading.

### Result
- iOS shows a notification, and the user must tap it before Savvy receives the card URL (Official).
- Core NFC is not available in extensions, so the shield cannot read the card itself (Apple DTS).

### Why It Failed
The iOS security and privacy design requires the user to confirm before tag data reaches an app.

### Alternative 1: Shield button opens Savvy, which auto-starts the scan (implemented, recommended)
| iOS version | Flow | User actions |
| --- | --- | --- |
| iOS 26.5+ | Tap the shield button (`openParentalControlsApp`), hold the card | 2 |
| Older iOS | Tap the shield button, tap the notification, hold the card | 3 |

### Alternative 2: Background NFC
Tap the card, then tap the notification, which opens Savvy and validates. That is 2 actions. It needs iPhone XS or later and a universal link domain, and it does not work in Airplane mode, with the camera open, or with Wallet open.

### Alternative 3: Shortcuts NFC automation
- The user sets up "When I tap this tag, run Scan Savvy Card".
- The automation cannot pass tag data, and it can be triggered by any tag with a cloned UID. So it only opens Savvy's scan. It is never a direct unlock.
- It needs user setup and cannot be installed by Savvy.

### End-Goal Comparison
The real goal is that the user must physically interact with the card before continuing. That goal is fully preserved.

### UX Difference
1 or 2 extra taps compared with the client's idea.

### Security Difference
None. Every path still requires the card data (or the QR).

### Store Risk
Low.

### Recommendation
- Use Alternative 1 as the main flow, with Alternative 2 as a bonus path.
- Client wording: "On iPhone, unlocking takes a tap on the Savvy screen and a tap of your card. iOS requires this confirmation step."

---

## 4. QR fallback proving physical possession

### Requirement
The QR works as a fallback key, like the card.

### Primary Approach
A static QR printed on the card, carrying the same URL as the NFC chip.

### Result
- It works as identity: the account is bound to one card, and wrong or other users' codes are rejected (backend-tested).
- A photo or screenshot of the QR also works (backend-tested).

### Why It Failed
A static image can be copied. No app can tell a printed QR from a photo of it reliably.

### Alternative 1: Delayed QR
QR unlock works, but only after a waiting period (for example 10 minutes), using the same server-side cooling-off logic as the emergency exit. It keeps the fallback useful and removes the "instant shortcut" temptation.

### Alternative 2: QR counts as an emergency exit
QR unlocks are limited and counted, like emergency exits.

### Alternative 3: Hidden QR
The QR is on the card under a scratch-off or tamper sticker. This is manufacturing-dependent.

### Alternative 4: No QR on NFC-capable phones
The QR is allowed only when the device has no NFC. On iPhone, every model since iPhone 7 has NFC, so this is mainly relevant on Android.

### End-Goal Comparison
- A static QR alone gives identity only.
- Alternatives 1 or 2 restore most of the friction.

### Recommendation
Implement the QR as an identity check plus Alternative 1 or 2. The client must choose (see OPEN_ITEMS).

---

## 5. Screen-time summary stored and shown to the parent

### Requirement
A basic screen-time summary, and a parent viewing the child's summary.

### Primary Approach
DeviceActivityReport.

### Result
- It shows usage on the device where the usage happened.
- It cannot store or upload the numbers.
- Parent remote viewing is not available to third-party apps (Apple DTS, June 2026).

### Why It Failed
It is Apple's privacy boundary. The report extension is network-sandboxed.

### Alternative 1: Savvy data instead of Screen Time data
The parent sees Savvy data:
- focus sessions and focus time;
- commitments completed or broken;
- emergency exits;
- authorization and rule status.

### Alternative 2: Child-side report
The child's Savvy shows its own Screen Time report on the child device.

### Alternative 3: EU data access
Only in the EU, on iOS 26.4 and later, with an extra entitlement.

### End-Goal Comparison
- For self-use: fully preserved on the device.
- For parents: they get behaviour data (focus and bypass attempts) but not per-app minutes.

### Recommendation
- Scope "screen-time summary" as on-device only on iPhone.
- The parent dashboard shows Savvy focus data.

---

## 6. Commitment timing without trusting the device clock

### Requirement
Changing the time does not end a commitment early.

### Primary Approach
DeviceActivity schedule, which is calendar based.

### Result
It may be moved by clock changes (Community). This is not documented.

### Alternative 1: requireAutomaticDateAndTime during the commitment (implemented)
It stops the user changing the clock. Whether it is honoured under `.individual` must be tested (T-TIME-2).

### Alternative 2: Monotonic clock re-check (implemented)
The Monitor extension compares elapsed time against `CLOCK_MONOTONIC` in the same boot and re-arms if the clock was moved.

### Alternative 3: Server time (backend-tested)
The commitment end is stored on the server, and the app reconciles on every launch.

### Recommendation
Use all three layers. The remaining gap is a reboot plus a clock change plus offline, all together. In that case the release waits for the server if Alternative 1 fails.

---

## 7. Commitments or task timers shorter than 15 minutes

### Requirement
Short tasks, for example 10 minutes.

### Result
The DeviceActivity minimum interval is 15 minutes (Official).

### Alternative 1: Back-dated 15-minute window
A 15-minute window is started in the past, so it ends at the requested time. Apple documents that callbacks fire immediately for an interval already underway. This needs a device test (T-PAUSE-2).

### Alternative 2: Minimum task length of 15 minutes
Make 15 minutes the product minimum.

### Recommendation
Set a 15-minute product minimum unless T-PAUSE-2 proves Alternative 1.
