package com.quan.ledgerservice.transaction.dto;

import java.util.List;

public record TransactionHistoryPage(List<TransactionResponse> items, int page, int size,
        long totalElements, long totalPages, List<CurrencyTotals> totalsByCurrency) {

    public record CurrencyTotals(String currencyCode, long totalIncome, long totalExpense) {}
}
