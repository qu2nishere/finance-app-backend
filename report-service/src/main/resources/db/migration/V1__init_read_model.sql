-- ===========================================================================
-- report_db — read model cua report-service (CQRS)
--
-- KHONG API nao ghi truc tiep vao day. Chi event consumer ghi.
-- Khong co FK: bang da denormalize, ten danh muc/vi nhung thang vao hang
-- de doc bao cao khong phai JOIN.
--
-- Vi sao vua co fact_transactions vua co agg_*:
--   agg_* phuc vu 100% man Report bang mot SELECT khong GROUP BY.
--   fact_transactions de dung lai agg_* khi cong thuc doi, va de xuat Excel.
-- ===========================================================================

CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;


-- ---------------------------------------------------------------------------
-- Bang chieu (dimension) — projection tu event cua user/ledger service
-- ---------------------------------------------------------------------------
CREATE TABLE dim_user (
    user_id         UUID PRIMARY KEY,
    currency_code   CHAR(3)     NOT NULL DEFAULT 'VND',
    -- Quyet dinh ranh gioi ngay/thang khi tinh occurred_date
    timezone        VARCHAR(50) NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    month_start_day SMALLINT    NOT NULL DEFAULT 1,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TRIGGER trg_dim_user_updated BEFORE UPDATE ON dim_user
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


CREATE TABLE dim_category (
    category_id UUID PRIMARY KEY,
    user_id     UUID,
    name        VARCHAR(100) NOT NULL,
    flow        VARCHAR(10)  NOT NULL,
    icon        VARCHAR(50),
    color       CHAR(7),
    parent_id   UUID,
    parent_name VARCHAR(100),
    is_deleted  BOOLEAN      NOT NULL DEFAULT false,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_dim_category_user ON dim_category (user_id) WHERE is_deleted = false;

CREATE TRIGGER trg_dim_category_updated BEFORE UPDATE ON dim_category
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


CREATE TABLE dim_wallet (
    wallet_id        UUID PRIMARY KEY,
    user_id          UUID         NOT NULL,
    name             VARCHAR(100) NOT NULL,
    kind             VARCHAR(20)  NOT NULL,
    color            CHAR(7),
    include_in_total BOOLEAN      NOT NULL DEFAULT true,
    is_deleted       BOOLEAN      NOT NULL DEFAULT false,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_dim_wallet_user ON dim_wallet (user_id) WHERE is_deleted = false;

CREATE TRIGGER trg_dim_wallet_updated BEFORE UPDATE ON dim_wallet
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ---------------------------------------------------------------------------
-- fact_transactions — bang phang, moi chieu nhung san vao hang.
-- transaction_id trung voi ledger_db.transactions.id (khong sinh id moi).
-- Xoa giao dich -> danh dau is_deleted, KHONG DELETE hang (con de replay).
-- ---------------------------------------------------------------------------
CREATE TABLE fact_transactions (
    transaction_id       UUID PRIMARY KEY,
    user_id              UUID        NOT NULL,
    occurred_at          TIMESTAMPTZ NOT NULL,
    occurred_date        DATE        NOT NULL,
    year_month           CHAR(7)     NOT NULL,   -- '2026-08'
    year_week            CHAR(8)     NOT NULL,   -- '2026-W34'
    year_num             SMALLINT    NOT NULL,
    type                 VARCHAR(15) NOT NULL,
    amount_abs           BIGINT      NOT NULL,
    amount_signed        BIGINT      NOT NULL,
    currency_code        CHAR(3)     NOT NULL DEFAULT 'VND',
    -- Chieu da nhung san - khong JOIN khi doc
    category_id          UUID,
    category_name        VARCHAR(100),
    category_flow        VARCHAR(10),
    parent_category_id   UUID,
    parent_category_name VARCHAR(100),
    wallet_id            UUID         NOT NULL,
    wallet_name          VARCHAR(100) NOT NULL,
    wallet_kind          VARCHAR(20)  NOT NULL,
    merchant             VARCHAR(150),
    note                 VARCHAR(500),
    source               VARCHAR(20)  NOT NULL,
    is_excluded          BOOLEAN     NOT NULL DEFAULT false,
    is_deleted           BOOLEAN     NOT NULL DEFAULT false,
    ingested_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_fact_user_month ON fact_transactions (user_id, year_month)
    WHERE is_deleted = false AND is_excluded = false;
CREATE INDEX idx_fact_user_date ON fact_transactions (user_id, occurred_date)
    WHERE is_deleted = false AND is_excluded = false;
CREATE INDEX idx_fact_user_cat ON fact_transactions (user_id, year_month, category_id)
    WHERE is_deleted = false AND is_excluded = false;
CREATE INDEX idx_fact_user_wallet ON fact_transactions (user_id, year_month, wallet_id)
    WHERE is_deleted = false AND is_excluded = false;

CREATE TRIGGER trg_fact_updated BEFORE UPDATE ON fact_transactions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ---------------------------------------------------------------------------
-- Bang tong hop — dung san cho tung tab cua ReportScreen
-- ---------------------------------------------------------------------------

-- Tab "Theo danh mục" + "Nguồn thu"
CREATE TABLE agg_monthly_category (
    user_id            UUID         NOT NULL,
    year_month         CHAR(7)      NOT NULL,
    category_id        UUID         NOT NULL,
    category_name      VARCHAR(100) NOT NULL,
    category_flow      VARCHAR(10)  NOT NULL,
    category_icon      VARCHAR(50),
    category_color     CHAR(7),
    total_amount       BIGINT       NOT NULL DEFAULT 0,
    txn_count          INTEGER      NOT NULL DEFAULT 0,
    avg_amount         BIGINT       NOT NULL DEFAULT 0,
    -- % trong tong chi/thu thang do
    share_percent      NUMERIC(5,2),
    -- So thang truoc: "▼ 4,1%"
    mom_change_percent NUMERIC(6,2),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, year_month, category_id)
);

CREATE TRIGGER trg_agg_cat_updated BEFORE UPDATE ON agg_monthly_category
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- Tab "Theo ví" + StatsCashflowScreen
CREATE TABLE agg_monthly_wallet (
    user_id         UUID         NOT NULL,
    year_month      CHAR(7)      NOT NULL,
    wallet_id       UUID         NOT NULL,
    wallet_name     VARCHAR(100) NOT NULL,
    wallet_kind     VARCHAR(20)  NOT NULL,
    opening_balance BIGINT       NOT NULL DEFAULT 0,
    total_income    BIGINT       NOT NULL DEFAULT 0,
    total_expense   BIGINT       NOT NULL DEFAULT 0,
    net_flow        BIGINT       NOT NULL DEFAULT 0,
    closing_balance BIGINT       NOT NULL DEFAULT 0,
    txn_count       INTEGER      NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, year_month, wallet_id)
);

CREATE TRIGGER trg_agg_wallet_updated BEFORE UPDATE ON agg_monthly_wallet
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- The dau man Report + StatsIncomeScreen
CREATE TABLE agg_monthly_summary (
    user_id             UUID        NOT NULL,
    year_month          CHAR(7)     NOT NULL,
    total_income        BIGINT      NOT NULL DEFAULT 0,
    total_expense       BIGINT      NOT NULL DEFAULT 0,
    net_cashflow        BIGINT      NOT NULL DEFAULT 0,
    savings_rate        NUMERIC(5,2),
    opening_balance     BIGINT      NOT NULL DEFAULT 0,
    closing_balance     BIGINT      NOT NULL DEFAULT 0,
    income_txn_count    INTEGER     NOT NULL DEFAULT 0,
    expense_txn_count   INTEGER     NOT NULL DEFAULT 0,
    top_category_id     UUID,
    top_category_name   VARCHAR(100),
    income_mom_percent  NUMERIC(6,2),
    expense_mom_percent NUMERIC(6,2),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, year_month)
);

CREATE TRIGGER trg_agg_summary_updated BEFORE UPDATE ON agg_monthly_summary
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- Mini-chart 7/30 ngay o HomeScreen
CREATE TABLE agg_daily (
    user_id       UUID    NOT NULL,
    occurred_date DATE    NOT NULL,
    total_income  BIGINT  NOT NULL DEFAULT 0,
    total_expense BIGINT  NOT NULL DEFAULT 0,
    net_flow      BIGINT  NOT NULL DEFAULT 0,
    txn_count     INTEGER NOT NULL DEFAULT 0,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, occurred_date)
);

CREATE TRIGGER trg_agg_daily_updated BEFORE UPDATE ON agg_daily
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ---------------------------------------------------------------------------
-- export_jobs — ExportDataScreen. Chay async, client poll trang thai.
-- ---------------------------------------------------------------------------
CREATE TABLE export_jobs (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              UUID        NOT NULL,
    format               VARCHAR(10) NOT NULL,
    from_date            DATE        NOT NULL,
    to_date              DATE        NOT NULL,
    -- Cac cong tac "chon noi dung xuat"
    include_transactions BOOLEAN     NOT NULL DEFAULT true,
    include_notes        BOOLEAN     NOT NULL DEFAULT true,
    include_attachments  BOOLEAN     NOT NULL DEFAULT false,
    include_limits       BOOLEAN     NOT NULL DEFAULT true,
    include_goals        BOOLEAN     NOT NULL DEFAULT false,
    -- NULL = tat ca
    wallet_ids           UUID[],
    category_ids         UUID[],
    status               VARCHAR(15) NOT NULL DEFAULT 'QUEUED',
    file_name            VARCHAR(255),
    file_url             TEXT,
    file_size_bytes      INTEGER,
    row_count            INTEGER,
    error_message        TEXT,
    requested_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at           TIMESTAMPTZ,
    completed_at         TIMESTAMPTZ,
    -- Don file tam sau 7 ngay
    expires_at           TIMESTAMPTZ,
    CONSTRAINT ck_export_format CHECK (format IN ('XLSX','CSV','PDF')),
    CONSTRAINT ck_export_status CHECK (status IN ('QUEUED','RUNNING','DONE','FAILED','EXPIRED')),
    CONSTRAINT ck_export_range  CHECK (to_date >= from_date)
);

CREATE INDEX idx_export_user ON export_jobs (user_id, requested_at DESC);
CREATE INDEX idx_export_pending ON export_jobs (requested_at) WHERE status IN ('QUEUED','RUNNING');


-- ---------------------------------------------------------------------------
-- Bang ky thuat
-- ---------------------------------------------------------------------------
CREATE TABLE processed_events (
    event_id     UUID PRIMARY KEY,
    event_type   VARCHAR(60) NOT NULL,
    aggregate_id UUID,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_processed_type ON processed_events (event_type, processed_at DESC);

-- Event nghe hong - KHONG vut di, de replay sau khi sua bug
CREATE TABLE dead_letter_events (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id    UUID,
    event_type  VARCHAR(60),
    payload     JSONB       NOT NULL,
    error       TEXT        NOT NULL,
    attempts    SMALLINT    NOT NULL DEFAULT 1,
    resolved_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_dlq_unresolved ON dead_letter_events (created_at) WHERE resolved_at IS NULL;
