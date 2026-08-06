-- Core schema. Every other plugin creates its own tables in its onEnable.

CREATE TABLE IF NOT EXISTS profiles (
    uuid       VARCHAR(36) PRIMARY KEY,
    name       VARCHAR(16) NOT NULL,
    first_join BIGINT      NOT NULL,
    last_seen  BIGINT      NOT NULL,
    balance    DOUBLE      NOT NULL DEFAULT 0,
    -- named rank_id because `rank` is a reserved word in MySQL 8
    rank_id    VARCHAR(32) NOT NULL DEFAULT 'default',
    play_time  BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_profiles_name ON profiles (name);

CREATE INDEX IF NOT EXISTS idx_profiles_balance ON profiles (balance);
