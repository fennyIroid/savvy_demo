# Savvy Android Bypass Matrix

Status date: 28 September 2026. "Actual" now includes device run 1 (Pixel 4, Android 13; details in ANDROID_POC_RESULTS.md). Response terms are the same as IOS_BYPASS_MATRIX.md: Prevent, Friction, Detect, None.

"Self" means normal Play distribution in self-use mode. "Parent" means parent mode with device admin plus the Accessibility guard.

| Scenario | Self: expected | Self: response | Parent: expected | Parent: response | Test | Actual |
| --- | --- | --- | --- | --- | --- | --- |
| Force-close from recents | Services continue (a11y bound by system; FGS) | Prevent | Same | Prevent | A-OEM-4 | Not run yet |
| Force stop in App info | Allowed; a11y permanently disabled (AOSP), FGS killed | Detect on next open | **Button disabled for active admin** (AOSP; OEM may differ) and App info guarded | Prevent | A-SELF-1, A-PARENT-1 | **Pixel 4:** Self: full bypass, a11y removed from enabled list until re-enabled; reopening Savvy now restarts the FGS (fix). Parent: button disabled for admin |
| Restart device | a11y re-bound; FGS restarted by BOOT_COMPLETED | Prevent | Same | Prevent | A-SELF-2 | **Pixel 4 Pass** (restarts after first unlock) |
| Remove internet / airplane | Local enforcement continues | Prevent | Same | Prevent | A-SELF-3 | **Pixel 4 Pass** |
| Change date/time | Same boot: elapsedRealtime; after reboot: server time when online | Prevent / Detect | Same | Prevent / Detect | A-SELF-4 | **Pixel 4 Pass** (+7 h, same boot) |
| Uninstall Savvy | Allowed | Detect + server restore on reinstall | Must deactivate admin first; deactivate and uninstall screens guarded | Prevent (except safe mode) | A-SELF-5, A-PARENT-2 | **Pixel 4:** Self restore Pass after fix (app list was not restored). Parent: `pm uninstall` refused while admin active; deactivate screen guarded (132 ms) |
| Reinstall Savvy | Commitment restored from server | Friction | Needs uninstall first | Prevent | A-SELF-6 | **Pixel 4 Pass** (permissions and card registration must be redone) |
| Revoke Accessibility | Allowed (no auth) | UsageStats fallback continues + Detect | Accessibility settings guarded | Prevent (except safe mode / AAPM) | A-SELF-7, A-PARENT-3 | **Pixel 4:** Self: UsageStats keeps blocking. Parent: Accessibility settings guarded (fix: guard now also under always-on rules) |
| Revoke Usage access | Allowed | a11y continues + Detect | Guarded via App info / special access screens (OEM names to collect) | Prevent partially | A-SELF-8 | **Pixel 4:** a11y keeps blocking. Usage access screen added to guard |
| Remove overlay permission | Allowed | a11y path unaffected | Guarded partially | Prevent partially | A-SELF-9 | Not run yet |
| Clear data | Allowed; local state lost | Server restore | **Button disabled for active admin** (AOSP) | Prevent | A-SELF-10 | **Pixel 4:** Self: as force stop plus state lost; a11y removed. Parent: UI button disabled, **but `adb shell pm clear` works with admin active** |
| Disable Accessibility via Android 17 Advanced Protection | a11y revoked | UsageStats fallback + Detect | Same | Detect | A-SELF-11 | Not run yet |
| Safe mode | All third-party apps off; in safe mode admin can be deactivated and app uninstalled | None | Same | Detect afterwards (heartbeat, admin disabled) | A-PARENT-4 | Not run yet |
| Factory reset | Everything removed | Detect (device stops reporting) | Same | Detect | - | Backend flag **Pass** |
| ADB uninstall | Possible with developer options | None | Detect adb_enabled | Detect | A-PARENT-5 | **Pixel 4:** `pm uninstall` and `dpm remove-active-admin` refused; `pm clear` succeeds (wipes rules). Detect only via adb flag / stale heartbeat |
| Battery optimization / OEM killer | Service may die | Onboarding + Detect | Same | Same | A-OEM-* | Not run yet |
| Open blocked app from notification | Blocked | Prevent | Blocked | Prevent | A-BLOCK-3 | Not run yet (manual) |
| Deep link | Blocked | Prevent | Blocked | Prevent | A-BLOCK-5 | **Pixel 4 Pass** |
| Search / Assistant | Blocked | Prevent | Blocked | Prevent | A-BLOCK-7 | Not run yet |
| Recent apps | Blocked | Prevent | Blocked | Prevent | A-BLOCK-4 | **Pixel 4 Pass** |
| Web version in browser | Not blocked (app blocking only) | None in MVP | Same | None in MVP | A-BLOCK-6 | Not run yet |
| Second app / clone app (Samsung Dual Messenger, Xiaomi Dual Apps) | Clone runs as another user; package detection must include it | To test | Same | To test | A-SELF-12 | Not run yet |
| Private space (Android 15+) | Instagram in private space may not be seen | To test | Same | To test | A-SELF-13 | Not run yet |
| Wrong NFC card / random tag | Rejected | Prevent | Rejected | Prevent | A-NFC-8 | **Pixel 4 Pass** (dispatch intent; no physical tag) |
| Copied QR | Accepted (identity only) | None | Same | None | A-QR-4 | Backend **Pass** |

## Summary

| Behaviour | Android Self | Android Parent |
| --- | --- | --- |
| Delete Savvy | Allowed; server restore and detect | Blocked behind admin + guard; safe mode remains |
| Revoke permissions | Allowed; fallback path and detect | Guarded; safe mode and AAPM remain |
| Restart bypass | Prevented | Prevented |
| Offline enforcement | Yes | Yes |
| Force stop / clear data | Allowed (breaks a11y until reopened) | Disabled for admin (AOSP) |
