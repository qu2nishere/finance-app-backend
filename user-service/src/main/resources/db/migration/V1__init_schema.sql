-- ===========================================================================
-- user_db — schema khoi tao cua user-service
--
-- So huu: danh tinh nguoi dung, cach chung minh danh tinh (mat khau / OAuth),
-- phien dang nhap, thiet bi, cai dat, hop thu thong bao.
--
-- Extension (citext, pgcrypto) da duoc tao boi infra/postgres/init-databases.sql.
-- ===========================================================================

-- Ham dung chung cho moi trigger updated_at trong database nay.
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;


-- ---------------------------------------------------------------------------
-- users — danh tinh con nguoi. KHONG chua cach dang nhap.
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email                CITEXT       NOT NULL,
    email_verified       BOOLEAN      NOT NULL DEFAULT false,
    email_verified_at    TIMESTAMPTZ,
    full_name            VARCHAR(100) NOT NULL,
    phone                VARCHAR(20),
    phone_verified       BOOLEAN      NOT NULL DEFAULT false,
    avatar_url           TEXT,
    status               VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    plan                 VARCHAR(20)  NOT NULL DEFAULT 'FREE',
    plan_expires_at      TIMESTAMPTZ,
    locale               VARCHAR(10)  NOT NULL DEFAULT 'vi-VN',
    timezone             VARCHAR(50)  NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    onboarding_completed BOOLEAN      NOT NULL DEFAULT false,
    last_login_at        TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at           TIMESTAMPTZ,
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE','SUSPENDED','PENDING_DELETION')),
    CONSTRAINT ck_users_plan   CHECK (plan   IN ('FREE','PLUS'))
);

-- Email chi unique trong cac tai khoan con song -> xoa roi dang ky lai duoc.
CREATE UNIQUE INDEX uq_users_email ON users (email) WHERE deleted_at IS NULL;

CREATE TRIGGER trg_users_updated BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ---------------------------------------------------------------------------
-- credentials — dang nhap bang mat khau.
--
-- TUY CHON: user chi dang nhap bang Google se KHONG co hang o day.
-- Man AccountScreen phan biet "Doi mat khau" / "Dat mat khau" bang cach
-- kiem tra bang nay co hang hay khong - khong can them cot vao users.
--
-- Tach khoi users de SELECT * tren users khong bao gio keo theo hash mat khau.
-- ---------------------------------------------------------------------------
CREATE TABLE credentials (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    password_hash       VARCHAR(255) NOT NULL,
    algorithm           VARCHAR(20)  NOT NULL DEFAULT 'BCRYPT',
    password_changed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    failed_attempts     SMALLINT     NOT NULL DEFAULT 0,
    locked_until        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_credentials_user UNIQUE (user_id)
);

CREATE TRIGGER trg_credentials_updated BEFORE UPDATE ON credentials
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ---------------------------------------------------------------------------
-- user_identities — dang nhap bang nha cung cap ngoai (Google / Apple).
--
-- Doi xung voi credentials: users giu DANH TINH, hai bang nay giu CACH
-- chung minh danh tinh do. Bang rieng (khong phai cot tren users) de mot
-- nguoi vua co mat khau vua lien ket Google, hoac lien ket nhieu provider.
--
-- provider_user_id la claim 'sub' cua Google - on dinh vinh vien.
-- TUYET DOI khong dung email lam khoa: user doi email Google duoc.
-- ---------------------------------------------------------------------------
CREATE TABLE user_identities (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider          VARCHAR(20)  NOT NULL,
    provider_user_id  VARCHAR(255) NOT NULL,
    email             CITEXT,
    email_verified    BOOLEAN      NOT NULL DEFAULT false,
    display_name      VARCHAR(100),
    avatar_url        TEXT,
    -- Chi dien khi can goi API cua provider (vd: backup len Google Drive).
    -- Neu backup di S3 cua ban thi de NULL - khong luu thi khong the ro ri.
    -- Ma hoa AES-GCM truoc khi luu, KHONG phai hash (can doc lai duoc).
    access_token_enc  TEXT,
    refresh_token_enc TEXT,
    token_expires_at  TIMESTAMPTZ,
    scopes            TEXT,
    linked_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_login_at     TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_identity_provider CHECK (provider IN ('GOOGLE','APPLE','FACEBOOK')),
    CONSTRAINT uq_identity_provider_uid UNIQUE (provider, provider_user_id)
);

CREATE INDEX idx_identity_user ON user_identities (user_id);

CREATE TRIGGER trg_identities_updated BEFORE UPDATE ON user_identities
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ---------------------------------------------------------------------------
-- verification_tokens — ma OTP 6 so (xac thuc email / quen mat khau).
--
-- code_hash dung BCRYPT, khong phai SHA-256: ma 6 so chi co 10^6 kha nang,
-- hash nhanh la brute-force xong trong mot giay neu lo DB.
-- ---------------------------------------------------------------------------
CREATE TABLE verification_tokens (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    purpose      VARCHAR(30)  NOT NULL,
    code_hash    VARCHAR(255) NOT NULL,
    destination  VARCHAR(255) NOT NULL,
    attempts     SMALLINT     NOT NULL DEFAULT 0,
    max_attempts SMALLINT     NOT NULL DEFAULT 5,
    expires_at   TIMESTAMPTZ  NOT NULL,
    consumed_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_vtoken_purpose CHECK (purpose IN ('VERIFY_EMAIL','RESET_PASSWORD','CHANGE_EMAIL'))
);

CREATE INDEX idx_vtoken_user_purpose ON verification_tokens (user_id, purpose, created_at DESC);
CREATE INDEX idx_vtoken_expires ON verification_tokens (expires_at) WHERE consumed_at IS NULL;


-- ---------------------------------------------------------------------------
-- devices — SecurityScreen "Thiet bi dang dang nhap"
-- ---------------------------------------------------------------------------
CREATE TABLE devices (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id     VARCHAR(128) NOT NULL,
    device_name   VARCHAR(100),
    platform      VARCHAR(20)  NOT NULL,
    os_version    VARCHAR(30),
    app_version   VARCHAR(30),
    push_token    TEXT,
    last_ip       INET,
    last_location VARCHAR(100),
    last_seen_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    is_trusted    BOOLEAN      NOT NULL DEFAULT false,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_devices_user_device UNIQUE (user_id, device_id),
    CONSTRAINT ck_devices_platform CHECK (platform IN ('ANDROID','IOS','WEB'))
);

CREATE TRIGGER trg_devices_updated BEFORE UPDATE ON devices
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ---------------------------------------------------------------------------
-- sessions — refresh token. SHA-256 la du vi token la random 128+ bit
-- (entropy cao -> hash nhanh khong sao, khac han code_hash o tren).
-- ---------------------------------------------------------------------------
CREATE TABLE sessions (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id          UUID         REFERENCES devices(id) ON DELETE SET NULL,
    refresh_token_hash VARCHAR(255) NOT NULL,
    issued_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at         TIMESTAMPTZ  NOT NULL,
    last_used_at       TIMESTAMPTZ,
    revoked_at         TIMESTAMPTZ,
    revoked_reason     VARCHAR(50),
    ip_address         INET,
    user_agent         TEXT,
    CONSTRAINT uq_sessions_token UNIQUE (refresh_token_hash)
);

CREATE INDEX idx_sessions_user_active ON sessions (user_id) WHERE revoked_at IS NULL;


-- ---------------------------------------------------------------------------
-- user_settings — CurrencySettingsScreen + ProfileScreen. Quan he 1-1.
-- ---------------------------------------------------------------------------
CREATE TABLE user_settings (
    user_id             UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    -- Tien te
    currency_code       CHAR(3)     NOT NULL DEFAULT 'VND',
    currency_decimals   SMALLINT    NOT NULL DEFAULT 0,
    decimal_separator   VARCHAR(10) NOT NULL DEFAULT 'DOT',
    symbol_position     VARCHAR(10) NOT NULL DEFAULT 'SUFFIX',
    compact_numbers     BOOLEAN     NOT NULL DEFAULT true,
    convert_legacy_data BOOLEAN     NOT NULL DEFAULT false,
    -- Hien thi
    theme_mode          VARCHAR(10) NOT NULL DEFAULT 'SYSTEM',
    language            VARCHAR(10) NOT NULL DEFAULT 'vi',
    -- Ky tai chinh
    start_of_week       SMALLINT    NOT NULL DEFAULT 1,
    month_start_day     SMALLINT    NOT NULL DEFAULT 1,
    -- Bao mat
    biometric_enabled   BOOLEAN     NOT NULL DEFAULT false,
    pin_hash            VARCHAR(255),
    pin_enabled         BOOLEAN     NOT NULL DEFAULT false,
    auto_lock_seconds   INTEGER     NOT NULL DEFAULT 60,
    -- Sao luu
    auto_backup_enabled BOOLEAN     NOT NULL DEFAULT false,
    last_backup_at      TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_settings_sep   CHECK (decimal_separator IN ('DOT','COMMA','SPACE')),
    CONSTRAINT ck_settings_pos   CHECK (symbol_position IN ('PREFIX','SUFFIX')),
    CONSTRAINT ck_settings_theme CHECK (theme_mode IN ('LIGHT','DARK','SYSTEM')),
    CONSTRAINT ck_settings_msd   CHECK (month_start_day BETWEEN 1 AND 28)
);

CREATE TRIGGER trg_settings_updated BEFORE UPDATE ON user_settings
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ---------------------------------------------------------------------------
-- notification_settings — moi cong tac tren NotificationSettingsScreen la mot cot.
-- ---------------------------------------------------------------------------
CREATE TABLE notification_settings (
    user_id                   UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    push_enabled              BOOLEAN  NOT NULL DEFAULT true,
    -- Nhac nho
    daily_reminder_enabled    BOOLEAN  NOT NULL DEFAULT true,
    daily_reminder_time       TIME     NOT NULL DEFAULT '21:00',
    recurring_due_enabled     BOOLEAN  NOT NULL DEFAULT true,
    recurring_due_lead_days   SMALLINT NOT NULL DEFAULT 3,
    -- Ngan sach
    budget_threshold_enabled  BOOLEAN  NOT NULL DEFAULT true,
    budget_exceeded_enabled   BOOLEAN  NOT NULL DEFAULT true,
    uncategorized_enabled     BOOLEAN  NOT NULL DEFAULT true,
    -- Thu nhap & muc tieu
    income_received_enabled   BOOLEAN  NOT NULL DEFAULT true,
    autosave_done_enabled     BOOLEAN  NOT NULL DEFAULT true,
    goal_behind_enabled       BOOLEAN  NOT NULL DEFAULT true,
    -- Bao cao
    weekly_report_enabled     BOOLEAN  NOT NULL DEFAULT true,
    weekly_report_dow         SMALLINT NOT NULL DEFAULT 1,
    monthly_summary_enabled   BOOLEAN  NOT NULL DEFAULT true,
    monthly_summary_day       SMALLINT NOT NULL DEFAULT 1,
    -- Khong lam phien
    quiet_hours_enabled       BOOLEAN  NOT NULL DEFAULT true,
    quiet_hours_start         TIME     NOT NULL DEFAULT '22:00',
    quiet_hours_end           TIME     NOT NULL DEFAULT '07:00',
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TRIGGER trg_notif_settings_updated BEFORE UPDATE ON notification_settings
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ---------------------------------------------------------------------------
-- notifications — hop thu trong app (NotificationsScreen).
-- Service khac KHONG insert truc tiep: chung publish event, user-service ghi.
-- ---------------------------------------------------------------------------
CREATE TABLE notifications (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kind       VARCHAR(20)  NOT NULL,
    title      VARCHAR(200) NOT NULL,
    body       TEXT,
    deep_link  VARCHAR(255),
    ref_type   VARCHAR(30),
    ref_id     UUID,
    icon       VARCHAR(50),
    severity   VARCHAR(10)  NOT NULL DEFAULT 'INFO',
    read_at    TIMESTAMPTZ,
    pushed_at  TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_notif_kind CHECK (kind IN ('LIMIT','TRANSACTION','GOAL','REPORT','SYSTEM')),
    CONSTRAINT ck_notif_severity CHECK (severity IN ('INFO','WARNING','CRITICAL'))
);

CREATE INDEX idx_notif_user_created ON notifications (user_id, created_at DESC);
CREATE INDEX idx_notif_user_unread  ON notifications (user_id) WHERE read_at IS NULL;


-- ---------------------------------------------------------------------------
-- Bang ky thuat
-- ---------------------------------------------------------------------------

-- Outbox pattern: ghi event trong CUNG transaction voi du lieu nghiep vu,
-- scheduler doc va day sang RabbitMQ. Khong mat event khi RabbitMQ chet.
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

CREATE TABLE audit_logs (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID,
    action     VARCHAR(60) NOT NULL,
    ip_address INET,
    user_agent TEXT,
    metadata   JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_user_created ON audit_logs (user_id, created_at DESC);
