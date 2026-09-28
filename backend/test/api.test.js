'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { startHarness } = require('./helpers/harness');
const { createSunChip } = require('./helpers/sunChip');
const { generateCardId, signCardId, buildCardUrl } = require('../src/crypto/cardToken');
const { verifyGrant } = require('../src/crypto/grants');

function makeSignedCard(h) {
  const cardId = generateCardId();
  h.services.cards.manufactureCard({ cardCode: cardId, format: 'signed' });
  return buildCardUrl('go.savvy.test', 'signed', { cardId, signature: signCardId(h.config.cardSigningKey, cardId) });
}

function makeSunCard(h, uidHex = '04A1B2C3D4E5F6') {
  const cardId = generateCardId();
  h.services.cards.manufactureCard({ cardCode: cardId, format: 'sun', chipUid: uidHex });
  const chip = createSunChip({ uidHex, metaReadKey: h.config.sunMetaReadKey, fileReadKey: h.config.sunFileReadKey });
  const tapUrl = () => { const t = chip.tap(); return buildCardUrl('go.savvy.test', 'sun', { cardId, encPiccHex: t.enc, macHex: t.mac }); };
  return { cardId, tapUrl };
}

test('dev card factory only exists when devEndpoints is enabled', async (t) => {
  const off = await startHarness();
  t.after(off.close);
  assert.equal((await off.call('POST', '/v1/dev/cards', {})).status, 404);
  const on = await startHarness({ devEndpoints: true });
  t.after(on.close);
  const r = (await on.call('POST', '/v1/dev/cards', {})).body;
  const alice = await on.device('alice@test');
  assert.equal((await on.call('POST', '/v1/cards/register', { payload: r.url }, alice.token)).status, 200);
});

test('card binding: one account per card, one card per account, wrong/forged cards rejected', async (t) => {
  const h = await startHarness();
  t.after(h.close);
  const alice = await h.device('alice@test');
  const bob = await h.device('bob@test');
  const card = makeSignedCard(h);
  const card2 = makeSignedCard(h);

  assert.equal((await h.call('POST', '/v1/cards/register', { payload: card }, alice.token)).status, 200);
  assert.equal((await h.call('POST', '/v1/cards/register', { payload: card }, bob.token)).body.error, 'card_bound_to_another_account');
  assert.equal((await h.call('POST', '/v1/cards/register', { payload: card2 }, alice.token)).body.error, 'account_already_has_card');

  const ok = await h.call('POST', '/v1/cards/verify', { payload: card, source: 'qr' }, alice.token);
  assert.deepEqual([ok.body.valid, ok.body.proof_of_presence], [true, false]);
  assert.equal((await h.call('POST', '/v1/cards/verify', { payload: card }, bob.token)).body.reason, 'card_not_owned_by_user');

  // Forged card: valid-looking id with a signature copied from another card.
  const sig = card.split('.').pop();
  const forged = `https://go.savvy.test/c/1.${generateCardId()}.${sig}`;
  assert.equal((await h.call('POST', '/v1/cards/verify', { payload: forged }, alice.token)).body.reason, 'unknown_card');
  // Random NFC tag / random URL.
  assert.equal((await h.call('POST', '/v1/cards/verify', { payload: 'https://example.com/hello' }, alice.token)).body.reason, 'not_a_savvy_card');
});

test('copied QR / cloned static payload is accepted: identity is proven, physical possession is not', async (t) => {
  const h = await startHarness();
  t.after(h.close);
  const alice = await h.device('alice@test');
  const card = makeSignedCard(h);
  await h.call('POST', '/v1/cards/register', { payload: card }, alice.token);
  // A screenshot of the QR yields the exact same string, so it verifies. Documented limitation.
  const copy = String(card);
  const r = await h.call('POST', '/v1/cards/verify', { payload: copy, source: 'qr' }, alice.token);
  assert.equal(r.body.valid, true);
  assert.equal(r.body.proof_of_presence, false);
});

test('NTAG 424 DNA SUN: fresh taps pass, replayed URL is rejected, cloned UID without key fails', async (t) => {
  const h = await startHarness();
  t.after(h.close);
  const alice = await h.device('alice@test');
  const sun = makeSunCard(h);
  const first = sun.tapUrl();
  assert.equal((await h.call('POST', '/v1/cards/register', { payload: first }, alice.token)).status, 200);
  const tap2 = sun.tapUrl();
  const ok = await h.call('POST', '/v1/cards/verify', { payload: tap2 }, alice.token);
  assert.deepEqual([ok.body.valid, ok.body.proof_of_presence], [true, true]);
  // Someone photographed / sniffed tap2 and replays it.
  assert.equal((await h.call('POST', '/v1/cards/verify', { payload: tap2 }, alice.token)).body.reason, 'replayed_sun_message');
  // A cloned chip with the same UID but without the AES key cannot produce a valid MAC.
  const fake = createSunChip({ uidHex: '04A1B2C3D4E5F6', metaReadKey: h.config.sunMetaReadKey, fileReadKey: Buffer.alloc(16, 7), startCounter: 99 }).tap();
  const cardId = first.split('/c/s/')[1].split('?')[0];
  const fakeUrl = `https://go.savvy.test/c/s/${cardId}?e=${fake.enc}&c=${fake.mac}`;
  assert.equal((await h.call('POST', '/v1/cards/verify', { payload: fakeUrl }, alice.token)).body.reason, 'bad_sun_mac');
});

test('6-hour card_required commitment: no early end without card, card releases with verifiable grant', async (t) => {
  const h = await startHarness();
  t.after(h.close);
  const alice = await h.device('alice@test');
  const card = makeSignedCard(h);
  await h.call('POST', '/v1/cards/register', { payload: card }, alice.token);
  const c = (await h.call('POST', '/v1/commitments', { mode: 'study', duration_minutes: 360, unlock_policy: 'card_required' }, alice.token)).body;
  assert.equal(c.ends_at, '2026-09-25T15:00:00.000Z');

  assert.equal((await h.call('POST', `/v1/commitments/${c.id}/release`, { method: 'user' }, alice.token)).body.error, 'early_end_requires_card');
  const other = makeSignedCard(h);
  assert.equal((await h.call('POST', `/v1/commitments/${c.id}/release`, { method: 'card', payload: other }, alice.token)).status, 403);

  h.advance(60);
  const rel = await h.call('POST', `/v1/commitments/${c.id}/release`, { method: 'card', payload: card, source: 'nfc' }, alice.token);
  assert.equal(rel.body.status, 'released');
  const g = verifyGrant(h.config.grantPublicKey, rel.body.grant, h.clock.now());
  assert.equal(g.valid, true);
  assert.equal(g.payload.commitment_id, c.id);
  assert.equal(g.payload.device_id, alice.device_id);
});

test('24-hour locked commitment: card cannot end it, device clock changes are irrelevant, server expiry releases', async (t) => {
  const h = await startHarness();
  t.after(h.close);
  const alice = await h.device('alice@test');
  const card = makeSignedCard(h);
  await h.call('POST', '/v1/cards/register', { payload: card }, alice.token);
  const c = (await h.call('POST', '/v1/commitments', { mode: 'work', duration_minutes: 1440, unlock_policy: 'locked' }, alice.token)).body;
  assert.equal((await h.call('POST', `/v1/commitments/${c.id}/release`, { method: 'card', payload: card }, alice.token)).body.error, 'locked_commitment_no_early_unlock');
  // The client never sends its own time, so a user moving the phone clock forward changes nothing server side.
  h.advance(23 * 60 + 59);
  assert.equal((await h.call('GET', '/v1/commitments/active', null, alice.token)).body.commitment.id, c.id);
  h.advance(1);
  assert.equal((await h.call('GET', '/v1/commitments/active', null, alice.token)).body.commitment, null);
});

test('reinstall: a new install on the same account can restore the active commitment state', async (t) => {
  const h = await startHarness();
  t.after(h.close);
  const first = await h.device('alice@test');
  const c = (await h.call('POST', '/v1/commitments', { mode: 'sleep', duration_minutes: 480, unlock_policy: 'card_required' }, first.token)).body;
  // App deleted and reinstalled: a new install registers as a new device on the same account.
  const second = await h.device('alice@test');
  assert.equal(second.user_id, first.user_id);
  h.advance(30);
  const r = (await h.call('GET', '/v1/commitments/active', null, second.token)).body;
  assert.equal(r.commitment.id, c.id);
  assert.equal(r.restored_from_previous_install, true);
  assert.equal(r.commitment.ends_at, c.ends_at); // the reinstall did not reset or shorten the timer
  // The adopted commitment still cannot be ended without the card.
  assert.equal((await h.call('POST', `/v1/commitments/${c.id}/release`, { method: 'user' }, second.token)).body.error, 'early_end_requires_card');
  // An Android install on the same account does not adopt an iOS commitment.
  const android = await h.device('alice@test', 'android');
  assert.equal((await h.call('GET', '/v1/commitments/active', null, android.token)).body.commitment, null);
});

test('task-based restriction: completion releases, card_required task also needs the card', async (t) => {
  const h = await startHarness();
  t.after(h.close);
  const alice = await h.device('alice@test');
  const card = makeSignedCard(h);
  await h.call('POST', '/v1/cards/register', { payload: card }, alice.token);
  const free = (await h.call('POST', '/v1/commitments', { mode: 'task', task_ref: 'task-1', duration_minutes: 30, unlock_policy: 'free' }, alice.token)).body;
  assert.equal((await h.call('POST', `/v1/commitments/${free.id}/release`, { method: 'task_complete' }, alice.token)).body.status, 'released');
  const strict = (await h.call('POST', '/v1/commitments', { mode: 'task', task_ref: 'task-2', duration_minutes: 30, unlock_policy: 'card_required' }, alice.token)).body;
  assert.equal((await h.call('POST', `/v1/commitments/${strict.id}/release`, { method: 'task_complete' }, alice.token)).status, 403);
  assert.equal((await h.call('POST', `/v1/commitments/${strict.id}/release`, { method: 'task_complete', payload: card }, alice.token)).body.status, 'released');
  // 30-minute task that is not completed simply expires.
  const timed = (await h.call('POST', '/v1/commitments', { mode: 'task', task_ref: 'task-3', duration_minutes: 30, unlock_policy: 'card_required' }, alice.token)).body;
  h.advance(30);
  const rel = await h.call('POST', `/v1/commitments/${timed.id}/release`, { method: 'task_complete' }, alice.token);
  assert.equal(rel.body.status, 'completed');
});

test('emergency exit: limited per window, tracked, cooling-off enforced by server time', async (t) => {
  const h = await startHarness({ emergencyExit: { maxPerWindow: 2, windowDays: 7, coolingOffSeconds: 600, action: 'release', pauseMinutes: 15 } });
  t.after(h.close);
  const alice = await h.device('alice@test');
  const start = () => h.call('POST', '/v1/commitments', { mode: 'study', duration_minutes: 360, unlock_policy: 'locked' }, alice.token);

  let c = (await start()).body;
  let r = await h.call('POST', `/v1/commitments/${c.id}/emergency-exit`, { reason: 'family emergency' }, alice.token);
  assert.equal(r.body.status, 'pending');
  r = await h.call('POST', `/v1/commitments/${c.id}/emergency-exit`, {}, alice.token);
  assert.equal(r.body.status, 'pending'); // still cooling off; retrying does not create a second request
  h.advance(10);
  r = await h.call('POST', `/v1/commitments/${c.id}/emergency-exit`, {}, alice.token);
  assert.equal(r.body.status, 'released');

  c = (await start()).body;
  await h.call('POST', `/v1/commitments/${c.id}/emergency-exit`, {}, alice.token);
  h.advance(10);
  assert.equal((await h.call('POST', `/v1/commitments/${c.id}/emergency-exit`, {}, alice.token)).body.status, 'released');

  c = (await start()).body;
  assert.equal((await h.call('POST', `/v1/commitments/${c.id}/emergency-exit`, {}, alice.token)).body.error, 'emergency_exit_limit_reached');
  assert.deepEqual((await h.call('GET', '/v1/emergency-exits/usage', null, alice.token)).body, { used: 2, limit: 2, window_days: 7 });
  h.advance(7 * 24 * 60);
  // window rolled; commitment (6h) has long expired, so start a new one
  c = (await start()).body;
  assert.equal((await h.call('POST', `/v1/commitments/${c.id}/emergency-exit`, {}, alice.token)).body.status, 'pending');
});

test('emergency exit in pause mode returns a time-boxed grant', async (t) => {
  const h = await startHarness({ emergencyExit: { maxPerWindow: 1, windowDays: 1, coolingOffSeconds: 0, action: 'pause', pauseMinutes: 15 } });
  t.after(h.close);
  const alice = await h.device('alice@test');
  const c = (await h.call('POST', '/v1/commitments', { mode: 'work', duration_minutes: 120, unlock_policy: 'locked' }, alice.token)).body;
  const r = await h.call('POST', `/v1/commitments/${c.id}/emergency-exit`, {}, alice.token);
  const g = verifyGrant(h.config.grantPublicKey, r.body.grant, h.clock.now());
  assert.equal(g.payload.action, 'pause');
  assert.equal(g.payload.pause_until, '2026-09-25T09:15:00.000Z');
  assert.equal((await h.call('GET', '/v1/commitments/active', null, alice.token)).body.commitment.id, c.id);
});

test('focus time and streaks by local day', async (t) => {
  const h = await startHarness();
  t.after(h.close);
  const alice = await h.device('alice@test');
  for (let day = 0; day < 3; day += 1) {
    await h.call('POST', '/v1/commitments', { mode: 'study', duration_minutes: 60, unlock_policy: 'free' }, alice.token);
    h.advance(60);
    await h.call('GET', '/v1/commitments/active', null, alice.token); // settles expiry
    h.advance(23 * 60);
  }
  const s = (await h.call('GET', '/v1/insights/summary?tz=Asia/Kolkata', null, alice.token)).body;
  assert.equal(s.streak_days, 3);
  assert.equal(s.focus_seconds_total, 3 * 3600);
  h.advance(48 * 60);
  assert.equal((await h.call('GET', '/v1/insights/summary?tz=Asia/Kolkata', null, alice.token)).body.streak_days, 0);
});

test('parent-child: link, versioned rules, child pulls and acks, tamper flags visible to parent', async (t) => {
  const h = await startHarness();
  t.after(h.close);
  const parent = await h.device('parent@test');
  const child = await h.device('child@test');
  const code = (await h.call('POST', '/v1/family/link-codes', {}, parent.token)).body.code;
  assert.equal((await h.call('POST', '/v1/family/link', { code }, child.token)).status, 200);
  assert.equal((await h.call('POST', '/v1/family/link', { code }, child.token)).status, 404); // single use

  const rules = { selection_ref: 'opaque-blob-from-child-picker', focus: { mode: 'study', duration_minutes: 120, unlock_policy: 'card_required' } };
  assert.equal((await h.call('PUT', `/v1/family/children/${child.device_id}/rules`, { rules }, parent.token)).body.version, 1);
  // Bob is not the parent.
  const bob = await h.device('bob@test');
  assert.equal((await h.call('PUT', `/v1/family/children/${child.device_id}/rules`, { rules }, bob.token)).status, 404);

  // No push token yet: the rule still waits for the child's next pull.
  assert.equal(h.store.push_outbox.filter((p) => p.device_id === child.device_id).length, 1);
  await h.call('POST', '/v1/devices/heartbeat', { authorization_status: 'approved', push_token: 'apns-abc' }, child.token);
  const second = (await h.call('PUT', `/v1/family/children/${child.device_id}/rules`, { rules }, parent.token)).body;
  assert.deepEqual(second, { version: 2, push_queued: true });
  assert.equal(h.store.push_outbox.filter((p) => p.push_token === 'apns-abc').length, 1);
  let pull = (await h.call('GET', '/v1/family/rules?since_version=0', null, child.token)).body;
  assert.deepEqual([pull.changed, pull.version], [true, 2]);
  await h.call('POST', '/v1/devices/heartbeat', { authorization_status: 'approved', applied_rule_version: 2, shield_active: true }, child.token);
  await h.call('POST', '/v1/family/rules/ack', { version: 2 }, child.token);
  let status = (await h.call('GET', `/v1/family/children/${child.device_id}/status`, null, parent.token)).body;
  assert.deepEqual(status.flags, []);

  await h.call('PUT', `/v1/family/children/${child.device_id}/rules`, { rules: { ...rules, focus: null } }, parent.token);
  pull = (await h.call('GET', '/v1/family/rules?since_version=2', null, child.token)).body;
  assert.equal(pull.version, 3);
  // Child goes offline or revokes Screen Time access: the parent can see it, not prevent it.
  await h.call('POST', '/v1/devices/heartbeat', { authorization_status: 'denied', applied_rule_version: 2 }, child.token);
  h.advance(45);
  status = (await h.call('GET', `/v1/family/children/${child.device_id}/status`, null, parent.token)).body;
  assert.deepEqual(status.flags.sort(), ['authorization_not_approved', 'device_not_reporting', 'latest_rules_not_applied']);
});

test('android parent mode: disabled protections are flagged to the parent', async (t) => {
  const h = await startHarness();
  t.after(h.close);
  const parent = await h.device('parent@test', 'android');
  const child = await h.device('child@test', 'android');
  const code = (await h.call('POST', '/v1/family/link-codes', {}, parent.token)).body.code;
  await h.call('POST', '/v1/family/link', { code }, child.token);
  await h.call('POST', '/v1/devices/heartbeat', {
    authorization_status: 'approved', accessibility: false, usage_access: true, device_admin: false,
    advanced_protection: true, adb_enabled: true, unknown_field: true,
  }, child.token);
  const status = (await h.call('GET', `/v1/family/children/${child.device_id}/status`, null, parent.token)).body;
  assert.deepEqual(status.flags.sort(), ['accessibility_off', 'adb_enabled_on', 'advanced_protection_on', 'device_admin_off']);
});

test('offline sync: offline start, offline card release, over-limit emergency exit, idempotent replay, clamped time', async (t) => {
  const h = await startHarness({ emergencyExit: { maxPerWindow: 1, windowDays: 7, coolingOffSeconds: 0, action: 'release', pauseMinutes: 15 } });
  t.after(h.close);
  const alice = await h.device('alice@test');
  const card = makeSignedCard(h);
  await h.call('POST', '/v1/cards/register', { payload: card }, alice.token);
  const at = (min) => new Date(h.clock.now() + min * 60000).toISOString();

  const events = [
    { event_id: 'e1', type: 'commitment_started', local_id: 'L1', mode: 'study', minutes: 120, policy: 'card_required', at: at(-30) },
    { event_id: 'e2', type: 'card_release', local_id: 'L1', payload: card, source: 'nfc', at: at(-10) },
    { event_id: 'e3', type: 'commitment_started', local_id: 'L2', mode: 'work', minutes: 60, policy: 'locked', at: at(-5) },
    { event_id: 'e4', type: 'emergency_exit', local_id: 'L2', at: at(-2) },
    { event_id: 'e5', type: 'commitment_started', local_id: 'L3', mode: 'work', minutes: 60, policy: 'locked', at: at(-1) },
    { event_id: 'e6', type: 'emergency_exit', local_id: 'L3', at: at(-1) },
    { event_id: 'e7', type: 'commitment_started', local_id: 'L4', mode: 'sleep', minutes: 60, policy: 'free', at: at(60 * 24 * 10) },
  ];
  const r = (await h.call('POST', '/v1/sync', { events }, alice.token)).body;
  const byId = Object.fromEntries(r.results.map((x) => [x.event_id, x]));
  assert.equal(byId.e2.card_valid, true);
  assert.equal(byId.e4.violation, null);
  assert.equal(byId.e6.violation, 'emergency_exit_limit_exceeded_offline');
  // Future timestamp (clock moved forward) is clamped to server now.
  const l4 = h.store.commitments.get(byId.e7.server_id);
  assert.equal(l4.started_at, new Date(h.clock.now()).toISOString());
  // Replaying the same queue changes nothing.
  const again = (await h.call('POST', '/v1/sync', { events }, alice.token)).body;
  assert.deepEqual(again.results, r.results);
  assert.equal(h.store.commitments.filter((c) => c.user_id === alice.user_id).length, 4);
  const s = (await h.call('GET', '/v1/insights/summary', null, alice.token)).body;
  assert.equal(s.sessions_by_outcome.released_by_card, 1);
  assert.equal(s.sessions_by_outcome.emergency_exit, 2);
});

test('parent lists children, sees Android app inventory and daily usage; iOS usage upload refused', async (t) => {
  const h = await startHarness();
  t.after(h.close);
  const parent = await h.device('parent@test', 'ios');
  const child = await h.device('child@test', 'android');
  const code = (await h.call('POST', '/v1/family/link-codes', {}, parent.token)).body.code;
  await h.call('POST', '/v1/family/link', { code }, child.token);
  assert.deepEqual((await h.call('GET', '/v1/family/children', null, parent.token)).body.children.map((c) => c.platform), ['android']);

  await h.call('PUT', '/v1/family/inventory', { apps: [{ package: 'com.instagram.android', label: 'Instagram' }, { bad: 1 }] }, child.token);
  const inv = (await h.call('GET', `/v1/family/children/${child.device_id}/inventory`, null, parent.token)).body;
  assert.deepEqual(inv.apps, [{ package: 'com.instagram.android', label: 'Instagram' }]);

  // Parent chooses from the inventory and sends real package names as the rule.
  await h.call('PUT', `/v1/family/children/${child.device_id}/rules`, { rules: { packages: ['com.instagram.android'], focus: null } }, parent.token);
  assert.deepEqual((await h.call('GET', '/v1/family/rules', null, child.token)).body.rules.packages, ['com.instagram.android']);

  await h.call('PUT', '/v1/usage/daily', { date: '2026-09-25', apps: [{ package: 'com.instagram.android', label: 'Instagram', seconds: 1800 }, { package: 'x', seconds: 600 }] }, child.token);
  await h.call('PUT', '/v1/usage/daily', { date: '2026-09-25', apps: [{ package: 'com.instagram.android', label: 'Instagram', seconds: 2400 }] }, child.token);
  const u = (await h.call('GET', `/v1/family/children/${child.device_id}/usage`, null, parent.token)).body;
  assert.equal(u.days.length, 1);
  assert.equal(u.days[0].total_seconds, 2400); // same day re-upload replaces
  // A stranger cannot read it.
  const bob = await h.device('bob@test');
  assert.equal((await h.call('GET', `/v1/family/children/${child.device_id}/usage`, null, bob.token)).status, 404);
  // iOS devices cannot upload usage (Apple keeps Screen Time data on device).
  assert.equal((await h.call('PUT', '/v1/usage/daily', { date: '2026-09-25', apps: [] }, parent.token)).body.error, 'usage_upload_android_only');
});

test('NTAG 424 DNA live proof: relay succeeds, clone and pre-played responses fail, token single-use, policy enforced', async (t) => {
  const { createAuthChip } = require('./helpers/ntag424Chip');
  const { derivePresenceKey } = require('../src/crypto/ntag424Auth');
  const h = await startHarness({ requireLiveProofForSunCards: true });
  t.after(h.close);
  const alice = await h.device('alice@test');
  const sun = makeSunCard(h);
  await h.call('POST', '/v1/cards/register', { payload: sun.tapUrl() }, alice.token);
  const realChip = createAuthChip(derivePresenceKey(h.config.presenceMasterKey, sun.cardId), 3);

  async function relay(chip) {
    const s = (await h.call('POST', '/v1/cards/live/start', { card_code: sun.cardId }, alice.token)).body;
    let last;
    for (const apdu of s.apdus) last = chip.transceive(Buffer.from(apdu, 'hex'));
    const step = await h.call('POST', '/v1/cards/live/step', { session_id: s.session_id, response: last }, alice.token);
    if (step.status !== 200) return { status: step.status, body: step.body, session: s.session_id };
    const r2 = chip.transceive(Buffer.from(step.body.apdu, 'hex'));
    const fin = await h.call('POST', '/v1/cards/live/finish', { session_id: s.session_id, response: r2 }, alice.token);
    return { ...fin, r2, session: s.session_id };
  }

  const c = (await h.call('POST', '/v1/commitments', { mode: 'study', duration_minutes: 360, unlock_policy: 'card_required' }, alice.token)).body;
  // With requireLiveProofForSunCards, a plain SUN tap is no longer enough.
  const plain = await h.call('POST', `/v1/commitments/${c.id}/release`, { method: 'card', payload: sun.tapUrl() }, alice.token);
  assert.equal(plain.body.error, 'live_proof_required');

  // Clone: right protocol, wrong key.
  const clone = await relay(createAuthChip(Buffer.alloc(16, 9), 3));
  assert.equal(clone.status, 403);

  // Real card.
  const ok = await relay(realChip);
  assert.equal(ok.status, 200);
  const token = ok.body.presence_token;

  // Pre-played response: replaying the card's earlier final answer into a new session fails (new RndA).
  const s2 = (await h.call('POST', '/v1/cards/live/start', { card_code: sun.cardId }, alice.token)).body;
  let r1;
  for (const apdu of s2.apdus) r1 = realChip.transceive(Buffer.from(apdu, 'hex'));
  await h.call('POST', '/v1/cards/live/step', { session_id: s2.session_id, response: r1 }, alice.token);
  const replay = await h.call('POST', '/v1/cards/live/finish', { session_id: s2.session_id, response: ok.r2 }, alice.token);
  assert.equal(replay.status, 403);
  // Finished sessions cannot be reused.
  assert.equal((await h.call('POST', '/v1/cards/live/finish', { session_id: ok.session, response: ok.r2 }, alice.token)).status, 404);

  const rel = await h.call('POST', `/v1/commitments/${c.id}/release`, { method: 'card', presence_token: token, source: 'nfc_live' }, alice.token);
  assert.equal(rel.body.status, 'released');
  assert.equal(rel.body.proof_of_presence, true);
  // Token is single use.
  const c2 = (await h.call('POST', '/v1/commitments', { mode: 'study', duration_minutes: 60, unlock_policy: 'card_required' }, alice.token)).body;
  assert.equal((await h.call('POST', `/v1/commitments/${c2.id}/release`, { method: 'card', presence_token: token }, alice.token)).body.error, 'invalid_presence_token');

  // Session expiry (30 s).
  const s3 = (await h.call('POST', '/v1/cards/live/start', { card_code: sun.cardId }, alice.token)).body;
  h.clock.t += 31000;
  assert.equal((await h.call('POST', '/v1/cards/live/step', { session_id: s3.session_id, response: '00'.repeat(16) + '91AF' }, alice.token)).body.error, 'live_session_expired');
  // Signed (non-424) cards cannot start a live session.
  const bob = await h.device('bob@test');
  const signed = makeSignedCard(h);
  await h.call('POST', '/v1/cards/register', { payload: signed }, bob.token);
  const code = signed.split('/c/1.')[1].split('.')[0];
  assert.equal((await h.call('POST', '/v1/cards/live/start', { card_code: code }, bob.token)).body.error, 'card_does_not_support_live_proof');
});
