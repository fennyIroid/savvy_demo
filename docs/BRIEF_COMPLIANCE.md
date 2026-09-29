# Android check against SAVVY.md (brief Part B and the cross-platform sections)

Check date: 29 September 2026. Branch `furtherenhancements`.
Scope: the Android POC only (this repo). The shared `docs/` folder, iOS and the backend were not changed.

**Status key**
- **Done**: already in the app before this check.
- **Added**: implemented in this pass.
- **Device**: code done, but still needs a real-device run.
- **Hardware**: needs the client's vendor card.
- **Decision**: waits for a client decision in OPEN_ITEMS.

## 1. Section by section

| Brief section | Requirement | Status | Where |
| --- | --- | --- | --- |
| 33 App selection | Launcher apps without QUERY_ALL_PACKAGES; select, persist, survive app reinstall | Done | `ui/screens/AppPickerScreen`, manifest `<queries>` |
| 34 A UsageStats | Detection by polling | Done | `service/UsageMonitorService` |
| 34 B Accessibility | Primary detection | Done | `service/SavvyAccessibilityService` |
| 34 C Overlay / block activity | Block screen | Done | `block/BlockActivity` + accessibility overlay fallback |
| 34 D Foreground service | Keeps the session running | Done. **Added**: live countdown in the notification | `UsageMonitorService` |
| 34 E Device admin | Parent mode | Done | `admin/SavvyDeviceAdminReceiver` |
| 34 F Device Owner / Profile Owner | Evaluate | **Added**: working R&D POC (was research only). Inert on normal installs | `admin/DeviceOwnerController` |
| 35 Restriction POC and launch paths | Launcher, recents, deep link, notification, split screen, PiP | Done. Pixel 4 passed launcher, recents and deep link. **Device**: notification, Assistant, split screen, PiP | - |
| 36 OEM reliability | Battery, autostart, OEM killers | **Added**: brand keep-running screen (Samsung, Xiaomi, Oppo, Vivo, OnePlus, Huawei); alert when Accessibility is turned off; periodic job that re-arms enforcement. **Device**: needs the OEM test plan | `data/OemBackground`, `service/Alerts`, `service/SyncJobService` |
| 37 Accessibility policy | Disclosure, consent, no hidden use | Done. The alert routes back through the disclosure screen, not straight to Settings | `PermissionList` |
| 38 Self anti-bypass | Force stop, clear data, revoke, reboot, time | Done (Pixel 4). **Added**: force-stop alert on next open; offline reboot clock fix | See section 2 |
| 39 Self uninstall delay | Evaluate DA, a11y, DO, PO, lock task | Done (research + server restore). **Added**: Device Owner proof. Self-mode device admin stays **Decision** C12 | `DeviceOwnerController` |
| 40 Parent anti-uninstall | Admin + guard + detection | Done. **Added**: child can no longer leave parent mode from Settings | See section 2 |
| 41 NFC | Foreground, background, killed, locked, off | Done (dispatch intent on Pixel 4). **Added**: tag writer, so real-tag tests can run. **Hardware**: vendor card | `nfc/NfcTagWriter`, Card screen |
| 42 NFC end-to-end unlock | Valid and invalid cards, offline | Done | `UnlockCoordinator` |
| 43 QR | Same card model | Done. Copy-risk policy is **Decision** C1 (enforced by the backend) | - |
| 44 To-do / commitment / emergency | Start, complete, expiry, 6 h / 24 h, restart, offline | Done. **Added**: task state after unlock or expiry; proactive expiry; "until done" tasks; real emergency allowance and cooling-off confirm | See section 2 |
| 45 Usage insights | Duration, daily totals, history, parent reporting | Done. **Added**: 35-day local history (A-USAGE-2) | `UsageCollector.recordHistory` |
| 46 Play compliance | Declarations, disclosures | Documented in STORE_POLICY_RISKS.md; no code change needed. The Device Owner path is not for Play | - |
| 25 Child changes Savvy settings | Test and prevent | **Added**: role, account and app list are locked on a managed child phone. Leaving needs the parent's PIN | `SavvyActions.leaveParentMode`, `core/ParentPin` |
| F Parent manages focus rules | Mode and policy | **Added**: the parent chooses Study / Work / Sleep, card / locked / free, up to 24 h. Before, it was always "study" with the card | `ChildRulesScreen` |
| 51 Offline-first | Local rules, cached card | Done | - |
| 52 Time manipulation | Clock and timezone change, reboot, offline | **Added**: the "reboot + offline + clock forward" gap is closed (see section 2) | `core/TimeIntegrity`, `BootReceiver` |
| 53 Reinstall | Restore | Done (Pixel 4) | `SavvyActions.restore` |
| 54 Card validation | Static, signed, SUN, live proof | Done | `core/CardPayload`, `nfc/LiveProofRelay` |

## 2. What was added and why

1. **Task state after unlock (device run 1 open item).**
   - **Before:** a card unlock, emergency exit or expiry of a task session left the task "active" forever.
   - **Now:** every release goes through `UnlockCoordinator.releaseLocally`, which marks the task **ended** (apps unlocked, not done).
   - The task shows "Mark done" (no card needed, since nothing is blocked any more) and "Start again".
   - A task becomes "active" only once the restriction is really on. Before, it was set first, so a refused start left it stuck.
2. **Proactive expiry (to-do Flow B, brief section 26).**
   - **Before:** expiry was only noticed at the next app switch.
   - **Now:** the FGS loop releases the session the moment time is up. The notification disappears and the task is marked.
3. **Offline reboot clock fix (section 52).**
   - **Before:** after a reboot with no internet, remaining time came from the wall clock, so moving the clock forward ended the commitment.
   - **Now:**
     - A checkpoint of monotonic credited time is saved every minute and on every `TIME_SET`.
     - The boot wall clock is recorded at `LOCKED_BOOT_COMPLETED`, before the user can unlock and open Settings.
     - After a reboot, elapsed time = checkpoint + gap to boot + time since boot. Honest power-off time still counts.
   - Server time is now trusted only if it was read in the current boot and advanced monotonically. The old "wall clock + saved offset" was manipulable.
   - Remaining gap: clock changed and phone rebooted with Savvy force-stopped (no `TIME_SET` delivered). Force stop is already a full bypass in self mode.
   - Covered by 5 core tests.
4. **Child settings lock and parent PIN (sections 25 and 40).**
   - **Before:** a child could pick "Just me" in Account. That turned the tamper guard off, and they could then uninstall.
   - **Now**, on a child phone under parent rules:
     - role change, re-registering (which would give an unlinked token) and app-list edits are refused;
     - leaving parent mode needs the PIN the parent sets in the rules. Only its hash travels, bound to the child device id;
     - no backend change was needed, because rules are stored as sent.
   - POC limit: a 4 to 8 digit hash can be brute-forced by someone with root access to app storage. Production should check it on the server with rate limiting.
5. **Parent focus rules.** The parent now chooses mode, unlock policy and length (up to 24 h). The last rules sent are prefilled, because the backend has no GET for them.
6. **Child rule sync without the FGS.**
   - **Before:** the FGS runs only while something is blocked, so a child phone with no rule yet received the first rule only when opened.
   - **Now:** `SyncJobService` (JobScheduler, every 15 minutes, persisted) also sends the heartbeat and re-arms enforcement after an OEM kill.
   - Instant delivery still needs push (FCM, a Firebase project), which is not set up.
7. **Accessibility-off alert (section 36).**
   - A notification appears when the service is turned off during a session: by the user, a force stop, Oppo/Vivo screen-off killing, or Android 17 Advanced Protection.
   - The state is persisted, so a force stop is caught when Savvy is next opened.
   - A heartbeat is sent at once, so a parent sees it without waiting 15 minutes.
8. **OEM keep-running screen.**
   - It deep-links to each brand's autostart or never-sleeping screen, with several component names and a fallback to App info.
   - On the attached Samsung M36 (Android 16), `com.samsung.android.lool/...BatteryActivity` exists.
   - Savvy cannot read these settings back, so the row only records that the user opened the screen.
9. **Emergency exit.**
   - The allowance is read from `/v1/emergency-exits/usage` (it was hard-coded "2 a week").
   - With a backend cooling-off period, the request is remembered, and the screen shows "Available at hh:mm", then "Confirm".
10. **NFC tag writer (debug builds).**
    - Card › "Write test card to an NFC tag" writes the dev card URL as record 1 and an Android Application Record as record 2 (URL only if the tag is too small).
    - Tags are never locked.
    - This unblocks A-NFC-1/2/3/6 with generic NTAG213/215/216 tags before vendor samples arrive.
11. **Device Owner POC (Approach F, R&D alternative only).**
    - When provisioned (`adb shell dpm set-device-owner com.iroid.savvy.rd/.admin.SavvyDeviceAdminReceiver`, on a phone with no accounts), a session:
      - suspends the blocked apps (the OS refuses them from every launch path, with no flash);
      - blocks Savvy's uninstall;
      - sets `DISALLOW_SAFE_BOOT`.
    - All of this is lifted on release or pause.
    - Diagnostics shows the state and can remove Device Owner.
    - This proves the brief's "technically possible under special device management". It is **not** a consumer or Play feature.
12. **Usage history.** Daily per-app totals are kept on the phone for 35 days, so the Activity week and average are not limited by the OS's few-day event retention.

## 3. Tests

| Suite | Result |
| --- | --- |
| `./gradlew :core:test` | 27 pass (21 before + 5 time integrity + 1 parent PIN) |
| `./gradlew -Psavvy.compilecheck=true :compilecheck:test` (Robolectric + real backend) | 16 pass (11 before + task ended, parent PIN / mode / policy, emergency usage, time-change and locked-boot wiring, child role without link) |
| `./gradlew :app:assembleDebug` | Builds |

## 4. Device tests to add to ANDROID_POC_RESULTS.md

| Test | Steps | Expected |
| --- | --- | --- |
| A-TIME-5 offline reboot clock | Start a 6 h session. Turn Wi-Fi and data off. Reboot. Unlock, set the clock +8 h, open Instagram | Still blocked; Diagnostics shows `bootWall` set and remaining about 6 h minus real time |
| A-TIME-6 honest power-off | Start a 2 h session, power off 30 min offline, boot | Remaining about 1 h 30 min |
| A-TASK-3 expiry | 15 min task, wait without touching the phone | Notification disappears at 15 min; task shows "Apps unlocked · not marked done" |
| A-TASK-4 card on task | Start a task, open Instagram, hold the card on the block screen | Unlocked; task "ended", not stuck "active" |
| A-PARENT-6 child role lock | On the child: Settings › Account | Role locked; "Register again" disabled |
| A-PARENT-7 leave with PIN | Parent sets PIN 2468 in the rules; child syncs; Family › Leave parent mode with 1111, then 2468 | 1111 refused; 2468 leaves; always-on blocks stop |
| A-PARENT-8 first rule without FGS | Link the child, close Savvy, send a rule from the parent, wait 15 to 20 min | Rule applied without opening Savvy |
| A-OEM-8 a11y off alert | Session on; turn Savvy off in Accessibility settings | "Savvy can't see blocked apps" notification within about 5 s; tap opens Permissions |
| A-OEM-9 force stop alert | Session on, a11y on; Force stop; open Savvy | Alert notification appears |
| A-OEM-10 brand screen | Settings › Permissions › brand row on Samsung, Xiaomi, Oppo, Vivo, OnePlus | The brand's screen opens (else App info) |
| A-NFC-10 write tag | Debug: Card › Create a test card › Write test card to an NFC tag, hold an NTAG215 | "Tag written"; then run A-NFC-1/2/3 with it |
| A-DO-1 Device Owner | Test phone with no accounts: `dpm set-device-owner`; start a session | Instagram shows the system "app not available" dialog; uninstall of Savvy refused; safe-mode boot refused; all lifted on release. Diagnostics › Remove Device Owner restores the phone |
| A-EMERG-2 cooling-off | Backend `coolingOffSeconds: 600`; use emergency exit | "Available at hh:mm", then "Confirm emergency exit" releases |

## 4a. Device run: Samsung Galaxy M36 (SM-M366B), Android 16, 2026-09-29

Local backend over `adb reverse tcp:3000 tcp:3000`, card links sent with `adb shell am start -a VIEW -d <card url>` (same path as a background tag tap / App Link).

| Test | Result |
| --- | --- |
| Upgrade install over the old build | Pass. `MY_PACKAGE_REPLACED` recorded `bootWall` (device-protected `savvy_boot`), boot count 33 matches `settings get global boot_count`; periodic sync job scheduled |
| Register, link signed card via link | Pass |
| Session start, FGS notification | Pass. Countdown chronometer ends exactly at start + 25 min |
| Blocked app (usage-stats path) | Pass. Block screen about 0.3 s after launch, remaining time shown |
| Clock +3 h (`cmd alarm set-time`, auto time off) | Pass. `TIME_SET` checkpoint logged; block screen still shows the real remaining time (23 min) |
| Card unlock on block screen | Pass. Released online; FGS and notification gone |
| A-TASK-4 "Until done" task + card | Pass. 1440 min session; task "active" only after start; card marks it done |
| A-OEM-10 Samsung row | Pass. Opens `com.samsung.android.lool/…BatteryActivity` |
| Emergency exit, no cooling-off | Pass. Released; server usage 1 of 2 |
| A-EMERG-2 cooling-off (backend copy with 60 s) | Pass. "Available at 1:06 am", button disabled, then "Confirm emergency exit" releases; pending cleared |
| Periodic sync job (`cmd jobscheduler run -f`) | Pass. Sync and heartbeat run from the job |
| A-PARENT-6 child role lock | Pass after fix 1. Role hidden, "Register again" disabled, app list read-only, always-on app blocked with "Your parent has blocked this app" |
| A-PARENT-7 leave with PIN | Pass after fix 2. Wrong PIN refused; right PIN leaves and the always-on app (Ajio) opens again |
| Device Owner diagnostics | Shows "off" and the adb command. Provisioning not tried: personal phone with accounts |
| A-OEM-8 a11y off alert | Not run: Android 16 removed Savvy from `enabled_accessibility_services` when set over adb; needs enabling by hand in Settings |
| A-TIME-5/6 reboot, A-NFC-10 write tag | Not run: need a reboot with the user present to unlock, and a blank NTAG |

Bugs found and fixed in this run:
1. Picking "Child's phone" before linking made the phone "managed by parent" at once: "Register again" and the role picker were locked and no parent PIN existed to leave. `managedByParent` now also needs `parentLinked` (set by `linkAsChild`, cleared by a new registration and by leaving). Robolectric test `childRoleWithoutParentLinkIsNotManaged`.
2. Leave parent mode always answered "wrong PIN": the button cleared the PIN field before the background action read it. The PIN is now read when the button is pressed.
3. "1 apps blocked" in the session notification and Family status.

## 5. Not implemented, with reasons

| Item | Why |
| --- | --- |
| Push for instant parent rules | Needs a Firebase project and backend FCM sender (OPEN_ITEMS F9). The 15-minute job is the fallback |
| Self-mode device admin "extra uninstall step" | Client decision C12 and Play policy check pending |
| QR copy mitigations (delay or limit) | Client decision C1; enforced by the backend, not the app |
| Child's card unlocking parent rules | Client decision C10 |
| Website blocking | Out of MVP scope (C13) |
| Real-device and OEM runs, vendor card | Environment. The steps are in section 4 |

## 6. Shared docs to update (outside this repo, not edited)

- `docs/ANDROID_POC_RESULTS.md`: the "still open" item about the task left active is fixed. Add the tests in section 4.
- `docs/ANDROID_BYPASS_MATRIX.md`: "Change date/time" after reboot offline is now Prevent (was Detect). Add "Child switches role in Savvy" as Prevent (parent PIN).
- `docs/ANDROID_FEASIBILITY.md`: R&D 2 table, row F: Device Owner POC exists. R&D 4: OEM screen and alert implemented.
