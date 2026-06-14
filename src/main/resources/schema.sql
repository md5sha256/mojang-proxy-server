-- L2 persistent cache for resolved Mojang game profiles.
-- Keyed by UUID. name_lower is UNIQUE because a username maps to exactly one
-- account at any instant (lookups are case-insensitive). When a name changes
-- hands, caching the new owner evicts the old row via INSERT OR REPLACE, so we
-- never retain a stale name -> profile mapping.
CREATE TABLE IF NOT EXISTS game_profile (
    uuid       TEXT PRIMARY KEY,
    name       TEXT NOT NULL,
    name_lower TEXT NOT NULL,
    properties TEXT NOT NULL,   -- JSON array: [{name, value, signature?}]
    cached_at  INTEGER NOT NULL -- epoch millis, drives TTL expiry
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_profile_name ON game_profile (name_lower);

-- Runtime metrics, stored as a time series: one row per counter per flush.
-- value is the cumulative counter total at ts (monotonic across restarts, since
-- counters are seeded from the latest row on startup). Old rows are pruned by a
-- retention-based scheduled cleanup.
CREATE TABLE IF NOT EXISTS metric_snapshot (
    ts    INTEGER NOT NULL, -- epoch millis the snapshot was taken
    name  TEXT NOT NULL,    -- counter name, e.g. 'cache.l1_hit'
    value INTEGER NOT NULL  -- cumulative value at ts
);

-- Supports the MAX(ts)-per-name latest lookup and the ts<? retention delete.
CREATE INDEX IF NOT EXISTS idx_metric_name_ts ON metric_snapshot (name, ts);
