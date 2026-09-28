'use strict';
// AES-128 CMAC (RFC 4493). Node's crypto module has no built-in CMAC, so it is
// built here from AES-128-ECB. Verified against RFC 4493 test vectors in
// test/cmac.test.js. Used by the NTAG 424 DNA SUN verifier.

const crypto = require('node:crypto');

const BLOCK = 16;
const RB = 0x87;

function aesEcb(key, block) {
  const cipher = crypto.createCipheriv('aes-128-ecb', key, null);
  cipher.setAutoPadding(false);
  return Buffer.concat([cipher.update(block), cipher.final()]);
}

function shiftLeftOne(buf) {
  const out = Buffer.alloc(buf.length);
  let carry = 0;
  for (let i = buf.length - 1; i >= 0; i -= 1) {
    out[i] = ((buf[i] << 1) & 0xff) | carry;
    carry = (buf[i] & 0x80) ? 1 : 0;
  }
  return out;
}

function subkeys(key) {
  const l = aesEcb(key, Buffer.alloc(BLOCK));
  const k1 = shiftLeftOne(l);
  if (l[0] & 0x80) k1[BLOCK - 1] ^= RB;
  const k2 = shiftLeftOne(k1);
  if (k1[0] & 0x80) k2[BLOCK - 1] ^= RB;
  return { k1, k2 };
}

function xor(a, b) {
  const out = Buffer.alloc(a.length);
  for (let i = 0; i < a.length; i += 1) out[i] = a[i] ^ b[i];
  return out;
}

function aesCmac(key, message) {
  if (key.length !== BLOCK) throw new Error('AES-128 key must be 16 bytes');
  const msg = Buffer.from(message);
  const { k1, k2 } = subkeys(key);
  const blockCount = Math.max(1, Math.ceil(msg.length / BLOCK));
  const lastComplete = msg.length > 0 && msg.length % BLOCK === 0;

  let last = msg.subarray((blockCount - 1) * BLOCK);
  if (lastComplete) {
    last = xor(last, k1);
  } else {
    const padded = Buffer.alloc(BLOCK);
    last.copy(padded);
    padded[last.length] = 0x80;
    last = xor(padded, k2);
  }

  let x = Buffer.alloc(BLOCK);
  for (let i = 0; i < blockCount - 1; i += 1) {
    x = aesEcb(key, xor(x, msg.subarray(i * BLOCK, (i + 1) * BLOCK)));
  }
  return aesEcb(key, xor(x, last));
}

module.exports = { aesCmac };
