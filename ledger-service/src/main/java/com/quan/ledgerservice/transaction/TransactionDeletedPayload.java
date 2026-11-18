package com.quan.ledgerservice.transaction;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionDeletedPayload(
        UUID transactionId,
        UUID userId,
        UUID walletId,
        UUID categoryId,
        TransactionType type,
        long amount,
        long signedAmount,
        Instant occurredAt,
        LocalDate occurredDate,
        boolean isExcludedFromReport
) {
}
