-- Finance Copilot core schema. Every user-owned table carries user_id; all reads are scoped by it.

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(100),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- user_id NULL = system default category, visible to everyone.
CREATE TABLE categories (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT REFERENCES users (id) ON DELETE CASCADE,
    name       VARCHAR(60) NOT NULL,
    kind       VARCHAR(10) NOT NULL DEFAULT 'EXPENSE' CHECK (kind IN ('EXPENSE', 'INCOME', 'TRANSFER')),
    color      VARCHAR(7)  NOT NULL DEFAULT '#94a3b8',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_categories_user_name UNIQUE NULLS NOT DISTINCT (user_id, name)
);

CREATE TABLE statements (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    file_name       VARCHAR(255) NOT NULL,
    file_type       VARCHAR(10) NOT NULL CHECK (file_type IN ('CSV', 'PDF')),
    file_hash       CHAR(64)    NOT NULL,
    rows_imported   INT         NOT NULL DEFAULT 0,
    rows_duplicate  INT         NOT NULL DEFAULT 0,
    rows_failed     INT         NOT NULL DEFAULT 0,
    period_start    DATE,
    period_end      DATE,
    uploaded_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_statements_user_hash UNIQUE (user_id, file_hash)
);

CREATE TABLE transactions (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    statement_id        BIGINT REFERENCES statements (id) ON DELETE SET NULL,
    txn_date            DATE           NOT NULL,
    description_raw     TEXT           NOT NULL,
    merchant_normalized VARCHAR(120)   NOT NULL,
    amount              NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    direction           VARCHAR(6)     NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    balance             NUMERIC(14, 2),
    category_id         BIGINT REFERENCES categories (id) ON DELETE SET NULL,
    category_source     VARCHAR(10)    NOT NULL DEFAULT 'NONE'
        CHECK (category_source IN ('RULE', 'CACHE', 'AI', 'USER', 'NONE')),
    -- sha256(user-independent row content + occurrence index); unique per user to skip re-imported rows
    hash                CHAR(64)       NOT NULL,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_transactions_user_hash UNIQUE (user_id, hash)
);

CREATE INDEX idx_transactions_user_date ON transactions (user_id, txn_date);
CREATE INDEX idx_transactions_user_category ON transactions (user_id, category_id);
CREATE INDEX idx_transactions_user_merchant ON transactions (user_id, merchant_normalized);

-- Shared across users: a merchant name maps to a system category. Populated by the LLM step.
CREATE TABLE merchant_category_cache (
    merchant_normalized VARCHAR(120) PRIMARY KEY,
    category_id         BIGINT      NOT NULL REFERENCES categories (id) ON DELETE CASCADE,
    hit_count           INT         NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- user_id NULL = built-in rule for well-known merchants; otherwise a user's own rule (evaluated first).
CREATE TABLE category_rules (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT REFERENCES users (id) ON DELETE CASCADE,
    pattern     VARCHAR(200) NOT NULL,
    match_type  VARCHAR(10)  NOT NULL DEFAULT 'KEYWORD' CHECK (match_type IN ('KEYWORD', 'REGEX')),
    category_id BIGINT       NOT NULL REFERENCES categories (id) ON DELETE CASCADE,
    priority    INT          NOT NULL DEFAULT 100,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_category_rules_user ON category_rules (user_id);

CREATE TABLE subscriptions (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    merchant_normalized VARCHAR(120)   NOT NULL,
    category_id         BIGINT REFERENCES categories (id) ON DELETE SET NULL,
    avg_amount          NUMERIC(14, 2) NOT NULL,
    frequency           VARCHAR(10)    NOT NULL CHECK (frequency IN ('WEEKLY', 'MONTHLY', 'YEARLY')),
    occurrences         INT            NOT NULL,
    last_charge_date    DATE           NOT NULL,
    next_expected_date  DATE           NOT NULL,
    status              VARCHAR(10)    NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_subscriptions_user_merchant UNIQUE (user_id, merchant_normalized)
);

-- Audit trail of every LLM call: text-to-SQL questions, categorisation batches and insights.
CREATE TABLE ai_query_log (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT REFERENCES users (id) ON DELETE CASCADE,
    kind              VARCHAR(20) NOT NULL CHECK (kind IN ('ASK', 'CATEGORIZE', 'INSIGHT')),
    question          TEXT,
    generated_sql     TEXT,
    is_valid          BOOLEAN,
    rejection_reason  TEXT,
    row_count         INT,
    latency_ms        INT,
    prompt_tokens     INT,
    completion_tokens INT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_query_log_user_created ON ai_query_log (user_id, created_at);
