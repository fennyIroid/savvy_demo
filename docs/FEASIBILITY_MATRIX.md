# Savvy Cross-Platform Feasibility Matrix

Status date: 25 September 2026. Codes A to H are defined in IOS_FEASIBILITY.md. **F** means the code is written and a device test is still needed.

## Section 63. Final cross-platform table

| Feature | iOS Self | iOS Parent/Child | Android Self | Android Parent/Child | Recommended Solution |
| --- | --- | --- | --- | --- | --- |
| App selection | A, E | A, E, F | A | A | iOS: FamilyActivityPicker (opaque tokens). Android: launcher `<queries>` list |
| App restriction | A, E, F | A, E, F | A, E, F | A, E, F | iOS: ManagedSettings shield. Android: Accessibility + UsageStats fallback |
| To-do restriction | A, F | A, F | A, F | A, F | Task starts a commitment; completion releases via backend; card optional per policy |
| Study / Work / Sleep | A | A | A | A | Presets over the same commitment engine |
| 6-hour commitment | A, F | A, F | A, F | A, F | Server end time + DeviceActivity (iOS) / monotonic check (Android) |
| 24-hour commitment | A, F | A, F | A, F | A, F | Same |
| NFC card scan | A, G | A, G | A, G | A, G | One HTTPS URL on the card (NDEF URI record) |
| NFC unlock | B, F, G (2 to 3 actions) | B, F, G | A, F, G (1 action) | A, F, G | iOS: shield button, then hold card. Android: hold card on block screen |
| QR unlock | A, C | A, C | A, C | A, C | Same URL as NFC; add delay or limit (client choice) |
| Uninstall prevention | D (exact: H) | A | D (exact: H on Play) | C | iOS parent: Apple protection. Android parent: device admin + guard. Self: restore + detect |
| Permission anti-bypass | D | A | D | C | Detect and record in self mode |
| Emergency exit | A | A | A | A | Server-limited, configurable |
| Screen-time summary | C (on-device only) | D (parent sees Savvy data) | A | A, E | iOS report extension; Android UsageStats |
| Focus time | A | A | A | A | Backend |
| Streaks | A | A | A | A | Backend |
| Parent remote configuration | N/A | A, F | N/A | A, F | Versioned rules on backend; child applies locally |
| Offline restriction | A, F | A, F | A, F | A, F | Local enforcement; signed cards verify offline |
| Reboot persistence | F (expected yes) | F | A, F | A, F | System-held shield (iOS); boot receiver + a11y (Android) |

## Section 55. Bypass test matrix

| Scenario | iOS Self | iOS Child | Android Self | Android Child |
| --- | --- | --- | --- | --- |
| Force-close Savvy | Shield stays (test) | Shield stays (test) | Services continue; force stop in App info disables a11y | Force stop disabled for admin (AOSP) |
| Restart device | Shield stays (test) | Same | a11y re-bound, FGS restarted | Same |
| Remove internet | Enforced locally | Same | Enforced locally | Same |
| Airplane mode | Enforced; no background NFC | Same | Enforced | Same |
| Change date/time | Auto time forced + monotonic check | Same | Monotonic check (unit-tested) | Same |
| Uninstall Savvy | Possible after revoke (friction) | **Blocked** (Apple) | Possible | Blocked behind admin + guard; safe mode remains |
| Reinstall Savvy | Commitment restored (backend-tested) | Not possible without parent | Commitment restored | Not possible without deactivation |
| Revoke permissions | Possible with Face ID; detect | Parent approval | Possible; detect | Guarded; safe mode / AAPM remain |
| Clear data | N/A | N/A | Possible; server restore | Disabled for admin (AOSP) |
| Disable Accessibility | N/A | N/A | Possible; UsageStats fallback | Guarded |
| Open blocked app from notification | Shield (test) | Shield (test) | Blocked (test) | Blocked (test) |
| Deep link | Shield (test) | Shield (test) | Blocked (test) | Blocked (test) |
| Search / Siri / Assistant | Shield (test) | Shield (test) | Blocked (test) | Blocked (test) |
| Recent apps | Shield (test) | Shield (test) | Blocked (test) | Blocked (test) |
| Wrong NFC card | Rejected (backend-tested) | Same | Same | Same |
| Copied QR | **Accepted** (identity only) | Same | Same | Same |

## Section 56. NFC UX matrix (user actions to unlock)

| State | iOS | Android |
| --- | --- | --- |
| Savvy foreground | 2 (tap Scan, hold card) | 1 (hold card on block screen) |
| Savvy background | 2 (tap card, tap notification) | 1 (tap card) |
| Savvy terminated | 2 (tap card, tap notification) | 1 (tap card) |
| Screen locked | 3 (tap card, unlock, tap notification) | 2 (unlock, tap card) |
| Blocked app visible | 2 on iOS 26.5+ (shield button, hold card); 3 on older iOS | 1 (hold card) |
| No internet | Signed card: works offline. SUN card: fails | Same |
| Invalid card | Rejected with message | Rejected with message |
| NFC disabled | Not applicable (no user switch for in-app reading; Airplane mode stops background reading only) | Turn on NFC or use QR |

All action counts are design expectations. They must be confirmed on device (T-UNLOCK, A-NFC tests).
