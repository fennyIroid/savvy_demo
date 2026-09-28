# Savvy NFC Findings

Status date: 25 September 2026.
Code:
- `backend/src/crypto/*` (tested)
- `ios/SavvyRDiOS/App/NFC`, `Shared/CardPayload.swift` (written)
- `android/SavvyRDAndroid/.../nfc` (written)

## 1. What the card must do

The card is Savvy's "key". Two separate questions must not be mixed:
1. **Identity:** is this the Savvy card that belongs to this account?
2. **Physical possession:** was the real card physically at the phone just now, or is someone replaying a copy of its data?

A static NFC payload or a QR answers question 1 only. Question 2 needs a chip that produces a fresh cryptographic value on every tap (NTAG 424 DNA SUN), or a live challenge-response.

## 2. One card identity for NFC, QR, iOS and Android

The card carries **one HTTPS URL**:
- as the first NDEF URI record on the chip;
- printed as the QR code on the same card.

| Format | URL | Verify offline | Proves possession | Chip |
| --- | --- | --- | --- | --- |
| A. Static id | `https://<domain>/c/<cardCode>` | No (server lookup) | No | Any NDEF chip |
| B. Signed id (Ed25519) | `https://<domain>/c/1.<cardCode>.<sig>` | **Yes**, with public key in app | No | NTAG213 only with a short domain (about 114 bytes tested with `go.savvy.test`), NTAG215 / 216 comfortable |
| C. Account + device binding | Applied on top of every format by the backend | - | No | - |
| D. NTAG 424 DNA SUN | `https://<domain>/c/s/<cardCode>?e=<picc>&c=<mac>` | No (needs AES key) | **Yes** (fresh MAC + counter) | NTAG 424 DNA |

Why a URL and not a custom MIME type:
- iOS background tag reading requires a universal link (Official). Custom schemes and MIME types are not delivered in the background.
- The same URL also works in Android App Links, in the system Camera QR scanner, and on a web fallback page when Savvy is not installed.

## 3. What is proven in the backend (automated tests)

| Claim | Test | Result |
| --- | --- | --- |
| AES-CMAC implementation correct | RFC 4493 vectors (empty, 16-byte, 40-byte) | Pass |
| NTAG 424 DNA SUN decryption and MAC correct | NXP AN12196 published example: `e=EF963FF7828658A599F3041510671E88`, `c=94EED9EE65337086`, zero keys, gives UID `04DE5F1EACC040`, counter 61 | Pass |
| Tampered SUN MAC rejected | 1 bit changed in MAC | Pass |
| Fresh SUN tap accepted, same URL second time rejected | Simulated chip | Pass |
| Chip with same UID but without the AES key cannot pass | Simulated clone | Pass |
| Signed card cannot be forged by copying a signature to a new id | Ed25519 | Pass |
| Signed URL fits NTAG213 (144 bytes user memory) | Length check | Pass |
| Card bound to one account; account has one card; other user's card rejected | API tests | Pass |
| Random URL or tag rejected | Parser tests | Pass |
| Copied static or signed payload accepted (identity only) | API test | Pass (as designed, flagged `proof_of_presence=false`) |

## 4. Chip comparison

| Chip | iOS | Android | Clone resistance | Approximate unit price (unverified vendor listings) | Notes |
| --- | --- | --- | --- | --- | --- |
| NTAG213 / 215 / 216 | Yes (NDEF, background, UID via tag session) | Yes | Low | about $0.27 (NTAG213 PVC at 1000 units) | UID, NDEF and even the originality signature can be copied to "magic" tags (Proxmark3 documentation) |
| MIFARE Ultralight EV1 / AES | Yes (as `NFCMiFareTag`) | Yes (NXP support) | Medium | Not checked | AES authenticates the reader, not a dynamic NDEF URL |
| **NTAG 424 DNA** | Yes (background URL; tag session as ISO 7816 with AID `D2760000850101`) | Yes (IsoDep) | **High** for dynamic data | about €0.49 to $2.50 | AES-128, SUN/SDM, per-tap counter |
| MIFARE Classic | **No** (not supported on iPhone, Official) | Only on phones with NXP controllers | Very low (Crypto1 broken) | - | **Do not use** |
| ICODE / ISO 15693 | Yes | Yes | Low to medium | - | No benefit for Savvy |

## 5. Can the UID be the identity?
**No.**
- On iOS, the UID is only available in the foreground tag session, not in background reading.
- Android `Tag.getId()` warns that some tags give random IDs.
- UIDs are cloneable.

The POC logs the UID for diagnostics only.

## 6. NTAG 424 DNA "pre-play" weakness

- A person holding the card can tap it many times with any NFC reader app, save the URLs, and redeem them later in order.
- Each saved URL has a valid MAC and a higher counter, and the chip adds no timestamp.
- The counter stops **replay** of used URLs. It does not stop **harvesting** of future ones.

For a self-control product, this needs a deliberate effort (tapping the card many times in advance and keeping the URLs). That is still far more effort than copying a static URL.

**Fix, now implemented as a POC (backend, iOS and Android):**
1. The Savvy app keeps the NFC session open. This is `NFCTagReaderSession` / `NFCISO7816Tag` on iOS and `IsoDep` on Android.
2. The app relays the card's AES `AuthenticateEV2First` exchange between the card and the server: `POST /v1/cards/live/start`, then `/step`, then `/finish`.
3. The server supplies a fresh random value (RndA) each time and checks the card's encrypted answer. It then issues a single-use presence token, valid for 60 seconds, which the release call accepts.

**Proven by backend tests with a simulated chip:**
- the real key passes;
- a clone without the key fails;
- a recorded answer replayed into a new session fails;
- a finished session cannot be reused;
- a token cannot be used twice;
- a session expires after 30 seconds.

Policy flag `requireLiveProofForSunCards` makes the live proof mandatory for NTAG 424 DNA cards.

**Remaining work:**
- Real-card tests: T-NFC-LIVE-1..4 on iOS, A-NFC-LIVE on Android.
- The vendor must write our per-card key into key slot 3. Production keys should follow NXP AN10922 diversification and be held in KMS/HSM.
- It needs a network round trip of about 2 steps while the card is held.

## 7. Recommendation for MVP

| Priority | Recommendation |
| --- | --- |
| If cost matters most | NTAG215 or NTAG216, format B (signed URL). Works on every path, offline verify, same QR. Copy risk accepted and disclosed |
| If "card cannot be copied" is a selling point | NTAG 424 DNA, format D. Replay and clone resistant (backend-proven). Needs internet to verify. Pre-play documented |
| Do not use | MIFARE Classic, UID-only identity |

The backend supports all formats at the same time. The format is chosen per card batch, so the decision can be made after vendor samples arrive.

## 8. NFC UX by platform (to be confirmed on device)

| State | iOS | Android |
| --- | --- | --- |
| Savvy foreground | In-app scan: tap Scan, hold card (or automatic after shield handoff) | Reader mode while block screen is visible: hold card (1 action) |
| Savvy background | Tap card, tap notification (2) | Tap card: App Link / NDEF intent opens Savvy directly (1) |
| Savvy terminated | Tap card, tap notification (2) | Tap card (1), app launches |
| Screen locked | Tap card, unlock, tap notification | Generally not read while locked (Official: "usually looking for NFC tags when the screen is unlocked"); unlock first |
| Blocked app visible | Shield button, hold card (2 on iOS 26.5+) | Savvy block screen is Savvy's own Activity: hold card (1) |
| No internet | Signed card: offline verify. SUN card: fails | Same |
| Invalid card | Rejected with message | Rejected with message |
| NFC disabled | iPhone has no NFC switch for reading; Airplane mode stops background reading only | User must enable NFC; Savvy shows a button to NFC settings |
| Tag intents disabled for Savvy (Android 16+) | N/A | Background tap does nothing; `isTagIntentAllowed()` check; reader mode still works |

## 9. Questions for the card vendor

1. Chip manufacturer and exact chip model (for example NXP NTAG216, NXP NTAG 424 DNA).
2. NFC Forum type and ISO standard (Type 2 / ISO 14443-3A, Type 4 / ISO 14443-4).
3. User memory size.
4. Can you pre-encode one NDEF URI record per card from our CSV (`backend/scripts/provision-cards.js`)?
5. Can you lock the card after encoding (permanent read-only)?
6. For NTAG 424 DNA: can you configure SDM/SUN with our keys (PICC data mirroring, SDMMAC, MAC input offset equal to MAC offset), and change the default keys?
7. How do you handle keys securely? Can we use per-card diversified keys (NXP AN10922)?
8. Is the UID 7-byte and unique? Do you supply a UID list per batch?
9. Originality signature support (NTAG21x READ_SIG / 424 DNA)?
10. Can the QR be printed with the same URL, and at what size and error-correction level?
11. Card material, antenna size (affects read distance on iPhone), and print options.
12. Minimum order, unit price at 1,000 / 5,000 / 10,000, sample lead time.
13. Bulk serial / URL export format and any API.

## 10. What still needs hardware

- Read time and reliability on iPhone and Android with the vendor card (T-NFC-6, Android A-NFC-6).
- The real NTAG 424 DNA card generating SUN URLs that the backend verifies with our keys. This confirms the vendor configuration matches AN12196.
- Antenna position and read distance on small iPhones and on phones with a case.
