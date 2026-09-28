'use strict';
// Simulates an NTAG 424 DNA chip producing SUN messages, so tests can create
// fresh taps (new counter) and replays (old URL) without hardware.
const crypto = require('node:crypto');
const { aesCmac } = require('../../src/crypto/cmac');
const { sessionMacKey, truncateMac } = require('../../src/crypto/ntag424');

function createSunChip({ uidHex, metaReadKey, fileReadKey, startCounter = 0 }) {
  let counter = startCounter;
  const uid = Buffer.from(uidHex, 'hex');
  return {
    tap() {
      counter += 1;
      const ctr = Buffer.from([counter & 0xff, (counter >> 8) & 0xff, (counter >> 16) & 0xff]);
      const plain = Buffer.concat([Buffer.from([0xc7]), uid, ctr, crypto.randomBytes(5)]);
      const c = crypto.createCipheriv('aes-128-cbc', metaReadKey, Buffer.alloc(16));
      c.setAutoPadding(false);
      const enc = Buffer.concat([c.update(plain), c.final()]).toString('hex').toUpperCase();
      const mac = truncateMac(aesCmac(sessionMacKey(fileReadKey, uid, ctr), Buffer.alloc(0)))
        .toString('hex').toUpperCase();
      return { enc, mac, counter };
    },
  };
}
module.exports = { createSunChip };
