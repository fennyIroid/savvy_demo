'use strict';
// Express app for the Savvy R&D backend. POC auth: each device registers and
// receives a bearer token. Production replaces this with real account auth.

const crypto = require('node:crypto');
const express = require('express');
const { buildConfig } = require('./config');
const { createMemoryStore } = require('./store/memoryStore');
const { createCardService } = require('./services/cardService');
const { createCommitmentService } = require('./services/commitmentService');
const { createInsightsService } = require('./services/insightsService');
const { createFamilyService } = require('./services/familyService');
const { createSyncService } = require('./services/syncService');
const { createUsageService } = require('./services/usageService');
const { ApiError } = require('./services/errors');
const { generateCardId, signCardId, buildCardUrl } = require('./crypto/cardToken');

function createApp({ config = buildConfig(), store = createMemoryStore(), clock = { now: () => Date.now() } } = {}) {
  const cards = createCardService({ store, config, clock });
  const commitments = createCommitmentService({ store, config, clock, cardService: cards });
  const insights = createInsightsService({ store, clock });
  const family = createFamilyService({ store, config, clock });
  const sync = createSyncService({ store, config, clock, cardService: cards });
  const usage = createUsageService({ store, clock, family });
  const iso = () => new Date(clock.now()).toISOString();

  const app = express();
  app.use(express.json({ limit: '64kb' }));

  const wrap = (fn) => (req, res, next) => {
    try { const out = fn(req, res); if (out !== undefined) res.json(out); } catch (e) { next(e); }
  };

  function auth(req, _res, next) {
    const token = (req.get('authorization') || '').replace(/^Bearer /, '');
    const device = token && store.devices.find((d) => d.token_hash === hash(token));
    if (!device) return next(new ApiError(401, 'unauthorized'));
    req.device = device;
    req.user = store.users.get(device.user_id);
    return next();
  }
  const hash = (t) => crypto.createHash('sha256').update(t).digest('hex');

  // Server time lets the app detect a manipulated device clock.
  app.get('/v1/time', (_req, res) => res.json({ server_time: iso() }));

  // Public key the apps embed to verify unlock grants and signed cards offline.
  app.get('/v1/keys', (_req, res) => res.json({
    grant_public_key: config.grantPublicKey.export({ format: 'der', type: 'spki' }).toString('base64'),
    card_public_key: config.cardPublicKey.export({ format: 'der', type: 'spki' }).toString('base64'),
  }));

  app.post('/v1/devices/register', wrap((req) => {
    const { email, platform, role = 'self' } = req.body || {};
    if (!email || !['ios', 'android'].includes(platform)) throw new ApiError(422, 'email_and_platform_required');
    let user = store.users.find((u) => u.email === email);
    if (!user) user = store.users.insert({ email, role }, iso());
    const token = crypto.randomBytes(24).toString('base64url');
    const device = store.devices.insert({
      user_id: user.id, platform, token_hash: hash(token), last_seen_at: null,
      authorization_status: null, applied_rule_version: null, shield_active: false,
    }, iso());
    // Reinstall path: the app asks for the active commitment right after login.
    return { user_id: user.id, device_id: device.id, device_token: token };
  }));

  app.post('/v1/cards/register', auth, wrap((req) => cards.registerCard({
    userId: req.user.id, deviceId: req.device.id, payload: req.body.payload, source: req.body.source || 'nfc',
  })));

  app.post('/v1/cards/verify', auth, wrap((req) => cards.verifyForUser({
    userId: req.user.id, deviceId: req.device.id, payload: req.body.payload, source: req.body.source || 'nfc',
  })));

  // Live proof of presence: phone relays APDUs between NTAG 424 DNA card and server.
  app.post('/v1/cards/live/start', auth, wrap((req) => cards.startLive({ userId: req.user.id, cardCode: req.body.card_code })));
  app.post('/v1/cards/live/step', auth, wrap((req) => cards.stepLive({
    userId: req.user.id, sessionId: req.body.session_id, response: req.body.response,
  })));
  app.post('/v1/cards/live/finish', auth, wrap((req) => cards.finishLive({
    userId: req.user.id, deviceId: req.device.id, sessionId: req.body.session_id, response: req.body.response,
  })));

  app.post('/v1/commitments', auth, wrap((req, res) => {
    const b = req.body || {};
    const c = commitments.start({
      userId: req.user.id, deviceId: req.device.id, mode: b.mode, durationMinutes: b.duration_minutes,
      unlockPolicy: b.unlock_policy, taskRef: b.task_ref, selectionRef: b.selection_ref,
    });
    res.status(201);
    return c;
  }));

  // Called on every launch, after reinstall and after reboot to restore state.
  app.get('/v1/commitments/active', auth, wrap((req) => ({
    server_time: iso(), ...commitments.restoreForDevice(req.device),
  })));

  app.post('/v1/commitments/:id/release', auth, wrap((req) => commitments.release({
    userId: req.user.id, deviceId: req.device.id, commitmentId: req.params.id,
    method: req.body.method, payload: req.body.payload, source: req.body.source || 'nfc',
    presenceToken: req.body.presence_token,
  })));

  app.post('/v1/commitments/:id/emergency-exit', auth, wrap((req) => commitments.emergencyExit({
    userId: req.user.id, commitmentId: req.params.id, reason: req.body.reason,
  })));

  // Replays actions the device took while offline (see syncService.js).
  app.post('/v1/sync', auth, wrap((req) => sync.sync({
    userId: req.user.id, deviceId: req.device.id, events: req.body.events,
  })));

  app.get('/v1/emergency-exits/usage', auth, wrap((req) => commitments.emergencyUsage(req.user.id)));

  app.get('/v1/insights/summary', auth, wrap((req) => insights.summary(req.user.id, req.query.tz || 'UTC')));

  app.post('/v1/devices/heartbeat', auth, wrap((req) => {
    family.heartbeat({
      deviceId: req.device.id, authorizationStatus: req.body.authorization_status,
      appliedRuleVersion: req.body.applied_rule_version, shieldActive: req.body.shield_active,
      protections: req.body, pushToken: req.body.push_token,
    });
    return { ok: true, server_time: iso() };
  }));

  app.post('/v1/family/link-codes', auth, wrap((req) => family.createLinkCode({ parentUserId: req.user.id })));
  app.post('/v1/family/link', auth, wrap((req) => family.redeemLinkCode({
    childUserId: req.user.id, childDeviceId: req.device.id, code: req.body.code,
  })));
  app.put('/v1/family/children/:deviceId/rules', auth, wrap((req) => family.putRules({
    parentUserId: req.user.id, childDeviceId: req.params.deviceId, rules: req.body.rules,
  })));
  app.get('/v1/family/children/:deviceId/status', auth, wrap((req) => family.childStatus({
    parentUserId: req.user.id, childDeviceId: req.params.deviceId,
  })));
  app.get('/v1/family/rules', auth, wrap((req) => family.pullRules({
    childDeviceId: req.device.id, sinceVersion: Number(req.query.since_version || 0),
  })));
  app.get('/v1/family/children', auth, wrap((req) => ({ children: family.listChildren({ parentUserId: req.user.id }) })));
  app.put('/v1/family/inventory', auth, wrap((req) => family.putInventory({ childDeviceId: req.device.id, apps: req.body.apps })));
  app.get('/v1/family/children/:deviceId/inventory', auth, wrap((req) => family.getInventory({
    parentUserId: req.user.id, childDeviceId: req.params.deviceId,
  })));
  app.put('/v1/usage/daily', auth, wrap((req) => {
    if (req.device.platform !== 'android') throw new ApiError(422, 'usage_upload_android_only');
    return usage.putDaily({ userId: req.user.id, deviceId: req.device.id, date: req.body.date, apps: req.body.apps });
  }));
  app.get('/v1/usage/daily', auth, wrap((req) => ({ days: usage.forDevice(req.device.id, Number(req.query.days || 7)) })));
  app.get('/v1/family/children/:deviceId/usage', auth, wrap((req) => usage.childUsage({
    parentUserId: req.user.id, childDeviceId: req.params.deviceId, days: Number(req.query.days || 7),
  })));
  app.post('/v1/family/rules/ack', auth, wrap((req) => family.ackRules({
    childDeviceId: req.device.id, version: req.body.version,
  })));

  if (config.devEndpoints) {
    // Creates test cards so testers (and E2E tests) can write them to NFC tags / print QR.
    app.post('/v1/dev/cards', wrap((req) => {
      const format = req.body.format === 'static' ? 'static' : 'signed';
      const cardId = generateCardId();
      cards.manufactureCard({ cardCode: cardId, format });
      const url = buildCardUrl(config.cardDomains[0], format, {
        cardId, signature: format === 'signed' ? signCardId(config.cardSigningKey, cardId) : undefined,
      });
      return { card_code: cardId, url };
    }));
  }

  app.use((_req, res) => res.status(404).json({ error: 'not_found' }));

  // eslint-disable-next-line no-unused-vars
  app.use((err, _req, res, _next) => {
    const status = err.status || 500;
    if (status === 500) console.error(err);
    res.status(status).json({ error: err.code || 'internal_error' });
  });

  return { app, services: { cards, commitments, insights, family }, store, config };
}

module.exports = { createApp };
