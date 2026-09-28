'use strict';
// Card registration and verification. One card per account for MVP (multiple
// cards per account is out of scope). A card can be bound to only one account.

const { parseCardPayload, verifyCardSignature } = require('../crypto/cardToken');
const crypto = require('node:crypto');
const { verifySun } = require('../crypto/ntag424');
const live = require('../crypto/ntag424Auth');
const { ApiError } = require('./errors');

function createCardService({ store, config, clock }) {
  const nowIso = () => new Date(clock.now()).toISOString();

  /** Checks the payload itself. Does not look at ownership. */
  function authenticatePayload(raw) {
    const parsed = parseCardPayload(raw, config.cardDomains);
    if (!parsed) return { ok: false, reason: 'not_a_savvy_card' };
    const card = store.cards.find((c) => c.card_code === parsed.cardId);
    if (!card) return { ok: false, reason: 'unknown_card' };
    if (card.status === 'revoked') return { ok: false, reason: 'card_revoked' };
    if (card.format !== parsed.format) return { ok: false, reason: 'format_mismatch' };

    if (parsed.format === 'signed'
      && !verifyCardSignature(config.cardPublicKey, parsed.cardId, parsed.signature)) {
      return { ok: false, reason: 'bad_signature' };
    }
    let proofOfPresence = false;
    if (parsed.format === 'sun') {
      const r = verifySun({
        metaReadKey: config.sunMetaReadKey,
        fileReadKey: config.sunFileReadKey,
        encPiccHex: parsed.encPiccHex,
        macHex: parsed.macHex,
      });
      if (!r.macValid) return { ok: false, reason: 'bad_sun_mac' };
      if (card.chip_uid && card.chip_uid !== r.uidHex) return { ok: false, reason: 'uid_mismatch' };
      if (r.counter <= (card.last_sun_counter ?? -1)) return { ok: false, reason: 'replayed_sun_message' };
      store.cards.update(card.id, { last_sun_counter: r.counter }, nowIso());
      proofOfPresence = true; // fresh counter + valid MAC = the real chip was just tapped
    }
    return { ok: true, card, parsed, proofOfPresence };
  }

  function logScan(card, userId, deviceId, source, result) {
    store.card_scans.insert({
      card_id: card ? card.id : null, user_id: userId, device_id: deviceId, source, result,
    }, nowIso());
  }

  function registerCard({ userId, deviceId, payload, source }) {
    const auth = authenticatePayload(payload);
    if (!auth.ok) {
      logScan(null, userId, deviceId, source, auth.reason);
      throw new ApiError(422, auth.reason);
    }
    const { card } = auth;
    if (card.user_id && card.user_id !== userId) throw new ApiError(409, 'card_bound_to_another_account');
    const existing = store.cards.find((c) => c.user_id === userId && c.status === 'active' && c.id !== card.id);
    if (existing) throw new ApiError(409, 'account_already_has_card');
    store.cards.update(card.id, { user_id: userId, status: 'active', bound_at: nowIso() }, nowIso());
    logScan(card, userId, deviceId, source, 'registered');
    return { card_id: card.id, card_code: card.card_code, format: card.format };
  }

  // ---- Live proof of presence (NTAG 424 DNA) -------------------------------
  const liveSessions = new Map();   // session_id -> state (POC; production: Redis with TTL)
  const presenceTokens = new Map(); // token -> { userId, cardId, expiresMs }

  function startLive({ userId, cardCode }) {
    const card = store.cards.find((c) => c.card_code === cardCode && c.user_id === userId && c.status === 'active');
    if (!card) throw new ApiError(404, 'card_not_owned_by_user');
    if (card.format !== 'sun') throw new ApiError(422, 'card_does_not_support_live_proof');
    const id = crypto.randomUUID();
    liveSessions.set(id, { userId, cardId: card.id, cardCode, step: 1, rndA: null,
      expiresMs: clock.now() + config.liveSessionTtlSeconds * 1000 });
    return {
      session_id: id,
      apdus: [live.SELECT_NDEF_APP.toString('hex'), live.firstCommand(config.presenceKeyNo).toString('hex')],
    };
  }

  function liveSession(userId, id, step) {
    const s = liveSessions.get(id);
    if (!s || s.userId !== userId) throw new ApiError(404, 'live_session_not_found');
    if (clock.now() > s.expiresMs) { liveSessions.delete(id); throw new ApiError(410, 'live_session_expired'); }
    if (s.step !== step) throw new ApiError(409, 'live_session_wrong_step');
    return s;
  }

  function stepLive({ userId, sessionId, response }) {
    const s = liveSession(userId, sessionId, 1);
    const key = live.derivePresenceKey(config.presenceMasterKey, s.cardCode);
    try {
      const { apdu, rndA } = live.secondCommand(key, response);
      Object.assign(s, { step: 2, rndA });
      return { apdu: apdu.toString('hex') };
    } catch (e) {
      liveSessions.delete(sessionId);
      throw new ApiError(422, e.message);
    }
  }

  function finishLive({ userId, deviceId, sessionId, response }) {
    const s = liveSession(userId, sessionId, 2);
    liveSessions.delete(sessionId); // single use, success or not
    const key = live.derivePresenceKey(config.presenceMasterKey, s.cardCode);
    const r = live.verifyFinal(key, s.rndA, response);
    const card = store.cards.get(s.cardId);
    logScan(card, userId, deviceId, 'nfc_live', r.ok ? 'ok' : r.reason);
    if (!r.ok) throw new ApiError(403, r.reason);
    const token = crypto.randomBytes(24).toString('base64url');
    presenceTokens.set(token, { userId, cardId: s.cardId, expiresMs: clock.now() + config.presenceTokenTtlSeconds * 1000 });
    return { presence_token: token, expires_in: config.presenceTokenTtlSeconds };
  }

  function consumePresenceToken(userId, token) {
    const t = presenceTokens.get(token);
    presenceTokens.delete(token);
    if (!t || t.userId !== userId || clock.now() > t.expiresMs) return null;
    return store.cards.get(t.cardId);
  }

  /** Verifies that the scanned card is the card bound to this user. */
  function verifyForUser({ userId, deviceId, payload, source, presenceToken }) {
    if (presenceToken) {
      const card = consumePresenceToken(userId, presenceToken);
      if (!card || card.status !== 'active') return { valid: false, reason: 'invalid_presence_token' };
      return { valid: true, card_id: card.id, format: card.format, proof_of_presence: true, live: true };
    }
    const auth = authenticatePayload(payload);
    if (!auth.ok) {
      logScan(null, userId, deviceId, source, auth.reason);
      return { valid: false, reason: auth.reason };
    }
    if (auth.card.user_id !== userId) {
      logScan(auth.card, userId, deviceId, source, 'card_not_owned');
      return { valid: false, reason: 'card_not_owned_by_user' };
    }
    logScan(auth.card, userId, deviceId, source, 'ok');
    return {
      valid: true,
      card_id: auth.card.id,
      format: auth.card.format,
      // Only SUN cards prove the physical chip was present. Static and signed
      // payloads (and every QR) prove identity only and can be copied.
      proof_of_presence: auth.proofOfPresence,
    };
  }

  function manufactureCard({ cardCode, format, chipUid = null }) {
    return store.cards.insert({
      card_code: cardCode, format, chip_uid: chipUid, status: 'unassigned',
      user_id: null, last_sun_counter: null, bound_at: null, batch_ref: 'poc',
    }, nowIso());
  }

  return { registerCard, verifyForUser, manufactureCard, authenticatePayload, startLive, stepLive, finishLive };
}

module.exports = { createCardService };
