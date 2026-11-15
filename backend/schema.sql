-- FinTrack PostgreSQL Reference Schema (v1.0)
-- Mirrors the Cloud Firestore & Room domain models for server-side FIU / AA webhook aggregation,
-- enterprise bank API ingestion, and analytics read replicas.

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    firebase_uid VARCHAR(128) UNIQUE NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    email VARCHAR(255) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Kolkata',
    pin_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    biometric_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    monthly_budget_limit NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS financial_accounts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    account_name VARCHAR(120) NOT NULL,
    institution_name VARCHAR(120) NOT NULL,
    account_type VARCHAR(32) NOT NULL CHECK (account_type IN ('SAVINGS', 'CURRENT', 'CREDIT_CARD', 'DEBIT_CARD', 'UPI_WALLET', 'CASH')),
    masked_identifier VARCHAR(32) NOT NULL,
    upi_id VARCHAR(120) DEFAULT '',
    source_type VARCHAR(40) NOT NULL,
    sync_status VARCHAR(32) NOT NULL,
    supports_real_balance BOOLEAN NOT NULL DEFAULT FALSE,
    current_balance NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    last_synced_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_accounts_user ON financial_accounts(user_id);

CREATE TABLE IF NOT EXISTS transactions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    account_id UUID REFERENCES financial_accounts(id) ON DELETE SET NULL,
    transaction_timestamp_ms BIGINT NOT NULL,
    date_ist DATE NOT NULL,
    time_ist TIME NOT NULL,
    amount NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    tx_type VARCHAR(16) NOT NULL CHECK (tx_type IN ('CREDIT', 'DEBIT')),
    sender_name VARCHAR(160) DEFAULT '',
    receiver_name VARCHAR(160) DEFAULT '',
    counterparty_name VARCHAR(160) DEFAULT '',
    recipient_mobile VARCHAR(32) NOT NULL DEFAULT 'Unavailable',
    upi_id VARCHAR(120) DEFAULT '',
    bank_account_name VARCHAR(120) DEFAULT '',
    masked_account_identifier VARCHAR(32) DEFAULT '',
    payment_app VARCHAR(32) NOT NULL DEFAULT 'OTHER',
    payment_method VARCHAR(32) NOT NULL DEFAULT 'UPI',
    reference_number VARCHAR(64) DEFAULT '',
    utr_or_upi_ref VARCHAR(64) DEFAULT '',
    status VARCHAR(24) NOT NULL CHECK (status IN ('SUCCESS', 'PENDING', 'FAILED', 'RECONCILED')),
    category VARCHAR(80) NOT NULL DEFAULT 'Other',
    description TEXT DEFAULT '',
    source VARCHAR(40) NOT NULL,
    notes TEXT DEFAULT '',
    dedup_fingerprint CHAR(32) NOT NULL,
    is_duplicate BOOLEAN NOT NULL DEFAULT FALSE,
    raw_source_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, dedup_fingerprint)
);

CREATE INDEX idx_tx_user_ts ON transactions(user_id, transaction_timestamp_ms DESC);
CREATE INDEX idx_tx_user_cat ON transactions(user_id, category);
CREATE INDEX idx_tx_user_party ON transactions(user_id, counterparty_name, upi_id);

CREATE TABLE IF NOT EXISTS expense_categories (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(80) NOT NULL,
    icon_key VARCHAR(48) NOT NULL DEFAULT 'category',
    color_hex CHAR(7) NOT NULL DEFAULT '#10B981',
    is_custom BOOLEAN NOT NULL DEFAULT FALSE,
    is_income_category BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, name)
);

CREATE TABLE IF NOT EXISTS budgets_and_goals (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(120) NOT NULL,
    scope VARCHAR(40) NOT NULL,
    category VARCHAR(80) DEFAULT '',
    limit_amount NUMERIC(14, 2) NOT NULL CHECK (limit_amount > 0),
    saved_or_target_amount NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    month_key CHAR(7) NOT NULL,
    alert_threshold_percent INTEGER NOT NULL DEFAULT 80,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS consent_records (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    scope VARCHAR(64) NOT NULL,
    granted BOOLEAN NOT NULL,
    purpose TEXT NOT NULL,
    aa_consent_handle VARCHAR(128),
    updated_at_ms BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, scope)
);

CREATE TABLE IF NOT EXISTS transaction_audit_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    field_changed VARCHAR(64) NOT NULL,
    previous_value TEXT NOT NULL,
    new_value TEXT NOT NULL,
    changed_at_ms BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS sync_audit_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source VARCHAR(48) NOT NULL,
    status VARCHAR(24) NOT NULL,
    records_processed INTEGER NOT NULL DEFAULT 0,
    duplicates_skipped INTEGER NOT NULL DEFAULT 0,
    message TEXT DEFAULT '',
    timestamp_ms BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
