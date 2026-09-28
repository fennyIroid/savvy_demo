'use strict';
// Commitment / focus / task restriction state. The server is the source of truth
// for start and end times, so changing the phone's clock cannot shorten a
// commitment that the server knows about, and reinstalling the app can restore
// the active commitment. The device still enforces locally (DeviceActivity on
// iOS, the blocking service on Android) so enforcement works offline.

const crypto = require('node:crypto');
const { issueGrant } = require('../crypto/grants');
const { ApiError } = require('./errors');

const MODES = ['study', 'work', 'sleep', 'custom', 'task'];
// card_required: ending early needs the bound Savvy card (NFC or QR).
// free:          user can end early in the app (basic focus, no card).
// locked:        no early end at all except the limited emergency exit.
const UNLOCK_POLICIES = ['card_required', 'free', 'locked'];

function createCommitmentService({ store, config, clock, cardService }) {
  const now = () => clock.now();
  const iso = (ms) => new Date(ms).toISOString();

  function grantFor(commitment, action, reason, extra = {}) {
    const issued = now();
    return issueGrant(config.grantSigningKey, {
      grant_id: crypto.randomUUID(),
      commitment_id: commitment.id,
      device_id: commitment.device_id,
      action,
      reason,
      issued_at: iso(issued),
      expires_at: iso(issued + config.grantTtlSeconds * 1000),
      ...extra,
    });
  }

  function recordFocus(c, outcome, endedAtMs) {
    store.focus_sessions.insert({
      user_id: c.user_id, device_id: c.device_id, commitment_id: c.id, mode: c.mode,
      started_at: c.started_at, ended_at: iso(endedAtMs), outcome,
      focused_seconds: Math.max(0, Math.round((endedAtMs - Date.parse(c.started_at)) / 1000)),
    }, iso(now()));
  }

  /** Lazily completes commitments whose end time has passed (server clock). */
  function settle(c) {
    if (c && c.status === 'active' && now() >= Date.parse(c.ends_at)) {
      store.commitments.update(c.id, { status: 'completed', ended_at: c.ends_at, end_reason: 'expired' }, iso(now()));
      recordFocus(c, 'completed', Date.parse(c.ends_at));
    }
    return c;
  }

  function activeForDevice(deviceId) {
    const c = store.commitments.find((x) => x.device_id === deviceId && x.status === 'active');
    settle(c);
    return c && c.status === 'active' ? c : null;
  }

  /**
   * Launch / reinstall restore. If this install has no active commitment but the
   * account has one from an earlier install on the same platform, this install
   * adopts it. The app then re-applies the shield locally (after the user grants
   * Screen Time / Accessibility again). This does NOT stop deletion; it only
   * makes deleting and reinstalling Savvy useless as a way to end a commitment.
   */
  function restoreForDevice(device) {
    const own = activeForDevice(device.id);
    if (own) return { commitment: own, restored_from_previous_install: false };
    const candidates = store.commitments.filter((x) => x.user_id === device.user_id && x.status === 'active');
    for (const c of candidates) {
      settle(c);
      const prevDevice = store.devices.get(c.device_id);
      if (c.status === 'active' && prevDevice && prevDevice.platform === device.platform) {
        store.commitments.update(c.id, { device_id: device.id }, iso(now()));
        return { commitment: c, restored_from_previous_install: true };
      }
    }
    return { commitment: null, restored_from_previous_install: false };
  }

  function start({ userId, deviceId, mode, durationMinutes, unlockPolicy, taskRef = null, selectionRef = null }) {
    if (!MODES.includes(mode)) throw new ApiError(422, 'invalid_mode');
    if (!UNLOCK_POLICIES.includes(unlockPolicy)) throw new ApiError(422, 'invalid_unlock_policy');
    if (!Number.isInteger(durationMinutes) || durationMinutes < 1 || durationMinutes > 24 * 60) {
      throw new ApiError(422, 'invalid_duration');
    }
    if (mode === 'task' && !taskRef) throw new ApiError(422, 'task_ref_required');
    if (activeForDevice(deviceId)) throw new ApiError(409, 'commitment_already_active');
    const startMs = now();
    return store.commitments.insert({
      user_id: userId, device_id: deviceId, mode, unlock_policy: unlockPolicy,
      task_ref: taskRef, selection_ref: selectionRef, status: 'active',
      started_at: iso(startMs), ends_at: iso(startMs + durationMinutes * 60000),
      ended_at: null, end_reason: null,
    }, iso(startMs));
  }

  function getOwned(userId, id) {
    const c = settle(store.commitments.get(id));
    if (!c || c.user_id !== userId) throw new ApiError(404, 'commitment_not_found');
    return c;
  }

  function end(c, status, reason, outcome) {
    const t = now();
    store.commitments.update(c.id, { status, ended_at: iso(t), end_reason: reason }, iso(t));
    recordFocus(c, outcome, t);
  }

  /** Early or on-time release. Returns a signed grant the device uses to remove shields. */
  function release({ userId, deviceId, commitmentId, method, payload, source, presenceToken }) {
    const c = getOwned(userId, commitmentId);
    if (c.status === 'completed') return { status: c.status, grant: grantFor(c, 'release', 'expired') };
    if (c.status !== 'active') throw new ApiError(409, 'commitment_not_active');

    if (method === 'card') {
      if (c.unlock_policy === 'locked') throw new ApiError(403, 'locked_commitment_no_early_unlock');
      const v = cardService.verifyForUser({ userId, deviceId, payload, source, presenceToken });
      if (!v.valid) throw new ApiError(403, v.reason);
      if (config.requireLiveProofForSunCards && v.format === 'sun' && !v.live) {
        throw new ApiError(403, 'live_proof_required');
      }
      end(c, 'released', `card_${source}`, 'released_by_card');
      return { status: 'released', proof_of_presence: v.proof_of_presence, grant: grantFor(c, 'release', `card_${source}`) };
    }
    if (method === 'task_complete') {
      if (c.mode !== 'task') throw new ApiError(422, 'not_a_task_commitment');
      if (c.unlock_policy === 'card_required') {
        // Marking a task done is only a tap in the app, so a card-protected task
        // also needs the card. Product decision; see docs/OPEN_ITEMS.md.
        const v = cardService.verifyForUser({ userId, deviceId, payload, source });
        if (!v.valid) throw new ApiError(403, v.reason);
      }
      end(c, 'released', 'task_complete', 'task_completed');
      return { status: 'released', grant: grantFor(c, 'release', 'task_complete') };
    }
    if (method === 'user') {
      if (c.unlock_policy !== 'free') throw new ApiError(403, 'early_end_requires_card');
      end(c, 'released', 'user_ended', 'ended_early');
      return { status: 'released', grant: grantFor(c, 'release', 'user_ended') };
    }
    throw new ApiError(422, 'invalid_release_method');
  }

  function emergencyExit({ userId, commitmentId, reason }) {
    const c = getOwned(userId, commitmentId);
    if (c.status !== 'active') throw new ApiError(409, 'commitment_not_active');
    const p = config.emergencyExit;
    const t = now();
    const windowStart = t - p.windowDays * 86400000;
    const used = store.emergency_exits.filter((e) => e.user_id === userId
      && e.status !== 'cancelled' && Date.parse(e.created_at) >= windowStart);
    // Pending request for this commitment: confirm it once the cooling-off period is over.
    const pending = used.find((e) => e.commitment_id === c.id && e.status === 'pending');
    if (pending) {
      if (t < Date.parse(pending.available_at)) {
        return { status: 'pending', available_at: pending.available_at };
      }
      return grantEmergency(c, pending);
    }
    if (used.length >= p.maxPerWindow) {
      throw new ApiError(429, 'emergency_exit_limit_reached');
    }
    const req = store.emergency_exits.insert({
      user_id: userId, device_id: c.device_id, commitment_id: c.id, reason: reason || null,
      status: 'pending', available_at: iso(t + p.coolingOffSeconds * 1000), used_at: null,
    }, iso(t));
    if (p.coolingOffSeconds > 0) return { status: 'pending', available_at: req.available_at, remaining: p.maxPerWindow - used.length - 1 };
    return grantEmergency(c, req);
  }

  function grantEmergency(c, req) {
    const p = config.emergencyExit;
    const t = now();
    store.emergency_exits.update(req.id, { status: 'used', used_at: iso(t) }, iso(t));
    if (p.action === 'pause') {
      const pauseUntil = iso(t + p.pauseMinutes * 60000);
      return { status: 'paused', grant: grantFor(c, 'pause', 'emergency_exit', { pause_until: pauseUntil }) };
    }
    end(c, 'released', 'emergency_exit', 'emergency_exit');
    return { status: 'released', grant: grantFor(c, 'release', 'emergency_exit') };
  }

  function emergencyUsage(userId) {
    const p = config.emergencyExit;
    const windowStart = now() - p.windowDays * 86400000;
    const used = store.emergency_exits.filter((e) => e.user_id === userId
      && e.status === 'used' && Date.parse(e.created_at) >= windowStart).length;
    return { used, limit: p.maxPerWindow, window_days: p.windowDays };
  }

  return { start, release, emergencyExit, emergencyUsage, activeForDevice, restoreForDevice, getOwned, MODES, UNLOCK_POLICIES };
}

module.exports = { createCommitmentService };
