package com.quan.reportservice.stats.dto;

import java.util.UUID;

public record WalletCashflowItemResponse(
        UUID walletId,
        String walletName,
        String walletKind,
        String color,
        String accountMask,
        long openingBalance,
        long totalIncome,
        long totalExpense,
        long netFlow,
        long closingBalance,
        boolean includeInTotal
) {
}
