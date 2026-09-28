'use strict';
// Parent / child linking and rule synchronisation.
//
// Platform reality this models (see docs/IOS_FEASIBILITY.md section 14):
// on iOS the Screen Time app tokens are opaque and the shield must be applied on
// the child device by the Savvy app that holds .child authorization there. The
// parent cannot push a ManagedSettings change directly. So the parent edits a
// versioned rule set on the backend, the child app pulls it (silent push +
// periodic reconcile), applies it locally, and acknowledges the version.
// `selection_ref` is an opaque blob created by the picker on the child device.

const crypto = require('node:crypto');
const { ApiError } = require('./errors');

const CODE_TTL_MS = 15 * 60000;

function createFamilyService({ store, config, clock }) {
  const iso = (ms) => new Date(ms).toISOString();

  function createLinkCode({ parentUserId }) {
    const code = crypto.randomInt(0, 1e8).toString().padStart(8, '0');
    const row = store.family_link_codes.insert({
      parent_user_id: parentUserId, code, expires_at: iso(clock.now() + CODE_TTL_MS), used_at: null,
    }, iso(clock.now()));
    return { code: row.code, expires_at: row.expires_at };
  }

  function redeemLinkCode({ childUserId, childDeviceId, code }) {
    const row = store.family_link_codes.find((r) => r.code === code && !r.used_at);
    if (!row || clock.now() > Date.parse(row.expires_at)) throw new ApiError(404, 'invalid_or_expired_code');
    if (row.parent_user_id === childUserId) throw new ApiError(422, 'cannot_link_to_self');
    // MVP: one child profile per parent (multiple children is out of scope).
    if (store.family_links.find((l) => l.parent_user_id === row.parent_user_id && l.status === 'active')) {
      throw new ApiError(409, 'parent_already_has_child');
    }
    store.family_link_codes.update(row.id, { used_at: iso(clock.now()) }, iso(clock.now()));
    const link = store.family_links.insert({
      parent_user_id: row.parent_user_id, child_user_id: childUserId, child_device_id: childDeviceId, status: 'active',
    }, iso(clock.now()));
    return { link_id: link.id };
  }

  function linkForParent(parentUserId, childDeviceId) {
    const link = store.family_links.find((l) => l.parent_user_id === parentUserId
      && l.child_device_id === Number(childDeviceId) && l.status === 'active');
    if (!link) throw new ApiError(404, 'child_not_linked');
    return link;
  }

  function latestRules(childDeviceId) {
    const all = store.child_rule_sets.filter((r) => r.child_device_id === childDeviceId);
    return all.sort((a, b) => b.version - a.version)[0] || null;
  }

  function putRules({ parentUserId, childDeviceId, rules }) {
    const link = linkForParent(parentUserId, childDeviceId);
    const prev = latestRules(link.child_device_id);
    const row = store.child_rule_sets.insert({
      family_link_id: link.id, child_device_id: link.child_device_id,
      version: prev ? prev.version + 1 : 1, rules, applied_at: null,
    }, iso(clock.now()));
    // Wake the child app so it pulls the new rules (silent APNs / FCM data message).
    // POC: outbox row. Production: a worker sends it; the child also pulls on launch
    // and on background refresh, so a lost push only delays the rule.
    const child = store.devices.get(link.child_device_id);
    store.push_outbox.insert({
      device_id: child.id, platform: child.platform, push_token: child.push_token || null,
      kind: 'silent', payload: { reason: 'rules_changed', version: row.version }, sent_at: null,
    }, iso(clock.now()));
    return { version: row.version, push_queued: Boolean(child.push_token) };
  }

  function pullRules({ childDeviceId, sinceVersion = 0 }) {
    const latest = latestRules(childDeviceId);
    if (!latest || latest.version <= sinceVersion) return { changed: false, version: latest ? latest.version : 0 };
    return { changed: true, version: latest.version, rules: latest.rules };
  }

  function ackRules({ childDeviceId, version }) {
    const row = store.child_rule_sets.find((r) => r.child_device_id === childDeviceId && r.version === version);
    if (!row) throw new ApiError(404, 'rule_version_not_found');
    store.child_rule_sets.update(row.id, { applied_at: iso(clock.now()) }, iso(clock.now()));
    return { ok: true };
  }

  // Android reports individual protections (accessibility, usage_access, device_admin,
  // advanced_protection, adb_enabled); iOS reports authorization_status only.
  const MUST_BE_ON = ['accessibility', 'usage_access', 'device_admin'];
  const MUST_BE_OFF = ['advanced_protection', 'adb_enabled'];

  function heartbeat({ deviceId, authorizationStatus, appliedRuleVersion, shieldActive, protections, pushToken }) {
    const known = {};
    for (const k of [...MUST_BE_ON, ...MUST_BE_OFF]) {
      if (typeof protections?.[k] === 'boolean') known[k] = protections[k];
    }
    return store.devices.update(deviceId, {
      last_seen_at: iso(clock.now()),
      authorization_status: authorizationStatus,
      applied_rule_version: appliedRuleVersion ?? null,
      shield_active: Boolean(shieldActive),
      protections: known,
      ...(typeof pushToken === 'string' && pushToken ? { push_token: pushToken.slice(0, 400) } : {}),
    }, iso(clock.now()));
  }

  /** What the parent sees. Detection only: Savvy cannot stop these events on self-use devices. */
  function childStatus({ parentUserId, childDeviceId }) {
    const link = linkForParent(parentUserId, childDeviceId);
    const device = store.devices.get(link.child_device_id);
    const latest = latestRules(link.child_device_id);
    const lastSeen = device.last_seen_at ? Date.parse(device.last_seen_at) : null;
    const flags = [];
    if (device.authorization_status && device.authorization_status !== 'approved') flags.push('authorization_not_approved');
    if (!lastSeen || clock.now() - lastSeen > config.heartbeatStaleSeconds * 1000) flags.push('device_not_reporting');
    if (latest && device.applied_rule_version !== latest.version) flags.push('latest_rules_not_applied');
    const p = device.protections || {};
    for (const k of MUST_BE_ON) if (p[k] === false) flags.push(`${k}_off`);
    for (const k of MUST_BE_OFF) if (p[k] === true) flags.push(`${k}_on`);
    return {
      child_device_id: device.id, platform: device.platform, last_seen_at: device.last_seen_at,
      authorization_status: device.authorization_status, latest_rule_version: latest ? latest.version : 0,
      applied_rule_version: device.applied_rule_version, flags,
    };
  }

  function listChildren({ parentUserId }) {
    return store.family_links.filter((l) => l.parent_user_id === parentUserId && l.status === 'active')
      .map((l) => {
        const d = store.devices.get(l.child_device_id);
        return { child_device_id: d.id, platform: d.platform, linked_at: l.created_at, last_seen_at: d.last_seen_at };
      });
  }

  // Android only: the child device reports launchable apps (real package names),
  // so the parent can choose apps remotely. iOS uses the parent-device picker instead.
  function putInventory({ childDeviceId, apps }) {
    if (!Array.isArray(apps) || apps.length > 1000) throw new ApiError(422, 'apps_required');
    const clean = apps.filter((a) => a && typeof a.package === 'string')
      .map((a) => ({ package: a.package.slice(0, 200), label: String(a.label || a.package).slice(0, 100) }));
    store.devices.update(childDeviceId, { app_inventory: clean, app_inventory_at: iso(clock.now()) }, iso(clock.now()));
    return { count: clean.length };
  }

  function getInventory({ parentUserId, childDeviceId }) {
    const link = linkForParent(parentUserId, childDeviceId);
    const d = store.devices.get(link.child_device_id);
    return { apps: d.app_inventory || [], reported_at: d.app_inventory_at || null };
  }

  return {
    createLinkCode, redeemLinkCode, putRules, pullRules, ackRules, heartbeat, childStatus,
    listChildren, putInventory, getInventory, linkForParent,
  };
}

module.exports = { createFamilyService };
