package com.quan.reportservice.stats.dto;

import com.quan.reportservice.ingest.FactTransaction;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ReportTransactionItemResponse(
        UUID transactionId,
        String type,
        long amount,
        long signedAmount,
        String currencyCode,
        Instant occurredAt,
        LocalDate occurredDate,
        UUID categoryId,
        String categoryName,
        String categoryIcon,
        String categoryColor,
        UUID walletId,
        String walletName,
        String merchant,
        String note
) {
    public static ReportTransactionItemResponse from(FactTransaction f) {
        return new ReportTransactionItemResponse(
                f.getTransactionId(),
                f.getType(),
                f.getAmountAbs(),
                f.getAmountSigned(),
                f.getCurrencyCode() != null ? f.getCurrencyCode().trim() : "VND",
                f.getOccurredAt(),
                f.getOccurredDate(),
                f.getCategoryId(),
                f.getCategoryName() != null ? f.getCategoryName() : "Chưa phân loại",
                f.getCategoryIcon() != null ? f.getCategoryIcon() : "Category",
                f.getCategoryColor() != null ? f.getCategoryColor().trim() : "#94A3B8",
                f.getWalletId(),
                f.getWalletName(),
                f.getMerchant(),
                f.getNote()
        );
    }
}
