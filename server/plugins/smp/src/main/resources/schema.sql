-- Tables owned by NetworkSMP.

CREATE TABLE IF NOT EXISTS smp_homes (
    uuid     VARCHAR(36) NOT NULL,
    name     VARCHAR(32) NOT NULL,
    location TEXT        NOT NULL,
    PRIMARY KEY (uuid, name)
);

CREATE TABLE IF NOT EXISTS smp_kit_cooldowns (
    uuid    VARCHAR(36) NOT NULL,
    kit     VARCHAR(32) NOT NULL,
    expires BIGINT      NOT NULL,
    PRIMARY KEY (uuid, kit)
);
