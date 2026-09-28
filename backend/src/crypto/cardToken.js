'use strict';
// Savvy card payload formats. The same payload is written to the NFC chip as an
// NDEF URI record and printed as the QR code on the same card, so NFC and QR
// share one card identity.
//
//   Option A  static id          https://<domain>/c/<cardId>
//   Option B  Ed25519 signed id  https://<domain>/c/1.<cardId>.<sig>
//   Option D  NTAG 424 DNA SUN   https://<domain>/c/s/<cardId>?e=<picc>&c=<mac>
//
// Option B lets the mobile app verify authenticity offline with the public key
// only. Option D proves the physical chip was tapped (per-tap counter + MAC)
// but needs the backend (or a per-card key) to verify. Option C (account and
// device binding) is enforced by the card service on top of every format.

const crypto = require('node:crypto');

const CARD_ID_ALPHABET = '0123456789ABCDEFGHJKMNPQRSTVWXYZ'; // Crockford base32
const CARD_ID_LENGTH = 12; // 60 bits of randomness

function generateCardId() {
  const bytes = crypto.randomBytes(CARD_ID_LENGTH);
  let id = '';
  for (let i = 0; i < CARD_ID_LENGTH; i += 1) id += CARD_ID_ALPHABET[bytes[i] & 31];
  return id;
}

function isValidCardId(id) {
  return typeof id === 'string' && id.length === CARD_ID_LENGTH
    && [...id].every((ch) => CARD_ID_ALPHABET.includes(ch));
}

function signedMessage(cardId) {
  return Buffer.from(`savvy-card:v1:${cardId}`, 'utf8');
}

function signCardId(privateKey, cardId) {
  return crypto.sign(null, signedMessage(cardId), privateKey).toString('base64url');
}

function verifyCardSignature(publicKey, cardId, sigB64) {
  try {
    return crypto.verify(null, signedMessage(cardId), publicKey, Buffer.from(sigB64, 'base64url'));
  } catch {
    return false;
  }
}

function buildCardUrl(domain, format, { cardId, signature, encPiccHex, macHex }) {
  switch (format) {
    case 'static': return `https://${domain}/c/${cardId}`;
    case 'signed': return `https://${domain}/c/1.${cardId}.${signature}`;
    case 'sun': return `https://${domain}/c/s/${cardId}?e=${encPiccHex}&c=${macHex}`;
    default: throw new Error(`Unknown card format ${format}`);
  }
}

/** Parse a scanned NFC/QR payload. Returns null for anything that is not a Savvy card. */
function parseCardPayload(raw, allowedDomains) {
  let url;
  try {
    url = new URL(String(raw).trim());
  } catch {
    return null;
  }
  if (url.protocol !== 'https:' || !allowedDomains.includes(url.hostname)) return null;
  const parts = url.pathname.split('/').filter(Boolean);
  if (parts[0] !== 'c') return null;

  if (parts.length === 3 && parts[1] === 's' && isValidCardId(parts[2])) {
    const e = url.searchParams.get('e');
    const c = url.searchParams.get('c');
    if (!/^[0-9A-Fa-f]{32}$/.test(e || '') || !/^[0-9A-Fa-f]{16}$/.test(c || '')) return null;
    return { format: 'sun', cardId: parts[2], encPiccHex: e, macHex: c };
  }
  if (parts.length === 2) {
    const seg = parts[1];
    const signed = /^1\.([0-9A-Z]{12})\.([A-Za-z0-9_-]{80,90})$/.exec(seg);
    if (signed && isValidCardId(signed[1])) {
      return { format: 'signed', cardId: signed[1], signature: signed[2] };
    }
    if (isValidCardId(seg)) return { format: 'static', cardId: seg };
  }
  return null;
}

module.exports = {
  generateCardId, isValidCardId, signCardId, verifyCardSignature, buildCardUrl, parseCardPayload,
};
