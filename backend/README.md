# Savvy R&D Backend POC

Node.js + Express. In-memory store that mirrors `db/schema.sql` (PostgreSQL).
This is an R&D proof, not production code. Auth is a simple per-device bearer token.

## Run

```bash
npm install
npm test          # 22 tests, runs in about 2 seconds, no database needed
npm start         # listens on :3000
node scripts/provision-cards.js signed 5   # sample card batch CSV for the vendor
```

## What the tests prove (on this backend, not on phones)

| Area | Test | Result |
| --- | --- | --- |
| AES-CMAC | RFC 4493 vectors | Pass |
| NTAG 424 DNA SUN | NXP AN12196 published example (UID 04DE5F1EACC040, counter 61) | Pass |
| SUN replay | Same tap URL used twice is rejected | Pass |
| SUN clone | Same UID, wrong AES key, is rejected | Pass |
| Signed card (Ed25519) | Forged id with copied signature is rejected | Pass |
| Card binding | One account per card, one card per account, other user's card rejected | Pass |
| Copied QR | Screenshot of static QR is accepted (identity only, `proof_of_presence=false`) | Pass, documents the limitation |
| 6-hour card commitment | No early end without the right card, grant signed and bound to device | Pass |
| 24-hour locked commitment | Card cannot end it, expiry uses server clock only | Pass |
| Reinstall | New install on same account and platform adopts the active commitment, same `ends_at` | Pass |
| Task restriction | Task completion releases, card-protected task also needs the card, timeout releases | Pass |
| Emergency exit | Limit per window, cooling-off, pause or release, usage tracked | Pass |
| Streak | Consecutive local days (time zone aware) | Pass |
| Parent-child | Link code, versioned rules, pull + ack, tamper flags for parent | Pass |
| Android parent protections | Accessibility, device admin off, Advanced Protection or USB debugging on are flagged to parent | Pass |
| Offline sync | Offline start, card release, over-limit emergency flagged, idempotent replay, device time clamped | Pass |
| Parent inventory and usage | Children list, Android app inventory, daily usage (replace per day), stranger refused, iOS upload refused | Pass |
| Push outbox | Rule change queues a silent push once the child reported a push token | Pass |
| NTAG 424 DNA live proof | Relay succeeds; clone and pre-played answers fail; session and token single use; 30 s expiry; policy enforced | Pass |
| Dev card factory | Exists only with SAVVY_DEV=1 | Pass |

## Main endpoints

| Method | URL | Purpose |
| --- | --- | --- |
| GET | /v1/time | Server time, used to detect device clock changes |
| GET | /v1/keys | Public keys embedded in apps (grant and card verification) |
| POST | /v1/devices/register | Register install, returns device token |
| POST | /v1/cards/register | Bind scanned card (NFC or QR payload) to account |
| POST | /v1/cards/verify | Check scanned card belongs to this account |
| POST | /v1/commitments | Start focus / task / commitment (server sets start and end) |
| GET | /v1/commitments/active | Restore state on launch, reboot and reinstall |
| POST | /v1/commitments/:id/release | End by `card`, `task_complete` or `user` (policy dependent) |
| POST | /v1/commitments/:id/emergency-exit | Limited, tracked emergency exit |
| GET | /v1/insights/summary | Focus time and streak |
| POST | /v1/devices/heartbeat | Device reports authorization and rule state |
| POST | /v1/family/link-codes | Parent creates link code |
| POST | /v1/family/link | Child install redeems code |
| PUT | /v1/family/children/:deviceId/rules | Parent updates versioned rule set |
| GET | /v1/family/rules | Child pulls rules |
| POST | /v1/family/rules/ack | Child confirms rule version applied |
| GET | /v1/family/children/:deviceId/status | Parent sees tamper flags |
| GET | /v1/family/children | Parent lists linked children |
| PUT | /v1/family/inventory | Android child reports launchable apps |
| GET | /v1/family/children/:deviceId/inventory | Parent reads child's app list (Android) |
| PUT | /v1/usage/daily | Android device uploads per-app daily usage (iOS cannot) |
| GET | /v1/family/children/:deviceId/usage | Parent reads child's usage (Android) |
| POST | /v1/sync | Replays actions taken offline (idempotent) |
| POST | /v1/cards/live/start, /step, /finish | NTAG 424 DNA live proof relay; returns single-use presence token |
| POST | /v1/dev/cards | Dev only (SAVVY_DEV=1): create a test card |

Every release returns an Ed25519-signed `grant`. The apps verify it with the public
key before removing shields. See `docs/NFC_FINDINGS.md` and `docs/QR_FINDINGS.md`.
