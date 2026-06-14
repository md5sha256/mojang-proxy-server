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
