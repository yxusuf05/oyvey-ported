-- Punishment log owned by NetworkStaff.
-- The id is a UUID generated in Java so the table works on SQLite and MySQL alike.

CREATE TABLE IF NOT EXISTS staff_punishments (
    id         VARCHAR(36) PRIMARY KEY,
    uuid       VARCHAR(36) NOT NULL,
    name       VARCHAR(16) NOT NULL,
    type       VARCHAR(16) NOT NULL,
    reason     TEXT        NOT NULL,
    actor      VARCHAR(32) NOT NULL,
    created_at BIGINT      NOT NULL,
    expires_at BIGINT      NOT NULL DEFAULT 0,
    active     INTEGER     NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_punishments_uuid ON staff_punishments (uuid);

CREATE INDEX IF NOT EXISTS idx_punishments_active ON staff_punishments (type, active);
