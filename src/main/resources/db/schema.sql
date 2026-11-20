-- Personal Finance Assistant schema (PostgreSQL).
-- Run once against a fresh database before starting the app.
--
-- Requires PostgreSQL 18+ for the built-in uuidv7(); written for 19.
-- CREATE TABLE IF NOT EXISTS skips existing tables entirely, so the constraints below
-- only apply to a fresh database -- an existing one needs explicit ALTER TABLEs.
--
-- Column types (varchar(n), char(3)) must match the skunk codecs in finance.db.Codecs:
-- skunk checks parameter and column types when it prepares a statement.
-- CHECK constraints mirror the Iron refinements on the domain types, so rows written
-- outside the app are held to the same rules.
--
-- Maintenance: reclaim bloat with REPACK (CONCURRENTLY), not VACUUM FULL or CLUSTER,
-- which take an ACCESS EXCLUSIVE lock for the whole rebuild.

-- Primary keys are UUIDv7 (finance.domain.Uuid generates them in the app). The defaults
-- use the same version so ids from either source sort by creation time and append to
-- the right edge of the primary-key index. uuidv7() is built in, so pgcrypto is not needed.

CREATE TABLE IF NOT EXISTS users (
    id            UUID         NOT NULL DEFAULT uuidv7(),
    email         VARCHAR(254) NOT NULL,
    display_name  VARCHAR(120) NOT NULL,
    password_hash VARCHAR(120) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT users_pk                 PRIMARY KEY (id),
    CONSTRAINT users_email_uq           UNIQUE (email),
    CONSTRAINT users_email_chk          CHECK (email ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$'),
    CONSTRAINT users_display_name_chk   CHECK (btrim(display_name) <> ''),
    CONSTRAINT users_password_hash_chk  CHECK (btrim(password_hash) <> '')
);

-- Every sync rewrites balance_minor and updated_at in place. Neither column is indexed,
-- so with 10% free space per page those updates stay HOT: the new row version goes on the
-- same page and no index needs touching. That is also why there is deliberately no
-- (user_id, updated_at) index for listForUser's ORDER BY -- a user has only a handful of
-- accounts, so the sort is trivial, and indexing updated_at would turn off HOT updates.
CREATE TABLE IF NOT EXISTS accounts (
    id            UUID         NOT NULL DEFAULT uuidv7(),
    user_id       UUID         NOT NULL,
    provider      VARCHAR(64)  NOT NULL,
    external_id   VARCHAR(128) NOT NULL,
    kind          VARCHAR(16)  NOT NULL,
    currency      CHAR(3)      NOT NULL,
    balance_minor BIGINT       NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT accounts_pk              PRIMARY KEY (id),
    CONSTRAINT accounts_user_fk         FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT accounts_external_uq     UNIQUE (provider, external_id),
    CONSTRAINT accounts_kind_chk        CHECK (kind IN ('checking', 'savings', 'credit')),
    CONSTRAINT accounts_currency_chk    CHECK (currency ~ '^[A-Z]{3}$')
) WITH (fillfactor = 90);

-- Serves listForUser, the join in Transactions.betweenForUser, and the ON DELETE CASCADE
-- from users (Postgres does not index foreign-key columns on its own).
CREATE INDEX IF NOT EXISTS accounts_user_idx ON accounts (user_id);

-- Append-mostly: rows are inserted by sync and never updated, so default fillfactor.
CREATE TABLE IF NOT EXISTS transactions (
    id            UUID         NOT NULL DEFAULT uuidv7(),
    account_id    UUID         NOT NULL,
    occurred_on   DATE         NOT NULL,
    amount_minor  BIGINT       NOT NULL,
    currency      CHAR(3)      NOT NULL,
    merchant      VARCHAR(200) NOT NULL,
    category      VARCHAR(64),
    CONSTRAINT transactions_pk           PRIMARY KEY (id),
    CONSTRAINT transactions_account_fk   FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE CASCADE,
    CONSTRAINT transactions_currency_chk CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT transactions_merchant_chk CHECK (btrim(merchant) <> ''),
    CONSTRAINT transactions_category_chk CHECK (category IS NULL OR btrim(category) <> '')
);

-- Serves both date-range queries (account_id = ? AND occurred_on BETWEEN ? AND ?
-- ORDER BY occurred_on DESC) and the ON DELETE CASCADE from accounts.
-- There is no index on category: nothing filters on it in SQL (Analytics groups in Scala),
-- and every extra index slows the bulk inserts that sync does.
CREATE INDEX IF NOT EXISTS tx_account_date_idx
    ON transactions (account_id, occurred_on DESC);

-- Per-user TrueLayer OAuth tokens. One row per user; refresh updates in place.
-- Only the primary key is indexed, so token refreshes are HOT updates (see accounts).
CREATE TABLE IF NOT EXISTS truelayer_tokens (
    user_id       UUID        NOT NULL,
    access_token  TEXT        NOT NULL,
    refresh_token TEXT        NOT NULL,
    expires_at    TIMESTAMPTZ NOT NULL,
    scope         TEXT,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT truelayer_tokens_pk      PRIMARY KEY (user_id),
    CONSTRAINT truelayer_tokens_user_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) WITH (fillfactor = 90);
