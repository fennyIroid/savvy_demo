'use strict';
// NTAG 424 DNA "Secure Unique NFC" (SUN) message verification, following NXP
// AN12196. The card mirrors two values into its NDEF URL on every tap:
//   e = AES-128-CBC(SDMMetaReadKey, IV=0, PICCDataTag | UID | SDMReadCtr | pad)
//   c = truncated CMAC over the dynamic data, keyed by a per-tap session key
// Because the counter increases on every tap and the MAC depends on it, a copied
// URL can be replayed only once and a cloned chip cannot produce new valid URLs
// without the AES key. Verification needs the key, so it runs on the backend.

const crypto = require('node:crypto');
const { aesCmac } = require('./cmac');

function decryptPiccData(metaReadKey, encHex) {
  const enc = Buffer.from(encHex, 'hex');
  if (enc.length !== 16) throw new Error('PICC data must be 16 bytes');
  const decipher = crypto.createDecipheriv('aes-128-cbc', metaReadKey, Buffer.alloc(16));
  decipher.setAutoPadding(false);
  const plain = Buffer.concat([decipher.update(enc), decipher.final()]);

  const tag = plain[0];
  const uidMirrored = (tag & 0x80) !== 0;
  const ctrMirrored = (tag & 0x40) !== 0;
  const uidLength = tag & 0x0f;
  if (!uidMirrored || !ctrMirrored || uidLength !== 7) {
    throw new Error(`Unexpected PICCDataTag 0x${tag.toString(16)}`);
  }
  const uid = plain.subarray(1, 8);
  const ctrBytes = plain.subarray(8, 11);
  const counter = ctrBytes[0] | (ctrBytes[1] << 8) | (ctrBytes[2] << 16);
  return { uid, uidHex: uid.toString('hex').toUpperCase(), ctrBytes, counter };
}

function sessionMacKey(fileReadKey, uid, ctrBytes) {
  // SV2 = 3CC3 0001 0080 || UID (7) || SDMReadCtr (3, LSB first)
  const sv2 = Buffer.concat([Buffer.from('3CC300010080', 'hex'), uid, ctrBytes]);
  return aesCmac(fileReadKey, sv2);
}

function truncateMac(fullMac) {
  // NXP truncation keeps the odd-indexed bytes (1, 3, ... 15) giving 8 bytes.
  const out = Buffer.alloc(8);
  for (let i = 0; i < 8; i += 1) out[i] = fullMac[i * 2 + 1];
  return out;
}

/**
 * Verify a SUN message. `macInput` is the ASCII data between SDMMACInputOffset
 * and SDMMACOffset as configured on the card (empty string when both offsets
 * are equal, which is the configuration Savvy should use).
 */
function verifySun({ metaReadKey, fileReadKey, encPiccHex, macHex, macInput = '' }) {
  const picc = decryptPiccData(metaReadKey, encPiccHex);
  const ksession = sessionMacKey(fileReadKey, picc.uid, picc.ctrBytes);
  const expected = truncateMac(aesCmac(ksession, Buffer.from(macInput, 'ascii')));
  const given = Buffer.from(macHex, 'hex');
  const macValid = given.length === 8 && crypto.timingSafeEqual(expected, given);
  return { macValid, uidHex: picc.uidHex, counter: picc.counter };
}

module.exports = { decryptPiccData, sessionMacKey, truncateMac, verifySun };
