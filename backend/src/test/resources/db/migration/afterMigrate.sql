-- Test-only table for the base entity mappings. Runs after every migrate, never in production.
CREATE TABLE IF NOT EXISTS base_entity_probe (
    id         uuid PRIMARY KEY,
    version    bigint       NOT NULL,
    created_at timestamptz  NOT NULL,
    updated_at timestamptz  NOT NULL,
    deleted_at timestamptz,
    name       varchar(255) NOT NULL
);

-- Test-only table for the automatic auditing of an audited entity.
CREATE TABLE IF NOT EXISTS audited_probe (
    id          uuid PRIMARY KEY,
    version     bigint       NOT NULL,
    created_at  timestamptz  NOT NULL,
    updated_at  timestamptz  NOT NULL,
    deleted_at  timestamptz,
    name        varchar(255) NOT NULL,
    secret_hash varchar(255) NOT NULL
);
