'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const { aesCmac } = require('../src/crypto/cmac');
const { verifySun, decryptPiccData } = require('../src/crypto/ntag424');
const {
  generateCardId, signCardId, verifyCardSignature, buildCardUrl, parseCardPayload,
} = require('../src/crypto/cardToken');
const { issueGrant, verifyGrant } = require('../src/crypto/grants');

const hex = (h) => Buffer.from(h, 'hex');

test('AES-CMAC matches RFC 4493 test vectors', () => {
  const key = hex('2b7e151628aed2a6abf7158809cf4f3c');
  assert.equal(aesCmac(key, Buffer.alloc(0)).toString('hex'), 'bb1d6929e95937287fa37d129b756746');
  assert.equal(aesCmac(key, hex('6bc1bee22e409f96e93d7e117393172a')).toString('hex'),
    '070a16b46b4d4144f79bdd9dd04a287c');
  assert.equal(aesCmac(key, hex('6bc1bee22e409f96e93d7e117393172aae2d8a571e03ac9c9eb76fac45af8e5130c81c46a35ce411')).toString('hex'),
    'dfa66747de9ae63030ca32611497c827');
});

test('NTAG 424 DNA SUN matches NXP AN12196 example (zero keys)', () => {
  const zero = Buffer.alloc(16);
  const picc = decryptPiccData(zero, 'EF963FF7828658A599F3041510671E88');
  assert.equal(picc.uidHex, '04DE5F1EACC040');
  assert.equal(picc.counter, 61);
  const r = verifySun({ metaReadKey: zero, fileReadKey: zero,
    encPiccHex: 'EF963FF7828658A599F3041510671E88', macHex: '94EED9EE65337086' });
  assert.equal(r.macValid, true);
});

test('NTAG 424 DNA SUN rejects a tampered MAC', () => {
  const zero = Buffer.alloc(16);
  const r = verifySun({ metaReadKey: zero, fileReadKey: zero,
    encPiccHex: 'EF963FF7828658A599F3041510671E88', macHex: '94EED9EE65337087' });
  assert.equal(r.macValid, false);
});

test('signed card token verifies and rejects forgery', () => {
  const { publicKey, privateKey } = crypto.generateKeyPairSync('ed25519');
  const cardId = generateCardId();
  const sig = signCardId(privateKey, cardId);
  assert.equal(verifyCardSignature(publicKey, cardId, sig), true);
  const other = generateCardId();
  assert.equal(verifyCardSignature(publicKey, other, sig), false);
  const url = buildCardUrl('go.savvy.test', 'signed', { cardId, signature: sig });
  // The URL must fit NTAG213 user memory (144 bytes) with NDEF overhead (~7 bytes, https:// is 1 byte prefix).
  assert.ok(url.replace('https://', '').length + 7 <= 144, `URL too long: ${url.length}`);
  const parsed = parseCardPayload(url, ['go.savvy.test']);
  assert.deepEqual(parsed, { format: 'signed', cardId, signature: sig });
});

test('parser rejects foreign domains, http and junk', () => {
  const id = generateCardId();
  assert.equal(parseCardPayload(`https://evil.test/c/${id}`, ['go.savvy.test']), null);
  assert.equal(parseCardPayload(`http://go.savvy.test/c/${id}`, ['go.savvy.test']), null);
  assert.equal(parseCardPayload('hello', ['go.savvy.test']), null);
  assert.equal(parseCardPayload('https://go.savvy.test/c/lowercase1234', ['go.savvy.test']), null);
  assert.deepEqual(parseCardPayload(`https://go.savvy.test/c/${id}`, ['go.savvy.test']), { format: 'static', cardId: id });
});

test('grants verify, reject tampering and expire', () => {
  const { publicKey, privateKey } = crypto.generateKeyPairSync('ed25519');
  const now = Date.parse('2026-09-25T10:00:00Z');
  const g = issueGrant(privateKey, { commitment_id: 7, action: 'release', expires_at: '2026-09-25T10:05:00Z' });
  assert.equal(verifyGrant(publicKey, g, now).valid, true);
  const [body, sig] = g.split('.');
  const forgedBody = Buffer.from(JSON.stringify({ commitment_id: 8, action: 'release', expires_at: '2026-09-25T10:05:00Z' })).toString('base64url');
  assert.equal(verifyGrant(publicKey, `${forgedBody}.${sig}`, now).valid, false);
  assert.equal(verifyGrant(publicKey, `${body}.${sig}`, now + 10 * 60000).reason, 'expired');
});
