package com.quan.ledgerservice.transaction;

import com.quan.ledgerservice.category.CategoryFlow;
import com.quan.ledgerservice.wallet.WalletKind;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionCreatedPayload(
        UUID transactionId,
        UUID walletId,
        String walletName,
        WalletKind walletKind,
        UUID categoryId,
        String categoryName,
        CategoryFlow categoryFlow,
        String categoryIcon,
        String categoryColor,
        UUID parentCategoryId,
        String parentCategoryName,
        TransactionType type,
        long amount,
        long signedAmount,
        String currencyCode,
        Instant occurredAt,
        LocalDate occurredDate,
        String timezone,
        String merchant,
        String note,
        TransactionSource source,
        boolean isExcludedFromReport
) {
}
