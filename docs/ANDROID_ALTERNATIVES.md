# Savvy Android Alternatives

This file uses the format from section 47 of the brief.

---

## 1. Self-use uninstall prevention for 6 or 24 hours

### Requirement
The user cannot uninstall Savvy during a commitment.

### Primary Approach
An AccessibilityService that blocks the uninstall and deactivation screens.

### Result
It works technically, based on the unit-tested policy with AOSP screen names. **Google Play prohibits it in self-use.** Accessibility must not "prevent the ability for users to disable or uninstall any app or service unless authorized by a parent or guardian through a parental control app".

### Why It Failed
It is a Play policy restriction. On top of that, safe mode and force stop bypass it in any case.

### Alternative 1: Server-side commitment with restore and detection (implemented, recommended)
This is the same as on iOS:
- a reinstall restores the same end time;
- a disabled service is detected and recorded;
- the streak is broken.

### Alternative 2: Device admin as a system "extra step" in self mode
- Uninstall must pass through deactivation. The user can always deactivate, so this is friction, not prevention.
- Play policy fit for self mode is not clear. It needs a human policy review (OPEN_ITEMS).

### Alternative 3: Device Owner
Real prevention. It needs a factory reset or ADB provisioning, and new custom DPC registrations are closed. It is technically possible under special device management, but **unsuitable for normal Savvy consumer deployment**.

### End-Goal Comparison
- Alternative 1 preserves most of the business goal: impulsive removal gains nothing, because the commitment returns.
- It does not stop a determined user.

### UX Difference
After a reinstall, the user must grant permissions again, and the commitment resumes.

### Security Difference
Uninstall remains possible.

### Store Risk
- Alternative 1: low.
- Alternative 2: medium.
- Alternative 3: not applicable for Play.

### Recommendation
Alternative 1. Consider Alternative 2 only after a policy review.

---

## 2. Stop the user disabling permissions (self-use)

### Requirement
The user cannot turn off Savvy's Accessibility or Usage access.

### Primary Approach
Guard the Settings screens with Accessibility.

### Result
Prohibited on Play in self-use. The implementation limits the guard to PARENT mode (unit-tested).

### Alternative 1: Two independent detection paths (implemented)
Accessibility plus UsageStats. The user must turn off both to stop blocking, and each change is detected.

### Alternative 2: Tamper state in heartbeat (implemented)
Disabled services show on the backend.

### Recommendation
Alternatives 1 and 2.

---

## 3. Parent-mode protection against safe mode, factory reset and Android 17 AAPM

### Requirement
The child cannot remove Savvy in any way.

### Primary Approach
Device admin plus the Accessibility guard.

### Result
Strong against normal Settings paths. Safe mode, factory reset and AAPM remain.

### Why It Failed
Only Device Owner can set `DISALLOW_SAFE_BOOT` and `DISALLOW_FACTORY_RESET`.

### Alternative 1: Detect and alert the parent in Savvy (implemented; backend-tested flags)
The parent sees:
- device not reporting;
- admin disabled;
- accessibility off;
- AAPM on;
- USB debugging on.

(WhatsApp and email alerts are out of MVP scope. The alert is in-app only.)

### Alternative 2: Google Family Link together with Savvy
The parent can also use Google's own Family Link, which can block safe mode on supervised devices. Savvy cannot control Family Link, because there is no third-party API. **Not clear**: whether Family Link's own restrictions can block safe mode on all devices. To be checked.

### Alternative 3: Device Owner provisioning for families who want maximum control
It needs a factory reset. This is not a Play consumer path.

### Recommendation
Use Alternative 1, and mention Alternative 2 as optional parent guidance.

---

## 4. Website version of blocked apps

### Requirement
Blocking Instagram should also stop instagram.com.

### Result
App blocking does not cover browsers. With Accessibility, Savvy could read browser URLs, but that needs `canRetrieveWindowContent`, which widens the Play disclosure and privacy impact. Also, non-tool services cannot see views marked data-sensitive.

### Alternative 1: Block browsers during strict commitments (user choice)
Only if the user selects them.

### Alternative 2: Out of MVP scope
Website blocking is not in the confirmed MVP list. Treat it as Phase 2.

### Recommendation
State clearly that Android MVP blocks apps, not websites.

(Note: iOS can shield Safari domains through the Screen Time API.)

---

## 5. QR copy risk

This is the same as iOS. See `IOS_ALTERNATIVES.md` item 4 and `QR_FINDINGS.md`.

On Android, "QR only when the phone has no NFC or NFC is off" is more relevant, because some Android phones have no NFC.
