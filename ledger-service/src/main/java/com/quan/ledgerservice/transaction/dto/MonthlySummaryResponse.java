package com.quan.ledgerservice.transaction.dto;

public record MonthlySummaryResponse(
        long totalIncome,
        long totalExpense,
        long netBalance
) {
}
