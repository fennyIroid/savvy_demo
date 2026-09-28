'use strict';
// Generates a batch of card payloads to send to the card vendor for encoding.
// Output CSV columns: card_code, format, ndef_url (also the QR content).
// For NTAG 424 DNA the vendor configures SDM so the chip itself generates e and c;
// this script then only outputs the URL template.
//
// Usage: SAVVY_CARD_SIGNING_KEY="$(cat card_key.pem)" node scripts/provision-cards.js signed 10

const crypto = require('node:crypto');
const { generateCardId, signCardId, buildCardUrl } = require('../src/crypto/cardToken');

const format = process.argv[2] || 'signed';
const count = Number(process.argv[3] || 5);
const domain = process.env.SAVVY_CARD_DOMAIN || 'go.savvy.test';
const key = process.env.SAVVY_CARD_SIGNING_KEY
  ? crypto.createPrivateKey(process.env.SAVVY_CARD_SIGNING_KEY)
  : crypto.generateKeyPairSync('ed25519').privateKey;

console.log('card_code,format,ndef_url');
for (let i = 0; i < count; i += 1) {
  const cardId = generateCardId();
  let url;
  if (format === 'sun') url = `https://${domain}/c/s/${cardId}?e={PICCData}&c={SDMMAC}`;
  else url = buildCardUrl(domain, format, { cardId, signature: format === 'signed' ? signCardId(key, cardId) : undefined });
  console.log(`${cardId},${format},${url}`);
}
