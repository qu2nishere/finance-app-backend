-- ===========================================================================
-- ledger_db — schema khoi tao cua ledger-service
--
-- So huu: wallet, transaction, category, recurring, receipt OCR, parse log.
--
-- Wallet va transaction o CUNG service co chu dich: moi lan ghi giao dich
-- phai tru so du vi trong CUNG mot DB transaction. Tach ra la tu tao
-- distributed transaction cho bai toan khong can den no.
--
-- user_id la UUID tran - KHONG co FK sang user_db (ranh gioi microservice).
-- Extension (pgcrypto, pg_trgm, unaccent) tao boi init-databases.sql.
-- ===========================================================================

CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;


-- ---------------------------------------------------------------------------
-- wallets — WalletsScreen / AddWalletScreen
-- ---------------------------------------------------------------------------
CREATE TABLE wallets (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID         NOT NULL,
    name             VARCHAR(100) NOT NULL,
    kind             VARCHAR(20)  NOT NULL,
    currency_code    CHAR(3)      NOT NULL DEFAULT 'VND',
    initial_balance  BIGINT       NOT NULL DEFAULT 0,
    -- CACHE co chu dich. Nguon su that = initial_balance + SUM(signed_amount).
    -- Xem job doi soat o cuoi DATABASE.md muc 12.
    current_balance  BIGINT       NOT NULL DEFAULT 0,
    credit_limit     BIGINT,
    icon             VARCHAR(50)  NOT NULL DEFAULT 'AccountBalanceWallet',
    color            CHAR(7)      NOT NULL DEFAULT '#2563EB',
    bank_code        VARCHAR(20),
    -- 4 so cuoi thoi. KHONG luu so tai khoan day du.
    account_mask     VARCHAR(10),
    is_linked        BOOLEAN      NOT NULL DEFAULT false,
    sync_enabled     BOOLEAN      NOT NULL DEFAULT false,
    last_synced_at   TIMESTAMPTZ,
    include_in_total BOOLEAN      NOT NULL DEFAULT true,
    is_archived      BOOLEAN      NOT NULL DEFAULT false,
    display_order    SMALLINT     NOT NULL DEFAULT 0,
    note             VARCHAR(255),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at       TIMESTAMPTZ,
    CONSTRAINT ck_wallet_kind CHECK (kind IN ('CASH','BANK','EWALLET','SAVINGS','CREDIT_CARD','INVESTMENT')),
    CONSTRAINT ck_wallet_credit CHECK (credit_limit IS NULL OR kind = 'CREDIT_CARD')
);

CREATE INDEX idx_wallets_user ON wallets (user_id, display_order) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_wallets_user_name ON wallets (user_id, lower(name)) WHERE deleted_at IS NULL;

CREATE TRIGGER trg_wallets_updated BEFORE UPDATE ON wallets
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ---------------------------------------------------------------------------
-- categories — user_id NULL = danh muc he thong dung chung moi user.
-- Ho tro 2 cap (cha/con). Rang buoc dung 2 cap enforce o tang service.
-- ---------------------------------------------------------------------------
CREATE TABLE categories (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID,
    parent_id     UUID REFERENCES categories(id) ON DELETE SET NULL,
    name          VARCHAR(100) NOT NULL,
    flow          VARCHAR(10)  NOT NULL,
    icon          VARCHAR(50)  NOT NULL DEFAULT 'Category',
    color         CHAR(7)      NOT NULL DEFAULT '#64748B',
    is_system     BOOLEAN      NOT NULL DEFAULT false,
    is_archived   BOOLEAN      NOT NULL DEFAULT false,
    display_order SMALLINT     NOT NULL DEFAULT 0,
    usage_count   INTEGER      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ,
    CONSTRAINT ck_category_flow CHECK (flow IN ('EXPENSE','INCOME')),
    CONSTRAINT ck_category_not_self_parent CHECK (parent_id IS NULL OR parent_id <> id)
);

CREATE INDEX idx_categories_user_flow ON categories (user_id, flow, display_order) WHERE deleted_at IS NULL;
CREATE INDEX idx_categories_parent ON categories (parent_id) WHERE parent_id IS NOT NULL;
CREATE UNIQUE INDEX uq_categories_system_name ON categories (lower(name), flow)
    WHERE user_id IS NULL AND deleted_at IS NULL;
CREATE UNIQUE INDEX uq_categories_user_name ON categories (user_id, lower(name), flow)
    WHERE user_id IS NOT NULL AND deleted_at IS NULL;

CREATE TRIGGER trg_categories_updated BEFORE UPDATE ON categories
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ---------------------------------------------------------------------------
-- transactions — bang lon nhat.
--
-- amount LUON DUONG; signed_amount mang dau. CHECK ep hai cot khop nhau,
-- nen SUM(signed_amount) ra so du ma khong can CASE WHEN.
--
-- recurring_rule_id / receipt_draft_id la forward reference - FK them o
-- cuoi file sau khi cac bang do da ton tai.
-- ---------------------------------------------------------------------------
CREATE TABLE transactions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID          NOT NULL,
    wallet_id               UUID          NOT NULL REFERENCES wallets(id),
    category_id             UUID          REFERENCES categories(id),
    type                    VARCHAR(15)   NOT NULL,
    amount                  BIGINT        NOT NULL,
    signed_amount           BIGINT        NOT NULL,
    currency_code           CHAR(3)       NOT NULL DEFAULT 'VND',
    exchange_rate           NUMERIC(18,8) NOT NULL DEFAULT 1,
    occurred_at             TIMESTAMPTZ   NOT NULL,
    -- occurred_at quy ve timezone cua user - de GROUP BY ngay khong lech mui gio
    occurred_date           DATE          NOT NULL,
    note                    VARCHAR(500),
    merchant                VARCHAR(150),
    location                VARCHAR(200),
    source                  VARCHAR(20)   NOT NULL DEFAULT 'MANUAL',
    -- Ma giao dich tu Momo/bank - chong ghi trung khi parse notification 2 lan
    external_ref            VARCHAR(120),
    -- Chuyen tien giua 2 vi: 2 ban ghi cung transfer_group_id
    transfer_group_id       UUID,
    counterpart_wallet_id   UUID REFERENCES wallets(id),
    recurring_rule_id       UUID,
    receipt_draft_id        UUID,
    parent_transaction_id   UUID REFERENCES transactions(id),
    is_excluded_from_report BOOLEAN     NOT NULL DEFAULT false,
    is_pending              BOOLEAN     NOT NULL DEFAULT false,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at              TIMESTAMPTZ,
    CONSTRAINT ck_txn_type   CHECK (type IN ('EXPENSE','INCOME','TRANSFER_OUT','TRANSFER_IN','ADJUSTMENT')),
    CONSTRAINT ck_txn_source CHECK (source IN ('MANUAL','NOTIFICATION','SMS','OCR','RECURRING','IMPORT','AUTO_SAVE')),
    CONSTRAINT ck_txn_amount CHECK (amount > 0),
    CONSTRAINT ck_txn_sign CHECK (
        (type IN ('EXPENSE','TRANSFER_OUT') AND signed_amount = -amount) OR
        (type IN ('INCOME','TRANSFER_IN')   AND signed_amount =  amount) OR
        (type = 'ADJUSTMENT')
    ),
    CONSTRAINT ck_txn_transfer CHECK (
        (type NOT IN ('TRANSFER_OUT','TRANSFER_IN')) OR
        (transfer_group_id IS NOT NULL AND counterpart_wallet_id IS NOT NULL)
    )
);

-- AllTransactions: danh sach theo ngay giam dan
CREATE INDEX idx_txn_user_occurred ON transactions (user_id, occurred_at DESC) WHERE deleted_at IS NULL;
-- Home: 5 giao dich gan nhat + tong thu chi thang
CREATE INDEX idx_txn_user_date ON transactions (user_id, occurred_date DESC) WHERE deleted_at IS NULL;
-- Loc theo danh muc / theo vi
CREATE INDEX idx_txn_user_category ON transactions (user_id, category_id, occurred_date DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_txn_wallet ON transactions (wallet_id, occurred_at DESC) WHERE deleted_at IS NULL;
-- "3 giao dich chua phan loai"
CREATE INDEX idx_txn_uncategorized ON transactions (user_id) WHERE category_id IS NULL AND deleted_at IS NULL;
CREATE INDEX idx_txn_transfer_group ON transactions (transfer_group_id) WHERE transfer_group_id IS NOT NULL;
CREATE UNIQUE INDEX uq_txn_external_ref ON transactions (user_id, source, external_ref)
    WHERE external_ref IS NOT NULL AND deleted_at IS NULL;
-- O search cua AllTransactionsScreen
CREATE INDEX idx_txn_note_trgm ON transactions USING gin (note gin_trgm_ops);
CREATE INDEX idx_txn_merchant_trgm ON transactions USING gin (merchant gin_trgm_ops);

CREATE TRIGGER trg_transactions_updated BEFORE UPDATE ON transactions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ---------------------------------------------------------------------------
-- transaction_attachments — anh hoa don (TransactionDetailScreen)
-- ---------------------------------------------------------------------------
CREATE TABLE transaction_attachments (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID        NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    user_id        UUID        NOT NULL,
    kind           VARCHAR(20) NOT NULL DEFAULT 'RECEIPT_PHOTO',
    -- S3/MinIO key. KHONG luu binary trong DB.
    storage_url    TEXT        NOT NULL,
    file_name      VARCHAR(255),
    mime_type      VARCHAR(60),
    size_bytes     INTEGER,
    width_px       SMALLINT,
    height_px      SMALLINT,
    ocr_text       TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_attachment_kind CHECK (kind IN ('RECEIPT_PHOTO','DOCUMENT'))
);

CREATE INDEX idx_attachment_txn ON transaction_attachments (transaction_id);


-- ---------------------------------------------------------------------------
-- balance_adjustments — AdjustBalanceScreen.
-- Ghi lai moi lan chinh so du tay de so du khong bao gio "tu nhien nhay".
-- ---------------------------------------------------------------------------
CREATE TABLE balance_adjustments (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID        NOT NULL,
    wallet_id      UUID        NOT NULL REFERENCES wallets(id) ON DELETE CASCADE,
    old_balance    BIGINT      NOT NULL,
    new_balance    BIGINT      NOT NULL,
    delta          BIGINT      NOT NULL,
    reason         VARCHAR(255),
    transaction_id UUID REFERENCES transactions(id),
    adjusted_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_adjust_delta CHECK (delta = new_balance - old_balance)
);

CREATE INDEX idx_adjust_wallet ON balance_adjustments (wallet_id, adjusted_at DESC);


-- ---------------------------------------------------------------------------
-- recurring_rules — RecurringTransactionsScreen
-- ---------------------------------------------------------------------------
CREATE TABLE recurring_rules (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID         NOT NULL,
    name               VARCHAR(100) NOT NULL,
    wallet_id          UUID         NOT NULL REFERENCES wallets(id),
    category_id        UUID         REFERENCES categories(id),
    type               VARCHAR(15)  NOT NULL,
    amount             BIGINT       NOT NULL CHECK (amount > 0),
    currency_code      CHAR(3)      NOT NULL DEFAULT 'VND',
    note               VARCHAR(500),
    -- Lich lap
    frequency          VARCHAR(15)  NOT NULL,
    interval_count     SMALLINT     NOT NULL DEFAULT 1,
    -- 31 -> tu lui ve ngay cuoi thang o tang service
    day_of_month       SMALLINT,
    day_of_week        SMALLINT,
    month_of_year      SMALLINT,
    start_date         DATE         NOT NULL,
    end_date           DATE,
    max_occurrences    SMALLINT,
    occurrence_count   SMALLINT     NOT NULL DEFAULT 0,
    -- Scheduler quet cot nay
    next_run_date      DATE         NOT NULL,
    last_run_date      DATE,
    auto_create        BOOLEAN      NOT NULL DEFAULT true,
    remind_days_before SMALLINT     NOT NULL DEFAULT 3,
    is_active          BOOLEAN      NOT NULL DEFAULT true,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at         TIMESTAMPTZ,
    CONSTRAINT ck_recur_freq CHECK (frequency IN ('DAILY','WEEKLY','MONTHLY','QUARTERLY','YEARLY')),
    CONSTRAINT ck_recur_type CHECK (type IN ('EXPENSE','INCOME')),
    CONSTRAINT ck_recur_dom  CHECK (day_of_month IS NULL OR day_of_month BETWEEN 1 AND 31),
    CONSTRAINT ck_recur_dow  CHECK (day_of_week  IS NULL OR day_of_week  BETWEEN 1 AND 7),
    CONSTRAINT ck_recur_moy  CHECK (month_of_year IS NULL OR month_of_year BETWEEN 1 AND 12),
    CONSTRAINT ck_recur_range CHECK (end_date IS NULL OR end_date >= start_date)
);

-- Toan bo query cua scheduler nam trong index nay
CREATE INDEX idx_recur_next_run ON recurring_rules (next_run_date)
    WHERE is_active = true AND deleted_at IS NULL;
CREATE INDEX idx_recur_user ON recurring_rules (user_id, next_run_date) WHERE deleted_at IS NULL;

CREATE TRIGGER trg_recurring_updated BEFORE UPDATE ON recurring_rules
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ---------------------------------------------------------------------------
-- recurring_runs — nhat ky chay dinh ky.
-- uq_run_rule_date la chot chan: scheduler chay 2 lan trong ngay khong ghi trung.
-- ---------------------------------------------------------------------------
CREATE TABLE recurring_runs (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id        UUID        NOT NULL REFERENCES recurring_rules(id) ON DELETE CASCADE,
    user_id        UUID        NOT NULL,
    scheduled_date DATE        NOT NULL,
    executed_at    TIMESTAMPTZ,
    transaction_id UUID REFERENCES transactions(id),
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    failure_reason TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_run_status CHECK (status IN ('PENDING','CREATED','SKIPPED','FAILED')),
    CONSTRAINT uq_run_rule_date UNIQUE (rule_id, scheduled_date)
);


-- ---------------------------------------------------------------------------
-- receipt_drafts — ScanReceiptScreen.
-- Ban nhap OCR CHUA phai giao dich. User xac nhan roi moi INSERT transactions.
-- ---------------------------------------------------------------------------
CREATE TABLE receipt_drafts (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID        NOT NULL,
    image_url             TEXT        NOT NULL,
    raw_text              TEXT,
    merchant              VARCHAR(150),
    total_amount          BIGINT,
    occurred_at           TIMESTAMPTZ,
    suggested_category_id UUID REFERENCES categories(id),
    suggested_wallet_id   UUID REFERENCES wallets(id),
    confidence            NUMERIC(4,3),
    status                VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    confirmed_at          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_draft_status CHECK (status IN ('DRAFT','CONFIRMED','DISCARDED')),
    CONSTRAINT ck_draft_confidence CHECK (confidence IS NULL OR confidence BETWEEN 0 AND 1)
);

CREATE INDEX idx_draft_user_status ON receipt_drafts (user_id, status, created_at DESC);

CREATE TRIGGER trg_drafts_updated BEFORE UPDATE ON receipt_drafts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- "Tach thanh N giao dich theo mon"
CREATE TABLE receipt_draft_lines (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    draft_id    UUID          NOT NULL REFERENCES receipt_drafts(id) ON DELETE CASCADE,
    line_no     SMALLINT      NOT NULL,
    description VARCHAR(200)  NOT NULL,
    quantity    NUMERIC(10,3) NOT NULL DEFAULT 1,
    unit_price  BIGINT,
    amount      BIGINT        NOT NULL,
    category_id UUID REFERENCES categories(id),
    is_selected BOOLEAN       NOT NULL DEFAULT true,
    CONSTRAINT uq_line_draft_no UNIQUE (draft_id, line_no)
);


-- ---------------------------------------------------------------------------
-- Giai doan 2: doc thong bao Momo / SMS banking.
-- Regex nam trong DB de sua duoc ma khong phai build lai app.
-- ---------------------------------------------------------------------------
CREATE TABLE notification_parse_rules (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    package_name   VARCHAR(120) NOT NULL,
    bank_code      VARCHAR(20)  NOT NULL,
    label          VARCHAR(100) NOT NULL,
    amount_regex   TEXT         NOT NULL,
    merchant_regex TEXT,
    direction_hint VARCHAR(10),
    priority       SMALLINT     NOT NULL DEFAULT 0,
    is_active      BOOLEAN      NOT NULL DEFAULT true,
    version        SMALLINT     NOT NULL DEFAULT 1,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_parse_direction CHECK (direction_hint IS NULL OR direction_hint IN ('IN','OUT','AUTO'))
);

CREATE INDEX idx_parse_rule_package ON notification_parse_rules (package_name, priority DESC) WHERE is_active;

CREATE TRIGGER trg_parse_rules_updated BEFORE UPDATE ON notification_parse_rules
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- Luu ca ca parse hong de con sua regex khi Momo doi format
CREATE TABLE notification_parse_log (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID        NOT NULL,
    package_name   VARCHAR(120),
    raw_title      TEXT,
    raw_text       TEXT        NOT NULL,
    received_at    TIMESTAMPTZ NOT NULL,
    rule_id        UUID REFERENCES notification_parse_rules(id),
    parse_status   VARCHAR(20) NOT NULL,
    transaction_id UUID REFERENCES transactions(id),
    parsed_amount  BIGINT,
    error_message  TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_parse_status CHECK (parse_status IN ('PARSED','NO_RULE','AMBIGUOUS','DUPLICATE','FAILED'))
);

CREATE INDEX idx_parselog_user_status ON notification_parse_log (user_id, parse_status, created_at DESC);


-- ---------------------------------------------------------------------------
-- Bang ky thuat
-- ---------------------------------------------------------------------------
CREATE TABLE outbox_events (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id   UUID        NOT NULL,
    event_type     VARCHAR(60) NOT NULL,
    payload        JSONB       NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ,
    attempts       SMALLINT    NOT NULL DEFAULT 0,
    last_error     TEXT
);

CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;

-- Idempotency cho event nghe tu service khac (UserRegistered, GoalDeposited)
CREATE TABLE processed_events (
    event_id     UUID PRIMARY KEY,
    event_type   VARCHAR(60) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);


-- ---------------------------------------------------------------------------
-- FK cho cac forward reference o bang transactions
-- ---------------------------------------------------------------------------
ALTER TABLE transactions ADD CONSTRAINT fk_txn_recurring_rule
    FOREIGN KEY (recurring_rule_id) REFERENCES recurring_rules(id) ON DELETE SET NULL;

ALTER TABLE transactions ADD CONSTRAINT fk_txn_receipt_draft
    FOREIGN KEY (receipt_draft_id) REFERENCES receipt_drafts(id) ON DELETE SET NULL;
