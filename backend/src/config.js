'use strict';
// POC configuration. Keys are generated per process when not provided. Production
// must load keys from a secrets manager (AWS Secrets Manager / KMS), never from code.

const crypto = require('node:crypto');

function loadKeyPair(privPem) {
  if (privPem) {
    const privateKey = crypto.createPrivateKey(privPem);
    return { privateKey, publicKey: crypto.createPublicKey(privateKey) };
  }
  return crypto.generateKeyPairSync('ed25519');
}

function buildConfig(overrides = {}) {
  const cardKeys = loadKeyPair(process.env.SAVVY_CARD_SIGNING_KEY);
  const grantKeys = loadKeyPair(process.env.SAVVY_GRANT_SIGNING_KEY);
  return {
    cardDomains: (process.env.SAVVY_CARD_DOMAINS || 'go.savvy.test').split(','),
    cardSigningKey: cardKeys.privateKey,
    cardPublicKey: cardKeys.publicKey,
    grantSigningKey: grantKeys.privateKey,
    grantPublicKey: grantKeys.publicKey,
    // NTAG 424 DNA keys. Production: per-card keys diversified from a master key (NXP AN10922).
    sunMetaReadKey: Buffer.from(process.env.SAVVY_SUN_META_KEY || '00'.repeat(16), 'hex'),
    sunFileReadKey: Buffer.from(process.env.SAVVY_SUN_FILE_KEY || '00'.repeat(16), 'hex'),
    // Live proof of presence (NTAG 424 DNA AuthenticateEV2First relayed by the phone).
    presenceMasterKey: Buffer.from(process.env.SAVVY_PRESENCE_MASTER_KEY || '11'.repeat(16), 'hex'),
    presenceKeyNo: 3,
    // When true, SUN cards must use the live challenge to end a commitment
    // (the plain SUN URL alone can be pre-harvested). Off by default for the POC.
    requireLiveProofForSunCards: false,
    liveSessionTtlSeconds: 30,
    presenceTokenTtlSeconds: 60,
    grantTtlSeconds: 300,
    // Emergency exit policy. Product rule is still open, so every value is configurable.
    emergencyExit: {
      maxPerWindow: 2,
      windowDays: 7,
      coolingOffSeconds: 0, // for example 600 = user must wait 10 minutes after requesting
      action: 'release', // 'release' ends the commitment, 'pause' unlocks for pauseMinutes
      pauseMinutes: 15,
    },
    heartbeatStaleSeconds: 30 * 60,
    // Dev-only helpers (card factory for device and E2E tests). Never enable in production.
    devEndpoints: process.env.SAVVY_DEV === '1',
    ...overrides,
  };
}

module.exports = { buildConfig };
