-- Savvy R&D schema (PostgreSQL). Mirrors src/store/memoryStore.js.
-- Conventions: snake_case, plural table names, integer identity keys,
-- created_at / updated_at on every table, deleted_at where soft delete is needed.

CREATE TABLE users (
  id            INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  email         VARCHAR(255) NOT NULL UNIQUE,
  role          VARCHAR(20)  NOT NULL DEFAULT 'self',       -- self | parent | child
  time_zone     VARCHAR(64)  NOT NULL DEFAULT 'UTC',
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  deleted_at    TIMESTAMPTZ
);

-- One row per app install. A reinstall creates a new row on the same user.
CREATE TABLE devices (
  id                    INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id               INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE ON UPDATE CASCADE,
  platform              VARCHAR(10) NOT NULL CHECK (platform IN ('ios', 'android')),
  token_hash            CHAR(64) NOT NULL UNIQUE,
  push_token            TEXT,
  authorization_status  VARCHAR(20),   -- iOS FamilyControls / Android service state as last reported
  applied_rule_version  INTEGER,
  shield_active         BOOLEAN NOT NULL DEFAULT FALSE,
  protections           JSONB NOT NULL DEFAULT '{}'::jsonb,
  app_inventory         JSONB,          -- Android child: launchable apps [{package, label}]
  app_inventory_at      TIMESTAMPTZ, -- Android: accessibility, usage_access, device_admin, advanced_protection, adb_enabled
  last_seen_at          TIMESTAMPTZ,
  created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at            TIMESTAMPTZ
);
CREATE INDEX idx_devices_user_id ON devices(user_id);

-- Manufactured cards. card_code is the public id in the NDEF URL and QR.
CREATE TABLE cards (
  id                INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  card_code         CHAR(12) NOT NULL UNIQUE,
  format            VARCHAR(10) NOT NULL CHECK (format IN ('static', 'signed', 'sun')),
  chip_uid          VARCHAR(20),              -- NTAG 424 DNA UID from SUN message, when known
  last_sun_counter  INTEGER,                  -- replay protection for SUN cards
  status            VARCHAR(20) NOT NULL DEFAULT 'unassigned', -- unassigned | active | revoked
  user_id           INTEGER REFERENCES users(id) ON DELETE SET NULL ON UPDATE CASCADE,
  bound_at          TIMESTAMPTZ,
  batch_ref         VARCHAR(50),
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- MVP rule: one active card per account.
CREATE UNIQUE INDEX uq_cards_one_active_per_user ON cards(user_id) WHERE status = 'active';

CREATE TABLE card_scans (
  id          INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  card_id     INTEGER REFERENCES cards(id) ON DELETE SET NULL ON UPDATE CASCADE,
  user_id     INTEGER REFERENCES users(id) ON DELETE CASCADE ON UPDATE CASCADE,
  device_id   INTEGER REFERENCES devices(id) ON DELETE SET NULL ON UPDATE CASCADE,
  source      VARCHAR(10) NOT NULL,          -- nfc | qr | nfc_background
  result      VARCHAR(40) NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE commitments (
  id             INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id        INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE ON UPDATE CASCADE,
  device_id      INTEGER NOT NULL REFERENCES devices(id) ON DELETE CASCADE ON UPDATE CASCADE,
  mode           VARCHAR(10) NOT NULL CHECK (mode IN ('study', 'work', 'sleep', 'custom', 'task')),
  unlock_policy  VARCHAR(20) NOT NULL CHECK (unlock_policy IN ('card_required', 'free', 'locked')),
  task_ref       VARCHAR(64),
  local_id       VARCHAR(64),                 -- app-generated id, used to reconcile offline events
  created_offline BOOLEAN NOT NULL DEFAULT FALSE,
  selection_ref  TEXT,                        -- opaque, platform specific app selection reference
  status         VARCHAR(20) NOT NULL DEFAULT 'active', -- active | released | completed
  started_at     TIMESTAMPTZ NOT NULL,        -- server clock
  ends_at        TIMESTAMPTZ NOT NULL,        -- server clock
  ended_at       TIMESTAMPTZ,
  end_reason     VARCHAR(40),
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_commitments_device_local_id ON commitments(device_id, local_id) WHERE local_id IS NOT NULL;
CREATE UNIQUE INDEX uq_commitments_one_active_per_device ON commitments(device_id) WHERE status = 'active';

-- Emergency exits. Use SELECT ... FOR UPDATE on the user row when counting,
-- so two parallel requests cannot both pass the limit.
CREATE TABLE emergency_exits (
  id             INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id        INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE ON UPDATE CASCADE,
  device_id      INTEGER REFERENCES devices(id) ON DELETE SET NULL ON UPDATE CASCADE,
  commitment_id  INTEGER NOT NULL REFERENCES commitments(id) ON DELETE CASCADE ON UPDATE CASCADE,
  reason         TEXT,
  status         VARCHAR(20) NOT NULL,        -- pending | used | cancelled
  available_at   TIMESTAMPTZ NOT NULL,
  used_at        TIMESTAMPTZ,
  offline        BOOLEAN NOT NULL DEFAULT FALSE,
  over_limit     BOOLEAN NOT NULL DEFAULT FALSE,  -- offline exit that broke the limit (cannot be undone)
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_emergency_exits_user_created ON emergency_exits(user_id, created_at);

CREATE TABLE focus_sessions (
  id               INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id          INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE ON UPDATE CASCADE,
  device_id        INTEGER REFERENCES devices(id) ON DELETE SET NULL ON UPDATE CASCADE,
  commitment_id    INTEGER REFERENCES commitments(id) ON DELETE SET NULL ON UPDATE CASCADE,
  mode             VARCHAR(10) NOT NULL,
  started_at       TIMESTAMPTZ NOT NULL,
  ended_at         TIMESTAMPTZ NOT NULL,
  outcome          VARCHAR(20) NOT NULL,      -- completed | released_by_card | task_completed | ended_early | emergency_exit
  focused_seconds  INTEGER NOT NULL,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_focus_sessions_user_ended ON focus_sessions(user_id, ended_at);

CREATE TABLE family_link_codes (
  id              INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  parent_user_id  INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE ON UPDATE CASCADE,
  code            CHAR(8) NOT NULL,
  expires_at      TIMESTAMPTZ NOT NULL,
  used_at         TIMESTAMPTZ,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE family_links (
  id               INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  parent_user_id   INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE ON UPDATE CASCADE,
  child_user_id    INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE ON UPDATE CASCADE,
  child_device_id  INTEGER NOT NULL REFERENCES devices(id) ON DELETE CASCADE ON UPDATE CASCADE,
  status           VARCHAR(20) NOT NULL DEFAULT 'active',
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at       TIMESTAMPTZ
);
-- MVP rule: one child profile per parent.
CREATE UNIQUE INDEX uq_family_links_one_child ON family_links(parent_user_id) WHERE status = 'active';

CREATE TABLE child_rule_sets (
  id               INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  family_link_id   INTEGER NOT NULL REFERENCES family_links(id) ON DELETE CASCADE ON UPDATE CASCADE,
  child_device_id  INTEGER NOT NULL REFERENCES devices(id) ON DELETE CASCADE ON UPDATE CASCADE,
  version          INTEGER NOT NULL,
  rules            JSONB NOT NULL,
  applied_at       TIMESTAMPTZ,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (child_device_id, version)
);

-- Offline sync idempotency: one row per device event id.
CREATE TABLE sync_events (
  id          INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  device_id   INTEGER NOT NULL REFERENCES devices(id) ON DELETE CASCADE ON UPDATE CASCADE,
  event_id    VARCHAR(64) NOT NULL,
  event_type  VARCHAR(30) NOT NULL,
  result      JSONB NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (device_id, event_id)
);

-- Android only: daily per-app usage from UsageStatsManager. iOS cannot upload Screen Time data.
CREATE TABLE usage_daily (
  id             INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id        INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE ON UPDATE CASCADE,
  device_id      INTEGER NOT NULL REFERENCES devices(id) ON DELETE CASCADE ON UPDATE CASCADE,
  usage_date     DATE NOT NULL,
  total_seconds  INTEGER NOT NULL,
  apps           JSONB NOT NULL,            -- [{package, label, seconds}]
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (device_id, usage_date)
);

-- Silent pushes to child devices when parent rules change. A worker sends them via APNs / FCM.
CREATE TABLE push_outbox (
  id          INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  device_id   INTEGER NOT NULL REFERENCES devices(id) ON DELETE CASCADE ON UPDATE CASCADE,
  platform    VARCHAR(10) NOT NULL,
  push_token  TEXT,
  kind        VARCHAR(20) NOT NULL,
  payload     JSONB NOT NULL,
  sent_at     TIMESTAMPTZ,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
