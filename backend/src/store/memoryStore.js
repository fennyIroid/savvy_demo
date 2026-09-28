'use strict';
// In-memory store that mirrors db/schema.sql (PostgreSQL). Integer auto-increment
// ids, snake_case columns, created_at/updated_at on every row. Swapping this for a
// PostgreSQL repository does not change the services.

class Table {
  constructor() { this.rows = new Map(); this.seq = 0; }
  insert(row, now) {
    this.seq += 1;
    const full = { id: this.seq, ...row, created_at: now, updated_at: now };
    this.rows.set(full.id, full);
    return full;
  }
  get(id) { return this.rows.get(Number(id)) || null; }
  find(pred) { for (const r of this.rows.values()) if (pred(r)) return r; return null; }
  filter(pred) { return [...this.rows.values()].filter(pred); }
  update(id, patch, now) {
    const row = this.get(id);
    if (!row) return null;
    Object.assign(row, patch, { updated_at: now });
    return row;
  }
}

function createMemoryStore() {
  return {
    users: new Table(),
    devices: new Table(),
    cards: new Table(),
    card_scans: new Table(),
    commitments: new Table(),
    emergency_exits: new Table(),
    focus_sessions: new Table(),
    family_link_codes: new Table(),
    family_links: new Table(),
    child_rule_sets: new Table(),
    usage_daily: new Table(),
    push_outbox: new Table(),
  };
}

module.exports = { createMemoryStore };
