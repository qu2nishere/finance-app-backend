-- ===========================================================================
-- budget_db — schema khoi tao cua budget-service
--
-- So huu: ngan sach, han muc theo danh muc, muc tieu tiet kiem, tu trich.
--
-- category_id / wallet_id tro sang ledger_db nhung KHONG co FK. Kem theo
-- cot cache ten/icon/mau (doc tu event) de render man hinh ma khong phai
-- goi ledger-service.
-- ===========================================================================

CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;


-- ---------------------------------------------------------------------------
-- budgets — ky ngan sach (BudgetScreen / EditBudgetScreen / RegisterStep3)
-- ---------------------------------------------------------------------------
CREATE TABLE budgets (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID         NOT NULL,
    name             VARCHAR(100) NOT NULL DEFAULT 'Ngân sách tháng',
    period_type      VARCHAR(10)  NOT NULL DEFAULT 'MONTH',
    -- period_start co the la 2026-07-25 neu user_settings.month_start_day = 25
    period_start     DATE         NOT NULL,
    period_end       DATE         NOT NULL,
    -- '2026-08' | '2026-W34' | '2026' — khoa doi chieu nhanh voi spending_snapshot
    period_key       VARCHAR(10)  NOT NULL,
    total_limit      BIGINT,
    expected_income  BIGINT,
    currency_code    CHAR(3)      NOT NULL DEFAULT 'VND',
    rollover_enabled BOOLEAN      NOT NULL DEFAULT false,
    is_active        BOOLEAN      NOT NULL DEFAULT true,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at       TIMESTAMPTZ,
    CONSTRAINT ck_budget_period CHECK (period_type IN ('WEEK','MONTH','YEAR')),
    CONSTRAINT ck_budget_range  CHECK (period_end >= period_start)
);

CREATE UNIQUE INDEX uq_budget_user_period ON budgets (user_id, period_type, period_key)
    WHERE deleted_at IS NULL;

CREATE TRIGGER trg_budgets_updated BEFORE UPDATE ON budgets
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ---------------------------------------------------------------------------
-- category_limits — CategoryLimitScreen / EditBudgetScreen
-- Han muc hieu dung = limit_amount + rolled_over_amount
-- ---------------------------------------------------------------------------
CREATE TABLE category_limits (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    budget_id             UUID         NOT NULL REFERENCES budgets(id) ON DELETE CASCADE,
    user_id               UUID         NOT NULL,
    -- Tham chieu mem sang ledger_db.categories
    category_id           UUID         NOT NULL,
    -- Ban sao doc tu event CategoryCreated/CategoryUpdated, chi de hien thi
    category_name         VARCHAR(100) NOT NULL,
    category_icon         VARCHAR(50),
    category_color        CHAR(7),
    limit_amount          BIGINT       NOT NULL CHECK (limit_amount > 0),
    -- 0.80 = canh bao khi dung toi 80% (slider tren EditBudgetScreen)
    alert_threshold       NUMERIC(3,2) NOT NULL DEFAULT 0.80,
    include_subcategories BOOLEAN      NOT NULL DEFAULT true,
    rollover_enabled      BOOLEAN      NOT NULL DEFAULT false,
    rolled_over_amount    BIGINT       NOT NULL DEFAULT 0,
    is_active             BOOLEAN      NOT NULL DEFAULT true,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_limit_threshold CHECK (alert_threshold > 0 AND alert_threshold <= 1),
    CONSTRAINT uq_limit_budget_category UNIQUE (budget_id, category_id)
);

CREATE INDEX idx_limit_user_category ON category_limits (user_id, category_id) WHERE is_active;

CREATE TRIGGER trg_limits_updated BEFORE UPDATE ON category_limits
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ---------------------------------------------------------------------------
-- spending_snapshot — so da chi, dung tu event TransactionCreated/Updated/Deleted.
--
-- Ly do budget-service KHONG phai hoi ledger-service moi lan mo man Budget.
-- Cap nhat bang UPSERT (xem DATABASE.md muc 7.3).
-- ---------------------------------------------------------------------------
CREATE TABLE spending_snapshot (
    user_id       UUID        NOT NULL,
    period_key    VARCHAR(10) NOT NULL,
    category_id   UUID        NOT NULL,
    spent_amount  BIGINT      NOT NULL DEFAULT 0,
    txn_count     INTEGER     NOT NULL DEFAULT 0,
    last_event_at TIMESTAMPTZ,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, period_key, category_id)
);

CREATE INDEX idx_snapshot_user_period ON spending_snapshot (user_id, period_key);


-- ---------------------------------------------------------------------------
-- savings_goals — SavingsGoalsScreen / EditGoalScreen
--
-- progress, remaining, "cần 1,4tr/tháng", "chậm 1 tháng" deu TINH RA o UI,
-- khong luu cot.
-- ---------------------------------------------------------------------------
CREATE TABLE savings_goals (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID         NOT NULL,
    name             VARCHAR(100) NOT NULL,
    icon             VARCHAR(50)  NOT NULL DEFAULT 'CardGiftcard',
    color            CHAR(7)      NOT NULL DEFAULT '#16A34A',
    target_amount    BIGINT       NOT NULL CHECK (target_amount > 0),
    -- Cache = SUM(goal_deposits.amount)
    saved_amount     BIGINT       NOT NULL DEFAULT 0,
    start_date       DATE         NOT NULL DEFAULT CURRENT_DATE,
    target_date      DATE,
    -- Vi tich luy (tham chieu mem sang ledger_db)
    wallet_id        UUID,
    wallet_name      VARCHAR(100),
    priority         SMALLINT     NOT NULL DEFAULT 0,
    status           VARCHAR(15)  NOT NULL DEFAULT 'ACTIVE',
    show_on_home     BOOLEAN      NOT NULL DEFAULT true,
    remind_if_behind BOOLEAN      NOT NULL DEFAULT true,
    completed_at     TIMESTAMPTZ,
    note             VARCHAR(500),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at       TIMESTAMPTZ,
    CONSTRAINT ck_goal_status CHECK (status IN ('ACTIVE','COMPLETED','PAUSED','ARCHIVED')),
    CONSTRAINT ck_goal_saved  CHECK (saved_amount >= 0),
    CONSTRAINT ck_goal_dates  CHECK (target_date IS NULL OR target_date >= start_date)
);

CREATE INDEX idx_goal_user_status ON savings_goals (user_id, status, priority) WHERE deleted_at IS NULL;

CREATE TRIGGER trg_goals_updated BEFORE UPDATE ON savings_goals
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ---------------------------------------------------------------------------
-- goal_deposits — GoalDepositSheet. amount am = rut ra khoi muc tieu.
-- auto_save_run_id la forward reference - FK them o cuoi file.
-- ---------------------------------------------------------------------------
CREATE TABLE goal_deposits (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    goal_id          UUID        NOT NULL REFERENCES savings_goals(id) ON DELETE CASCADE,
    user_id          UUID        NOT NULL,
    amount           BIGINT      NOT NULL,
    source           VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    wallet_id        UUID,
    -- Giao dich tuong ung ben ledger_db (neu co)
    transaction_id   UUID,
    auto_save_run_id UUID,
    deposited_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    note             VARCHAR(255),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_deposit_source CHECK (source IN ('MANUAL','AUTO_SAVE','INTEREST','WITHDRAW')),
    CONSTRAINT ck_deposit_nonzero CHECK (amount <> 0)
);

CREATE INDEX idx_deposit_goal ON goal_deposits (goal_id, deposited_at DESC);
CREATE INDEX idx_deposit_user_date ON goal_deposits (user_id, deposited_at DESC);


-- ---------------------------------------------------------------------------
-- auto_save_rules — AutoSaveSettingScreen.
-- Mot user mot rule; rule chia % cho nhieu muc tieu qua auto_save_targets.
-- ---------------------------------------------------------------------------
CREATE TABLE auto_save_rules (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID        NOT NULL,
    is_enabled         BOOLEAN     NOT NULL DEFAULT false,
    mode               VARCHAR(10) NOT NULL DEFAULT 'PERCENT',
    -- "TỔNG % THU NHẬP TRÍCH RA" — 20 = 20%
    percent_total      SMALLINT,
    -- "HOẶC ĐẶT SỐ TIỀN CỐ ĐỊNH"
    fixed_amount       BIGINT,
    trigger_type       VARCHAR(20) NOT NULL DEFAULT 'ON_INCOME',
    day_of_month       SMALLINT,
    source_wallet_id   UUID,
    source_wallet_name VARCHAR(100),
    -- "Bỏ qua nếu số dư dưới 2.000.000₫"
    min_balance        BIGINT      NOT NULL DEFAULT 0,
    min_income_amount  BIGINT      NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_autosave_user UNIQUE (user_id),
    CONSTRAINT ck_autosave_mode CHECK (mode IN ('PERCENT','FIXED')),
    CONSTRAINT ck_autosave_trigger CHECK (trigger_type IN ('ON_INCOME','DAY_OF_MONTH')),
    CONSTRAINT ck_autosave_pct CHECK (percent_total IS NULL OR percent_total BETWEEN 0 AND 100),
    CONSTRAINT ck_autosave_dom CHECK (day_of_month IS NULL OR day_of_month BETWEEN 1 AND 28),
    CONSTRAINT ck_autosave_value CHECK (
        (mode = 'PERCENT' AND percent_total IS NOT NULL) OR
        (mode = 'FIXED'   AND fixed_amount  IS NOT NULL)
    )
);

CREATE TRIGGER trg_autosave_updated BEFORE UPDATE ON auto_save_rules
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- SUM(percent) = percent_total KHONG enforce o DB (trigger ton kem).
-- Validate o tang service khi luu - UI da hien "Tổng 20% chia cho 2 mục tiêu".
CREATE TABLE auto_save_targets (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id    UUID     NOT NULL REFERENCES auto_save_rules(id) ON DELETE CASCADE,
    goal_id    UUID     NOT NULL REFERENCES savings_goals(id) ON DELETE CASCADE,
    user_id    UUID     NOT NULL,
    percent    SMALLINT NOT NULL DEFAULT 0,
    is_enabled BOOLEAN  NOT NULL DEFAULT true,
    sort_order SMALLINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_target_rule_goal UNIQUE (rule_id, goal_id),
    CONSTRAINT ck_target_percent CHECK (percent BETWEEN 0 AND 100)
);

CREATE TRIGGER trg_targets_updated BEFORE UPDATE ON auto_save_targets
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ---------------------------------------------------------------------------
-- auto_save_runs — nhat ky tu trich.
-- uq_autosave_run_event: mot event thu nhap chi trich dung mot lan.
-- ---------------------------------------------------------------------------
CREATE TABLE auto_save_runs (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id                UUID        NOT NULL REFERENCES auto_save_rules(id) ON DELETE CASCADE,
    user_id                UUID        NOT NULL,
    trigger_event_id       UUID,
    trigger_transaction_id UUID,
    income_amount          BIGINT,
    deducted_amount        BIGINT      NOT NULL DEFAULT 0,
    status                 VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    failure_reason         TEXT,
    executed_at            TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_autorun_status CHECK (status IN
        ('PENDING','COMPLETED','SKIPPED_MIN_BALANCE','SKIPPED_DISABLED','FAILED')),
    CONSTRAINT uq_autosave_run_event UNIQUE (rule_id, trigger_event_id)
);

CREATE INDEX idx_autorun_user ON auto_save_runs (user_id, created_at DESC);


-- ---------------------------------------------------------------------------
-- budget_alerts — chong spam thong bao.
-- Moi muc (nguong / vuot) chi ban mot lan trong mot ky.
-- ---------------------------------------------------------------------------
CREATE TABLE budget_alerts (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID        NOT NULL,
    category_limit_id UUID        NOT NULL REFERENCES category_limits(id) ON DELETE CASCADE,
    period_key        VARCHAR(10) NOT NULL,
    level             VARCHAR(20) NOT NULL,
    spent_amount      BIGINT      NOT NULL,
    limit_amount      BIGINT      NOT NULL,
    sent_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_alert_level CHECK (level IN ('THRESHOLD_REACHED','EXCEEDED')),
    CONSTRAINT uq_alert_limit_period_level UNIQUE (category_limit_id, period_key, level)
);


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

CREATE TABLE processed_events (
    event_id     UUID PRIMARY KEY,
    event_type   VARCHAR(60) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);


-- ---------------------------------------------------------------------------
-- FK cho forward reference
-- ---------------------------------------------------------------------------
ALTER TABLE goal_deposits ADD CONSTRAINT fk_deposit_autosave_run
    FOREIGN KEY (auto_save_run_id) REFERENCES auto_save_runs(id) ON DELETE SET NULL;
