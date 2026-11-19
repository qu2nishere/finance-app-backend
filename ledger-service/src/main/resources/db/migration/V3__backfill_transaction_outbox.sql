-- ===========================================================================
-- Backfill outbox_events cho cac giao dich da ton tai truoc khi bat Outbox.
--
-- Cot payload luu NGUYEN EventEnvelope (eventId, eventType, version, occurredAt,
-- userId, aggregateId, payload) dung dinh dang EventPublisher sinh ra.
-- Chi chen cho ban ghi deleted_at IS NULL va chua co dong TransactionCreated.
-- ===========================================================================

INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, payload, created_at)
SELECT
    'TRANSACTION',
    t.id,
    'TransactionCreated',
    jsonb_build_object(
        'eventId', gen_random_uuid(),
        'eventType', 'TransactionCreated',
        'version', 1,
        'occurredAt', to_char(t.created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
        'userId', t.user_id,
        'aggregateId', t.id,
        'payload', jsonb_build_object(
            'transactionId', t.id,
            'walletId', t.wallet_id,
            'walletName', w.name,
            'walletKind', w.kind,
            'categoryId', t.category_id,
            'categoryName', c.name,
            'categoryFlow', c.flow,
            'categoryIcon', c.icon,
            'categoryColor', CASE WHEN c.color IS NOT NULL THEN trim(c.color) ELSE NULL END,
            'parentCategoryId', pc.id,
            'parentCategoryName', pc.name,
            'type', t.type,
            'amount', t.amount,
            'signedAmount', t.signed_amount,
            'currencyCode', trim(t.currency_code),
            'occurredAt', to_char(t.occurred_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
            'occurredDate', to_char(t.occurred_date, 'YYYY-MM-DD'),
            'merchant', t.merchant,
            'note', t.note,
            'source', t.source,
            'isExcludedFromReport', t.is_excluded_from_report
        )
    ),
    t.created_at
FROM transactions t
JOIN wallets w ON w.id = t.wallet_id
LEFT JOIN categories c ON c.id = t.category_id
LEFT JOIN categories pc ON pc.id = c.parent_id
WHERE t.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1
      FROM outbox_events o
      WHERE o.aggregate_type = 'TRANSACTION'
        AND o.aggregate_id = t.id
        AND o.event_type = 'TransactionCreated'
  )
ORDER BY t.occurred_at ASC;
