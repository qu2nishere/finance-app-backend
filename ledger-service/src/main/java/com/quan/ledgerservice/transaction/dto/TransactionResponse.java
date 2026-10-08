package com.quan.ledgerservice.transaction.dto;

import com.quan.ledgerservice.category.Category;
import com.quan.ledgerservice.transaction.Transaction;
import com.quan.ledgerservice.transaction.TransactionType;
import com.quan.ledgerservice.wallet.Wallet;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        UUID walletId,
        String walletName,
        UUID categoryId,
        String categoryName,
        String categoryIcon,
        String categoryColor,
        TransactionType type,
        long amount,
        long signedAmount,
        String currencyCode,
        Instant occurredAt,
        LocalDate occurredDate,
        String note,
        String merchant
) {
    public static TransactionResponse from(Transaction txn, Wallet wallet, Category category) {
        return new TransactionResponse(
                txn.getId(),
                txn.getWalletId(),
                wallet != null ? wallet.getName() : null,
                txn.getCategoryId(),
                category != null ? category.getName() : "Chưa phân loại",
                category != null ? category.getIcon() : "Category",
                category != null ? category.getColor() : "#64748B",
                txn.getType(),
                txn.getAmount(),
                txn.getSignedAmount(),
                txn.getCurrencyCode(),
                txn.getOccurredAt(),
                txn.getOccurredDate(),
                txn.getNote(),
                txn.getMerchant()
        );
    }
}
