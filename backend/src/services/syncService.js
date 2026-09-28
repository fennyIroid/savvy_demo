'use strict';
// Offline event sync. Devices keep enforcing without network and queue what
// happened; this endpoint replays the queue. Rules:
//  - Device timestamps are untrusted: they are clamped to [now - 48 h, now].
//  - Offline actions already happened on the device and cannot be undone, so an
//    action that breaks policy (for example an emergency exit over the limit) is
//    recorded with a violation flag instead of being rejected silently.
//  - Idempotent: each event carries an event_id; replays return the stored result.

const { ApiError } = require('./errors');

const MAX_BACKDATE_MS = 48 * 3600 * 1000;

function createSyncService({ store, config, clock, cardService }) {
  const iso = (ms) => new Date(ms).toISOString();
  const seen = new Map(); // event_id -> result (POC; production: sync_events table)

  function clampTime(at) {
    const now = clock.now();
    const t = Date.parse(at);
    if (Number.isNaN(t)) return now;
    return Math.min(now, Math.max(now - MAX_BACKDATE_MS, t));
  }

  function findCommitment(userId, deviceId, ev) {
    if (ev.server_id) {
      const c = store.commitments.get(ev.server_id);
      if (c && c.user_id === userId) return c;
    }
    if (ev.local_id) return store.commitments.find((c) => c.device_id === deviceId && c.local_id === ev.local_id);
    return null;
  }

  function endCommitment(c, atMs, status, reason, outcome) {
    const endMs = Math.min(atMs, Date.parse(c.ends_at));
    store.commitments.update(c.id, { status, ended_at: iso(endMs), end_reason: reason }, iso(clock.now()));
    store.focus_sessions.insert({
      user_id: c.user_id, device_id: c.device_id, commitment_id: c.id, mode: c.mode,
      started_at: c.started_at, ended_at: iso(endMs), outcome,
      focused_seconds: Math.max(0, Math.round((endMs - Date.parse(c.started_at)) / 1000)),
    }, iso(clock.now()));
  }

  function apply(userId, deviceId, ev) {
    const atMs = clampTime(ev.at);
    switch (ev.type) {
      case 'commitment_started': {
        const existing = findCommitment(userId, deviceId, ev);
        if (existing) return { server_id: existing.id };
        const endsMs = atMs + ev.minutes * 60000;
        const row = store.commitments.insert({
          user_id: userId, device_id: deviceId, local_id: ev.local_id, mode: ev.mode, unlock_policy: ev.policy,
          task_ref: ev.task_ref || null, selection_ref: ev.selection_ref || null,
          status: endsMs <= clock.now() ? 'completed' : 'active',
          started_at: iso(atMs), ends_at: iso(endsMs), ended_at: null, end_reason: null, created_offline: true,
        }, iso(clock.now()));
        return { server_id: row.id, status: row.status };
      }
      case 'card_release': {
        const c = findCommitment(userId, deviceId, ev);
        if (!c) return { error: 'commitment_not_found' };
        const v = cardService.verifyForUser({ userId, deviceId, payload: ev.payload, source: `${ev.source}_offline` });
        if (c.status === 'active') endCommitment(c, atMs, 'released', 'card_offline', v.valid ? 'released_by_card' : 'ended_early');
        return { server_id: c.id, card_valid: v.valid, violation: v.valid ? null : v.reason };
      }
      case 'task_completed': {
        const c = findCommitment(userId, deviceId, ev);
        if (!c) return { error: 'commitment_not_found' };
        if (c.status === 'active') endCommitment(c, atMs, 'released', 'task_complete_offline', 'task_completed');
        return { server_id: c.id };
      }
      case 'emergency_exit': {
        const c = findCommitment(userId, deviceId, ev);
        if (!c) return { error: 'commitment_not_found' };
        const p = config.emergencyExit;
        const used = store.emergency_exits.filter((e) => e.user_id === userId && e.status === 'used'
          && Date.parse(e.created_at) >= atMs - p.windowDays * 86400000).length;
        const overLimit = used >= p.maxPerWindow;
        store.emergency_exits.insert({
          user_id: userId, device_id: deviceId, commitment_id: c.id, reason: 'offline', status: 'used',
          available_at: iso(atMs), used_at: iso(atMs), offline: true, over_limit: overLimit,
        }, iso(atMs));
        if (c.status === 'active') endCommitment(c, atMs, 'released', 'emergency_exit_offline', 'emergency_exit');
        return { server_id: c.id, violation: overLimit ? 'emergency_exit_limit_exceeded_offline' : null };
      }
      default:
        return { error: 'unknown_event_type' };
    }
  }

  function sync({ userId, deviceId, events }) {
    if (!Array.isArray(events) || events.length > 200) throw new ApiError(422, 'events_required');
    const results = events.map((ev) => {
      if (!ev || !ev.event_id) return { error: 'event_id_required' };
      const key = `${deviceId}:${ev.event_id}`;
      if (!seen.has(key)) seen.set(key, apply(userId, deviceId, ev));
      return { event_id: ev.event_id, ...seen.get(key) };
    });
    return { server_time: iso(clock.now()), results };
  }

  return { sync };
}

module.exports = { createSyncService };
