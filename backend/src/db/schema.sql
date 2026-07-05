-- ─────────────────────────────────────────────────────────────────────────────
-- OX Arena — PostgreSQL schema
--
-- Apply with:  psql "$DATABASE_URL" -f src/db/schema.sql
-- Idempotent: safe to run repeatedly (IF NOT EXISTS throughout).
--
-- Tables implemented in this slice: players, sessions, matches, match_moves.
-- The remaining tables (leaderboard, statistics, achievements, reports, bans,
-- settings, analytics) are defined here so the data model is complete and the
-- roadmap features have somewhere to land, even though the slice does not yet
-- write to all of them.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE EXTENSION IF NOT EXISTS "pgcrypto";  -- for gen_random_uuid()

-- Anonymous players. There is no PII; identity is a random UUID minted on first
-- connect and carried in a JWT.
CREATE TABLE IF NOT EXISTS players (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    display_name    TEXT,                      -- optional cosmetic name (roadmap)
    xp              INTEGER NOT NULL DEFAULT 0,
    level           INTEGER NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Issued anonymous sessions (for rotation / revocation / audit).
CREATE TABLE IF NOT EXISTS sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    player_id       UUID NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    issued_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked         BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_sessions_player ON sessions(player_id);

-- One row per match played.
CREATE TABLE IF NOT EXISTS matches (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mode            TEXT NOT NULL DEFAULT 'classic',
    player_x        UUID REFERENCES players(id) ON DELETE SET NULL,
    player_o        UUID REFERENCES players(id) ON DELETE SET NULL,
    status          TEXT NOT NULL DEFAULT 'active',   -- active | x_won | o_won | draw | abandoned
    winner          TEXT,                             -- 'X' | 'O' | NULL
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at        TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_matches_player_x ON matches(player_x);
CREATE INDEX IF NOT EXISTS idx_matches_player_o ON matches(player_o);

-- Ordered move log per match (for replay, audit, anti-cheat analysis).
CREATE TABLE IF NOT EXISTS match_moves (
    id              BIGSERIAL PRIMARY KEY,
    match_id        UUID NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
    move_number     INTEGER NOT NULL,
    symbol          TEXT NOT NULL,                    -- 'X' | 'O'
    cell_index      INTEGER NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (match_id, move_number)
);
CREATE INDEX IF NOT EXISTS idx_moves_match ON match_moves(match_id);

-- ─── Roadmap tables (schema only for now) ────────────────────────────────────

CREATE TABLE IF NOT EXISTS voice_sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    match_id        UUID REFERENCES matches(id) ON DELETE CASCADE,
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at        TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS leaderboard (
    player_id       UUID PRIMARY KEY REFERENCES players(id) ON DELETE CASCADE,
    rating          INTEGER NOT NULL DEFAULT 1000,
    wins            INTEGER NOT NULL DEFAULT 0,
    losses          INTEGER NOT NULL DEFAULT 0,
    draws           INTEGER NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_leaderboard_rating ON leaderboard(rating DESC);

CREATE TABLE IF NOT EXISTS statistics (
    player_id       UUID PRIMARY KEY REFERENCES players(id) ON DELETE CASCADE,
    matches_played  INTEGER NOT NULL DEFAULT 0,
    total_moves     INTEGER NOT NULL DEFAULT 0,
    fastest_win_ms  INTEGER
);

CREATE TABLE IF NOT EXISTS achievements (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    player_id       UUID NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    code            TEXT NOT NULL,
    unlocked_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (player_id, code)
);

CREATE TABLE IF NOT EXISTS reports (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id     UUID REFERENCES players(id) ON DELETE SET NULL,
    reported_id     UUID REFERENCES players(id) ON DELETE SET NULL,
    match_id        UUID REFERENCES matches(id) ON DELETE SET NULL,
    reason          TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS bans (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    player_id       UUID NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    reason          TEXT NOT NULL,
    banned_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_bans_player ON bans(player_id);

CREATE TABLE IF NOT EXISTS settings (
    player_id       UUID PRIMARY KEY REFERENCES players(id) ON DELETE CASCADE,
    sound_enabled   BOOLEAN NOT NULL DEFAULT TRUE,
    voice_enabled   BOOLEAN NOT NULL DEFAULT TRUE,
    haptics_enabled BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS analytics (
    id              BIGSERIAL PRIMARY KEY,
    player_id       UUID REFERENCES players(id) ON DELETE SET NULL,
    event           TEXT NOT NULL,
    payload         JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_analytics_event ON analytics(event);
