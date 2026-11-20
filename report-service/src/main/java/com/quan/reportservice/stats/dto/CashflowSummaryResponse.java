package com.quan.reportservice.stats.dto;

import java.util.List;

public record CashflowSummaryResponse(
        String period,
        String currencyCode,
        long openingBalance,
        long totalIncome,
        long totalExpense,
        long closingBalance,
        List<WalletCashflowItemResponse> wallets
) {
}
