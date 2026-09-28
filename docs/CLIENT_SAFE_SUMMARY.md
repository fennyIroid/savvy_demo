# Savvy: Technical Feasibility Summary and Recommended MVP Scope

Prepared by iRoid Solutions, 25 September 2026.

## Where the R&D stands

We have completed the research and the first proof-of-concept code for both iPhone and Android.

**What has been proven so far (72 automated tests, all passing):**
- **Backend:** card checking, commitments, emergency exits, offline sync, parent-child rules and streaks.
- **Secure card (NXP NTAG 424 DNA):** cannot be usefully copied. Verified against NXP's own published example, and a live "hold the card" check stops copied or pre-recorded taps.
- **Android:** the app code compiles against the real Android 16 system. Its main flows run end to end in a simulated Android environment: block, card unlock, offline unlock, parent rules, usage, tamper alerts and restart.
- **iPhone:** the app logic runs end to end against the backend on a test machine: shield on and off, card unlock, offline unlock, clock-change protection, reinstall restore and parent rules. Apple's own system is simulated.

**What still needs real phones:**
- Neither app has been installed on a real phone yet. That needs a Mac with Xcode, Android Studio, test phones, Apple's Screen Time approval and your card samples.
- Only real-phone tests can confirm how iOS and Android themselves behave.

Everything below that depends on those tests is marked clearly. We will not treat any item as final until it has been shown working on a real device, with a screen recording.

---

## Confirmed

We can commit to these features on both iPhone and Android.
- Choosing the distracting apps to restrict.
- Starting a focus session in Study, Work or Sleep mode, or for a to-do task.
- The selected apps stay restricted when Savvy is closed, and when the phone has no internet.
- 6-hour and 24-hour commitments. The end time is kept on Savvy's server, so changing the phone's clock or reinstalling Savvy does not end a commitment early.
- To-do based restriction: apps stay restricted until the task is marked done or the set time ends.
- A limited emergency exit that is recorded and counted.
- Linking a Savvy card to one account, and rejecting any other card.
- Focus time and streak tracking.
- A parent linking a child's phone and setting the child's focus rules from the parent's phone.

## Confirmed with platform difference

These work on both platforms, but the experience is different on iPhone and Android.

| Feature | iPhone | Android |
| --- | --- | --- |
| Unlock with the NFC card | Tap "Unlock with Savvy card" on the Savvy screen, then hold the card to the phone. On older iOS versions there is one more tap on a notification. iOS requires this step; no iPhone app can read a card silently | Hold the card to the phone while the Savvy screen is showing. One action |
| Tapping the card when Savvy is closed | iPhone shows a notification; tapping it opens Savvy and unlocks | Savvy opens directly and unlocks |
| Screen-time summary | Shown inside Savvy on the same iPhone. Apple does not allow apps to save or send this data, so a parent cannot see it on their own phone | Shown in Savvy and can be shared with the parent (with clear disclosure) |
| Minimum session length | 15 minutes (Apple limit) | No minimum |
| What the parent sees about the child | Focus sessions, commitments, emergency exits, and whether protection is on | The same, plus app usage minutes if you choose to include it |

## Confirmed with limitation

- **QR code fallback.** The QR code on the card works as a backup key. A QR code can be photographed, so it proves which card it is, but not that the physical card is present. We recommend one of these rules: QR unlocks take effect after a short wait, or QR unlocks are limited, like emergency exits.
- **Self-use commitment lock (iPhone).**
  - During a commitment, Savvy locks app deletion on the iPhone and forces automatic date and time.
  - The phone owner can still turn off Savvy's Screen Time access in iPhone Settings using their own Face ID or passcode. Apple designed this on purpose, and no App Store app can prevent it.
  - If this happens, Savvy records it, and reinstalling Savvy brings the commitment back.
- **Self-use commitment lock (Android).**
  - Google Play does not allow a self-use app to block its own removal or the disabling of its permissions.
  - Savvy uses two separate detection methods, records any switch-off, and restores the commitment after reinstall.
  - Restarting the phone does not end the commitment.
- **Android phone brands.** Some brands (Xiaomi, Oppo, Vivo, OnePlus, Samsung) aggressively close background apps. Savvy will guide users through the right settings on first setup and detect when protection has been switched off. We will test on each major brand before launch.
- **Website versions of apps.** On Android, the MVP restricts apps, not websites in a browser. On iPhone, Safari websites can also be restricted.

## Requires client decision

1. **QR rule:** instant, delayed, or limited (see above).
2. **Card type:**
   - a standard NFC card (low cost, and its data can be copied with the right tools);
   - or a secure NFC card, NXP NTAG 424 DNA. Its data cannot be usefully copied. It costs more per card and needs internet to check.
3. **Task completion:** should marking a task as done also require the card?
4. **Emergency exit rule:** how many per week, and whether there is a short waiting time before it works.
5. **Parent mode:** can the child use their own card to unlock, or only with the parent's approval?
6. **Wording:** we recommend "commitment lock" instead of "uninstall protection" for self-use (see below).

## Requires hardware / vendor input

- Sample cards from your card vendor, and answers to our 13-point technical questionnaire: chip model, memory, encoding, security features and price.
- A card web domain (for example go.savvy.app). The card's link and QR code use it.

## Requires external approval

- **Apple Family Controls (Screen Time) approval** for the Savvy app and its 4 helper extensions. This is mandatory before TestFlight or App Store release. Developers report waits from a few days to over a month, so we recommend applying immediately.
- **Google Play declarations** for the Accessibility service and the foreground service, with demo videos, and a clear disclosure screen (already built into the POC).
- Neither store can confirm approval before submission. Our design follows current Apple and Google rules.

## Parent-child mode: stronger protection

- **iPhone.** When a parent approves Savvy on the child's iPhone through Apple Family Sharing:
  - only the parent can delete Savvy;
  - the child cannot turn off Savvy's access without the parent;
  - the child cannot sign out of iCloud.

  Family Sharing with a child Apple Account is required for this.
- **Android.** Savvy uses device administrator protection and guards the settings screens used to remove it. The parent is shown when protection is switched off.

  A technically skilled child could still use Android safe mode or a factory reset. Savvy cannot block these on a normal Play Store app, but it will show the parent that protection was removed.

## Not technically guaranteeable

We have tried every approach allowed on the App Store and Google Play. These specific promises cannot be guaranteed in **self-use** mode:
1. "Savvy cannot be uninstalled for 6 or 24 hours" on a phone the user owns and controls. On both platforms this is only possible with company-style device management (MDM or Device Owner), which needs a factory reset or special enrollment and is not suitable for a consumer app.
2. "The user cannot switch off Savvy's permissions" in self-use mode.
3. "Unlock with a single tap of the card and nothing else" on iPhone.

What we can promise instead:
- removal is deliberate, takes several steps, and is recorded;
- reinstalling Savvy brings the commitment back;
- the card is always required to unlock early, apart from the limited emergency exit.

## Recommended wording for the proposal

- **Commitment lock:** "Once you start a commitment, your chosen apps stay locked until the time ends or you unlock with your Savvy card. Savvy locks app deletion during the commitment and keeps your commitment even if Savvy is reinstalled."
- **NFC unlock on iPhone:** "Tap Unlock on the Savvy screen and hold your card to your iPhone."
- **NFC unlock on Android:** "Hold your Savvy card to your phone to unlock."
- **Parent mode on iPhone:** "With Apple Family Sharing, only a parent can remove Savvy from the child's iPhone."
- **Parent mode on Android:** "Savvy protects its settings on the child's phone and alerts the parent if protection is switched off."

## Next steps

1. Your approval to apply for Apple's Screen Time entitlement, and access to the Apple Developer account.
2. Card vendor contact and sample cards.
3. Your decisions on the 6 points above.
4. iRoid builds the POC apps on real devices, runs the test plan (over 100 recorded test cases), and shares the video evidence and the final go / no-go report.
