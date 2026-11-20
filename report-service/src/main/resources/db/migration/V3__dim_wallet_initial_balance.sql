-- ===========================================================================
-- Bo sung initial_balance va created_at cho dim_wallet de report-service
-- tinh so du dau ky / cuoi ky cho tab Dong tien ma khong goi sang ledger.
-- ===========================================================================

ALTER TABLE dim_wallet
    ADD COLUMN initial_balance BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN created_at      TIMESTAMPTZ;
