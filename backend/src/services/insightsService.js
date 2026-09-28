'use strict';
// Focus time and streaks. Plain Savvy data, not Screen Time data, so it can be
// stored on the backend on both platforms.

const QUALIFYING = new Set(['completed', 'released_by_card', 'task_completed']);

function dayKey(ms, timeZone) {
  // en-CA formats as YYYY-MM-DD
  return new Intl.DateTimeFormat('en-CA', { timeZone, year: 'numeric', month: '2-digit', day: '2-digit' })
    .format(new Date(ms));
}

function previousDay(key) {
  const d = new Date(`${key}T12:00:00Z`);
  d.setUTCDate(d.getUTCDate() - 1);
  return d.toISOString().slice(0, 10);
}

/** Current streak = consecutive local days with at least one qualifying session, ending today or yesterday. */
function computeStreak(sessions, nowMs, timeZone) {
  const days = new Set(sessions.filter((s) => QUALIFYING.has(s.outcome))
    .map((s) => dayKey(Date.parse(s.ended_at), timeZone)));
  let cursor = dayKey(nowMs, timeZone);
  if (!days.has(cursor)) cursor = previousDay(cursor);
  let streak = 0;
  while (days.has(cursor)) { streak += 1; cursor = previousDay(cursor); }
  return streak;
}

function createInsightsService({ store, clock }) {
  function summary(userId, timeZone) {
    const sessions = store.focus_sessions.filter((s) => s.user_id === userId);
    const today = dayKey(clock.now(), timeZone);
    const todaySeconds = sessions.filter((s) => dayKey(Date.parse(s.ended_at), timeZone) === today)
      .reduce((a, s) => a + s.focused_seconds, 0);
    const byOutcome = {};
    for (const s of sessions) byOutcome[s.outcome] = (byOutcome[s.outcome] || 0) + 1;
    return {
      streak_days: computeStreak(sessions, clock.now(), timeZone),
      focus_seconds_today: todaySeconds,
      focus_seconds_total: sessions.reduce((a, s) => a + s.focused_seconds, 0),
      sessions_by_outcome: byOutcome,
    };
  }
  return { summary };
}

module.exports = { createInsightsService, computeStreak, dayKey };
