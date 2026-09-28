'use strict';
// Unlock grants. When the backend approves a release (card verified, task done,
// timer expired, emergency exit) it returns an Ed25519-signed grant. The mobile
// app and its extensions verify the grant with the embedded public key before
// removing shields, so a tampered local request cannot forge a release.

const crypto = require('node:crypto');

function canonical(payload) {
  const keys = Object.keys(payload).sort();
  return JSON.stringify(payload, keys);
}

function issueGrant(privateKey, payload) {
  const body = Buffer.from(canonical(payload), 'utf8').toString('base64url');
  const sig = crypto.sign(null, Buffer.from(body, 'utf8'), privateKey).toString('base64url');
  return `${body}.${sig}`;
}

function verifyGrant(publicKey, grant, nowMs) {
  const [body, sig] = String(grant).split('.');
  if (!body || !sig) return { valid: false, reason: 'malformed' };
  const ok = crypto.verify(null, Buffer.from(body, 'utf8'), publicKey, Buffer.from(sig, 'base64url'));
  if (!ok) return { valid: false, reason: 'bad_signature' };
  const payload = JSON.parse(Buffer.from(body, 'base64url').toString('utf8'));
  if (payload.expires_at && Date.parse(payload.expires_at) < nowMs) {
    return { valid: false, reason: 'expired', payload };
  }
  return { valid: true, payload };
}

module.exports = { issueGrant, verifyGrant };
