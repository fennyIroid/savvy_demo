# Savvy iOS Bypass Matrix

Status date: 25 September 2026.

**What "Expected" means here:**
- It comes from Apple documentation or Apple engineer statements where marked **(Official)**.
- Otherwise it comes from developer reports or engineering inference.
- "Actual" is filled in from the device tests in `IOS_POC_RESULTS.md`.

**What Savvy can do about a bypass:**

| Term | Meaning |
| --- | --- |
| Prevent | The bypass does not work |
| Friction | The bypass works, but needs extra deliberate steps |
| Detect | Savvy notices afterwards and records it (streak, parent flag) |
| None | Savvy cannot do anything |

## Self-use (.individual authorization)

| Scenario | Expected result | Savvy response | Test | Actual |
| --- | --- | --- | --- | --- |
| Force-close Savvy | Shield stays (system-held) | Prevent | T-SHIELD-3 | Not run yet |
| Restart device | Shield stays | Prevent | T-SHIELD-5 | Not run yet |
| Remove internet | Shield stays; card unlock works offline for signed cards only | Prevent | T-SHIELD-7 | Not run yet |
| Airplane mode | Shield stays; background NFC off (Official); in-app scan and QR still possible | Prevent | T-SHIELD-8, T-NFC-BG-7 | Not run yet |
| Change date/time | `requireAutomaticDateAndTime` during commitment (if honoured under .individual); else Monitor re-arms using monotonic clock in same boot; server time wins when online | Prevent (same boot), Detect (after reboot) | T-TIME-1..3 | Not run yet |
| Delete Savvy | Blocked while `denyAppRemoval` is set, until access is revoked (Official: not guaranteed under .individual) | Friction | T-SELF-1 | Not run yet |
| Revoke Screen Time access in Settings | Allowed with owner's Face ID or passcode; all restrictions removed (Official) | Detect only | T-SELF-2 | Not run yet |
| Revoke then delete | Allowed | Detect (server sees heartbeat stop, commitment still active) | T-SELF-2 | Not run yet |
| Reinstall Savvy | Server restores commitment with same end time; user must re-authorize and re-select apps | Friction + Detect | T-SELF-3 | Backend part **Pass** |
| Log out of Savvy | Shield is local and stays; nothing to release | Prevent | T-SELF-5 | Not run yet |
| Offload Savvy | Unknown: record | To test | T-SELF-6 | Not run yet |
| Open blocked app from notification | Shield expected | Prevent | T-SHIELD-6c | Not run yet |
| Deep link / universal link | Shield expected | Prevent | T-SHIELD-6d | Not run yet |
| Siri / Spotlight | Shield expected | Prevent | T-SHIELD-6a, 6b | Not run yet |
| Recent apps | Shield expected | Prevent | T-SHIELD-6e | Not run yet |
| Web version in Safari | Shielded if domain selected | Prevent | T-SHIELD-6g | Not run yet |
| Web version in other browser | Depends on browser reporting usage (Official) | Partial | T-SHIELD-6h | Not run yet |
| Wrong NFC card | Rejected | Prevent | T-UNLOCK-2 | Backend **Pass** |
| Random NFC tag | Rejected | Prevent | T-UNLOCK-3 | Backend **Pass** |
| Copied QR (screenshot / photo) | **Accepted** (identity, not possession) | None for static QR | T-QR-4 | Backend **Pass** (accepted, as designed) |
| Copied NFC URL (static / signed card) | **Accepted** | None | - | Backend **Pass** (accepted) |
| Cloned NTAG 424 DNA (same UID, no key) | Rejected | Prevent | - | Backend **Pass** |
| Replayed NTAG 424 DNA URL | Rejected | Prevent | - | Backend **Pass** |
| Pre-harvested NTAG 424 DNA taps | Accepted in order | None without live challenge | - | Documented (NFC_FINDINGS) |
| Shortcuts intent run by hand | Only starts focus or opens scan; never unlocks | Prevent | T-INTENT-1 | Not run yet |
| Mark task done without doing it | Allowed in Free policy; card required in card_required policy | Policy | T-TASK-3 | Backend **Pass** |
| Repeated emergency exit | Limited per window, counted server-side, survives reinstall | Prevent beyond limit | T-EMERG-1 | Backend **Pass** |

## Parent-child (.child authorization, Family Sharing)

| Scenario | Expected result | Savvy response | Test | Actual |
| --- | --- | --- | --- | --- |
| Force-close / restart | Shield stays | Prevent | T-PC-8 | Not run yet |
| Remove internet | Last rule enforced; new rules wait | Prevent + Detect (parent sees not reporting) | T-PC-7 | Backend part **Pass** |
| Change date/time | `requireAutomaticDateAndTime`; parent can also lock with Apple Screen Time passcode | Prevent | T-PC-9 | Not run yet |
| Delete Savvy | Only parent can delete (Official) | Prevent | T-PC-4 | Not run yet |
| Revoke authorization | Parent approval needed (Official) | Prevent | T-PC-5 | Not run yet |
| Sign out of iCloud | Blocked (Official) | Prevent | T-PC-6 | Not run yet |
| Reinstall | Not possible without parent deleting first | Prevent | - | - |
| Change Savvy settings | Child role in app; rules come from backend | Prevent (app logic) | - | Backend part **Pass** |
| Notification / deep link / Siri | Same as self-use | Prevent | T-SHIELD-6 on child | Not run yet |
| Wrong card / copied QR | Same as self-use | Same | - | Backend **Pass** |

## Summary

| Behaviour | Self-Use | Parent-Child |
| --- | --- | --- |
| Delete Savvy | Friction only | Prevented (Official) |
| Revoke Screen Time authorization | Allowed; detect only | Parent approval (Official) |
| Restart bypass | Expected prevented | Expected prevented |
| Offline enforcement | Yes (local) | Yes (local) |
| Permission removal | Allowed; detect only | Parent approval |
