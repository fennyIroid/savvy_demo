'use strict';
// Daily per-app usage totals uploaded by Android (UsageStatsManager).
// iOS cannot upload Screen Time data (DeviceActivityReport is network-sandboxed),
// so iOS devices never call this. Storing and showing this to a parent makes
// Savvy a "monitoring" app under Google Play policy (isMonitoringTool, disclosure).

const { ApiError } = require('./errors');

function createUsageService({ store, clock, family }) {
  const iso = () => new Date(clock.now()).toISOString();

  function putDaily({ userId, deviceId, date, apps }) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(date || '') || !Array.isArray(apps)) throw new ApiError(422, 'date_and_apps_required');
    const existing = store.usage_daily.find((u) => u.device_id === deviceId && u.usage_date === date);
    const rows = apps.filter((a) => a && typeof a.package === 'string' && Number.isFinite(a.seconds) && a.seconds >= 0)
      .map((a) => ({ package: a.package, label: String(a.label || a.package), seconds: Math.round(a.seconds) }));
    const totalSeconds = rows.reduce((s, a) => s + a.seconds, 0);
    if (existing) store.usage_daily.update(existing.id, { apps: rows, total_seconds: totalSeconds }, iso());
    else store.usage_daily.insert({ user_id: userId, device_id: deviceId, usage_date: date, apps: rows, total_seconds: totalSeconds }, iso());
    return { date, total_seconds: totalSeconds };
  }

  function forDevice(deviceId, days) {
    return store.usage_daily.filter((u) => u.device_id === deviceId)
      .sort((a, b) => b.usage_date.localeCompare(a.usage_date)).slice(0, days)
      .map((u) => ({ date: u.usage_date, total_seconds: u.total_seconds, apps: [...u.apps].sort((a, b) => b.seconds - a.seconds) }));
  }

  function childUsage({ parentUserId, childDeviceId, days = 7 }) {
    const link = family.linkForParent(parentUserId, childDeviceId);
    return { days: forDevice(link.child_device_id, days) };
  }

  return { putDaily, childUsage, forDevice };
}

module.exports = { createUsageService };
