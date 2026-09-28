'use strict';
// Simulated NTAG 424 DNA card side of AuthenticateEV2First, for tests.
const crypto = require('node:crypto');
const { aesCbc, rotl } = require('../../src/crypto/ntag424Auth');

function createAuthChip(key, keyNo = 3) {
  let rndB = null;
  return {
    /** Takes a C-APDU (Buffer), returns the R-APDU hex (data + SW). */
    transceive(apdu) {
      if (apdu[0] === 0x00 && apdu[1] === 0xa4) return '9000';
      if (apdu[1] === 0x71) {
        if (apdu[5] !== keyNo) return '91AE'; // authentication error
        rndB = crypto.randomBytes(16);
        return `${aesCbc(true, key, rndB).toString('hex')}91AF`;
      }
      if (apdu[1] === 0xaf && rndB) {
        const plain = aesCbc(false, key, apdu.subarray(5, 37));
        const rndA = plain.subarray(0, 16);
        if (!plain.subarray(16, 32).equals(rotl(rndB))) { rndB = null; return '91AE'; }
        rndB = null;
        const ti = crypto.randomBytes(4);
        const caps = Buffer.alloc(12);
        return `${aesCbc(true, key, Buffer.concat([ti, rotl(rndA), caps])).toString('hex')}9100`;
      }
      return '911C'; // illegal command
    },
  };
}
module.exports = { createAuthChip };
