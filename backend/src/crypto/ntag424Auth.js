'use strict';
// NTAG 424 DNA live proof of presence: server side of AuthenticateEV2First
// (NXP NT4H2421Gx datasheet, section "AuthenticateEV2First"), relayed by the phone.
//
//   phone -> card : 90 71 00 00 02 <KeyNo> 00 00          (wrapped native command)
//   card  -> phone: E(K, RndB)                  + 91 AF
//   server        : RndB = D(K, ...); new random RndA; RndB' = rotl(RndB)
//   phone -> card : 90 AF 00 00 20 E(K, RndA || RndB') 00
//   card  -> phone: E(K, TI || RndA' || PDcap2 || PCDcap2) + 91 00
//   server        : check RndA' == rotl(RndA)
//
// Because RndA is fresh from the server for every attempt, responses recorded
// earlier ("pre-played" taps) are useless, and a clone without the AES key fails.
// E/D = AES-128-CBC with a zero IV (single block for step 1).

const crypto = require('node:crypto');

const AUTH_EV2_FIRST = 0x71;
const ADDITIONAL_FRAME = 0xaf;

function aesCbc(encrypt, key, data) {
  const c = encrypt ? crypto.createCipheriv('aes-128-cbc', key, Buffer.alloc(16))
    : crypto.createDecipheriv('aes-128-cbc', key, Buffer.alloc(16));
  c.setAutoPadding(false);
  return Buffer.concat([c.update(data), c.final()]);
}

const rotl = (b) => Buffer.concat([b.subarray(1), b.subarray(0, 1)]);

/** ISO 7816 SELECT of the NTAG 424 DNA NDEF application (DF name D2760000850101). */
const SELECT_NDEF_APP = Buffer.from('00A4040007D276000085010100', 'hex');

function firstCommand(keyNo) {
  return Buffer.from([0x90, AUTH_EV2_FIRST, 0x00, 0x00, 0x02, keyNo, 0x00, 0x00]);
}

/** Splits an R-APDU into data and status word. */
function splitResponse(hex) {
  const r = Buffer.from(hex, 'hex');
  if (r.length < 2) throw new Error('short_response');
  return { data: r.subarray(0, r.length - 2), sw: r.subarray(r.length - 2).toString('hex').toUpperCase() };
}

/** Step 1 -> returns the second C-APDU and the RndA the server must remember. */
function secondCommand(key, response1Hex) {
  const { data, sw } = splitResponse(response1Hex);
  if (sw !== '91AF' || data.length !== 16) throw new Error(`unexpected_step1_${sw}`);
  const rndB = aesCbc(false, key, data);
  const rndA = crypto.randomBytes(16);
  const enc = aesCbc(true, key, Buffer.concat([rndA, rotl(rndB)]));
  const apdu = Buffer.concat([Buffer.from([0x90, ADDITIONAL_FRAME, 0x00, 0x00, 0x20]), enc, Buffer.from([0x00])]);
  return { apdu, rndA };
}

/** Step 2 -> true when the card proved it knows the key AND saw this RndA. */
function verifyFinal(key, rndA, response2Hex) {
  const { data, sw } = splitResponse(response2Hex);
  if (sw !== '9100' || data.length !== 32) return { ok: false, reason: `unexpected_step2_${sw}` };
  const plain = aesCbc(false, key, data);
  const rndAPrime = plain.subarray(4, 20);
  const ok = crypto.timingSafeEqual(rndAPrime, rotl(rndA));
  return ok ? { ok: true, ti: plain.subarray(0, 4).toString('hex') } : { ok: false, reason: 'rnda_mismatch' };
}

/**
 * POC per-card key derivation from a master key. Production should use NXP AN10922
 * key diversification with keys held in an HSM / KMS, and the vendor must write the
 * same derived key into key slot `keyNo` of each card.
 */
function derivePresenceKey(masterKey, cardCode) {
  return crypto.createHmac('sha256', masterKey).update(`savvy-presence:v1:${cardCode}`).digest().subarray(0, 16);
}

module.exports = {
  SELECT_NDEF_APP, firstCommand, secondCommand, verifyFinal, derivePresenceKey, aesCbc, rotl,
};
