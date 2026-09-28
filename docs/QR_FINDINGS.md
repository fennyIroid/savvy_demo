# Savvy QR Fallback Findings

Status date: 25 September 2026.

## 1. Design

- The QR printed on the card contains **the same URL** as the NFC chip. See `NFC_FINDINGS.md`.
- One card identity serves both NFC and QR, on iOS and on Android.
- Savvy scans it with a live camera scanner:
  - iOS: `AVCaptureMetadataOutput`;
  - Android: CameraX with ML Kit barcode scanning, or ZXing.
- **Import from Photos is deliberately not offered.**
- The system Camera app also works. On iOS and Android it opens the URL, and the universal link or App Link routes it to Savvy.

## 2. Test results

| Case | Result | Evidence |
| --- | --- | --- |
| Valid QR of own card | Accepted | Backend test (same path as NFC) |
| Invalid QR (random text or URL) | Rejected `not_a_savvy_card` | Backend parser test |
| Another user's card QR | Rejected `card_not_owned_by_user` | Backend test |
| Forged QR (new id with copied signature) | Rejected | Backend test |
| **Screenshot / photograph / printed duplicate / copied URL** | **Accepted** | Backend test "copied QR ... is accepted"; `proof_of_presence=false` |
| Offline, signed card | Accepted by app's offline check (if enabled) | App code written; device test T-QR-6 |
| Offline, SUN card URL printed as QR | Not possible: SUN URL changes per tap, so a printed QR cannot be SUN | By design |
| Backend unavailable | Same as offline | App code written |
| On-device scan | Not run yet | T-QR-1..7, Android A-QR-1..6 |

## 3. Identity versus physical possession

A static QR proves **which card** it is. It does not prove that **the physical card is present**. Anyone can take a photo of their own card and keep it on their phone or in another place. Hiding the Photos import only removes the easiest path: a photo shown on a second phone still scans.

So the QR **weakens physical-possession enforcement** compared with an NTAG 424 DNA card. It is about equal to a static NFC card (whose URL can also be copied).

## 4. Alternatives, without over-engineering

| Option | Effect | Cost |
| --- | --- | --- |
| 1. Accept and disclose | Simple. QR equals static NFC in strength | None |
| 2. **QR unlock only after a delay (for example 10 minutes)** | Removes the instant shortcut. Reuses the backend's emergency cooling-off logic | Small |
| 3. **QR unlock counted and limited like emergency exits** | Makes QR a rare fallback | Small |
| 4. QR allowed only when the device has no NFC or NFC is off | Forces NFC where possible. On iPhone every model since iPhone 7 has NFC | Small |
| 5. Scratch-off or tamper-evident QR on the card | Physical friction | Manufacturing |
| 6. Rotating QR shown on a second Savvy device | Real possession proof, but needs a second device | High; not MVP |

**Recommendation:** Option 1 plus Option 2 or 3. The client must choose (OPEN_ITEMS). All of these are server-side policies using existing code paths.

## 5. Client-safe wording

"If your phone cannot read NFC, you can scan the QR code on your Savvy card instead. The QR code identifies your card. Because a QR code can be photographed, QR unlocks [take effect after a short wait / are limited], so the physical card stays the main key."
