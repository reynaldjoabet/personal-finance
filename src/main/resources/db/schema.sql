-- Personal Finance Assistant schema (PostgreSQL).
-- Run once against a fresh database before starting the app.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(254) NOT NULL UNIQUE,
    display_name  VARCHAR(120) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS accounts (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider      VARCHAR(64)  NOT NULL,
    external_id   VARCHAR(128) NOT NULL,
    kind          VARCHAR(16)  NOT NULL CHECK (kind IN ('checking','savings','credit')),
    currency      CHAR(3)      NOT NULL,
    balance_minor BIGINT       NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (provider, external_id)
);

CREATE INDEX IF NOT EXISTS accounts_user_idx ON accounts(user_id);

CREATE TABLE IF NOT EXISTS transactions (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id    UUID         NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    occurred_on   DATE         NOT NULL,
    amount_minor  BIGINT       NOT NULL,
    currency      CHAR(3)      NOT NULL,
    merchant      VARCHAR(200) NOT NULL,
    category      VARCHAR(64)
);

CREATE INDEX IF NOT EXISTS tx_account_date_idx ON transactions(account_id, occurred_on DESC);
CREATE INDEX IF NOT EXISTS tx_category_idx     ON transactions(category) WHERE category IS NOT NULL;
