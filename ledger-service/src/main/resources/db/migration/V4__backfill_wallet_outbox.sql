-- ===========================================================================
-- Backfill outbox_events cho cac vi da ton tai truoc khi bat WalletCreated.
--
-- Cot payload luu NGUYEN EventEnvelope (eventId, eventType, version, occurredAt,
-- userId, aggregateId, payload) dung dinh dang EventPublisher sinh ra.
-- Chi chen cho ban ghi deleted_at IS NULL va chua co dong WalletCreated.
-- ===========================================================================

INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, payload, created_at)
SELECT
    'WALLET',
    w.id,
    'WalletCreated',
    jsonb_build_object(
        'eventId', gen_random_uuid(),
        'eventType', 'WalletCreated',
        'version', 1,
        'occurredAt', to_char(w.created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
        'userId', w.user_id,
        'aggregateId', w.id,
        'payload', jsonb_build_object(
            'walletId', w.id,
            'name', w.name,
            'kind', w.kind,
            'color', CASE WHEN w.color IS NOT NULL THEN trim(w.color) ELSE NULL END,
            'initialBalance', w.initial_balance,
            'currencyCode', trim(w.currency_code),
            'includeInTotal', w.include_in_total,
            'createdAt', to_char(w.created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')
        )
    ),
    w.created_at
FROM wallets w
WHERE w.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1
      FROM outbox_events o
      WHERE o.aggregate_type = 'WALLET'
        AND o.aggregate_id = w.id
        AND o.event_type = 'WalletCreated'
  )
ORDER BY w.created_at ASC;
