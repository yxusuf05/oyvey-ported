-- Tables owned by NetworkPractice.

CREATE TABLE IF NOT EXISTS practice_stats (
    uuid        VARCHAR(36) NOT NULL,
    kit         VARCHAR(32) NOT NULL,
    elo         INTEGER     NOT NULL DEFAULT 1000,
    wins        INTEGER     NOT NULL DEFAULT 0,
    losses      INTEGER     NOT NULL DEFAULT 0,
    streak      INTEGER     NOT NULL DEFAULT 0,
    best_streak INTEGER     NOT NULL DEFAULT 0,
    PRIMARY KEY (uuid, kit)
);

CREATE INDEX IF NOT EXISTS idx_practice_stats_kit ON practice_stats (kit, elo);

CREATE TABLE IF NOT EXISTS practice_kit_layouts (
    uuid     VARCHAR(36) NOT NULL,
    kit      VARCHAR(32) NOT NULL,
    contents TEXT        NOT NULL,
    PRIMARY KEY (uuid, kit)
);
