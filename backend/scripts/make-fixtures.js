'use strict';
// Writes cross-implementation fixtures: card URLs and grants produced by this
// backend, which the Android core tests (and later iOS tests) must verify with
// their own code. Proves the formats agree across Node, Kotlin and Swift.
// Usage: node scripts/make-fixtures.js ../android/SavvyRDAndroid/core/src/test/resources/fixtures.json

const fs = require('node:fs');
const crypto = require('node:crypto');
const { signCardId, buildCardUrl } = require('../src/crypto/cardToken');
const { issueGrant } = require('../src/crypto/grants');

// Fixed keys so the fixture file is stable across runs (test keys only).
const cardKey = crypto.createPrivateKey({
  key: Buffer.concat([Buffer.from('302e020100300506032b657004220420', 'hex'), Buffer.alloc(32, 1)]),
  format: 'der', type: 'pkcs8',
});
const grantKey = crypto.createPrivateKey({
  key: Buffer.concat([Buffer.from('302e020100300506032b657004220420', 'hex'), Buffer.alloc(32, 2)]),
  format: 'der', type: 'pkcs8',
});
const raw = (k) => crypto.createPublicKey(k).export({ format: 'der', type: 'spki' }).subarray(-32).toString('hex');

const cardCode = '8V1QFQWTY6VG';
const signedUrl = buildCardUrl('go.savvy.test', 'signed', { cardId: cardCode, signature: signCardId(cardKey, cardCode) });
const grant = issueGrant(grantKey, {
  grant_id: '00000000-0000-4000-8000-000000000001', commitment_id: 42, device_id: 7, action: 'release',
  reason: 'card_nfc', issued_at: '2026-09-25T09:00:00.000Z', expires_at: '2026-09-25T09:05:00.000Z',
});
const pauseGrant = issueGrant(grantKey, {
  grant_id: '00000000-0000-4000-8000-000000000002', commitment_id: 42, device_id: 7, action: 'pause',
  reason: 'emergency_exit', issued_at: '2026-09-25T09:00:00.000Z', expires_at: '2026-09-25T09:05:00.000Z',
  pause_until: '2026-09-25T09:15:00.000Z',
});

const out = {
  card_public_key_raw_hex: raw(cardKey),
  grant_public_key_raw_hex: raw(grantKey),
  bound_card_code: cardCode,
  signed_card_url: signedUrl,
  forged_card_url: signedUrl.replace(cardCode, 'ZZZZZZZZZZZZ'),
  static_card_url: `https://go.savvy.test/c/${cardCode}`,
  sun_card_url: 'https://go.savvy.test/c/s/8V1QFQWTY6VG?e=EF963FF7828658A599F3041510671E88&c=94EED9EE65337086',
  grant, pause_grant: pauseGrant,
  grant_now_valid: '2026-09-25T09:01:00.000Z',
  grant_now_expired: '2026-09-25T09:06:00.000Z',
};
const target = process.argv[2];
if (target) fs.writeFileSync(target, `${JSON.stringify(out, null, 2)}\n`);
else console.log(JSON.stringify(out, null, 2));
