package com.quan.reportservice.stats.dto;

import java.time.LocalDate;
import java.util.List;

public record MonthlyTransactionsResponse(
        String period,
        LocalDate selectedDate,
        String currencyCode,
        long monthTotalAmount,
        int monthTxnCount,
        long filteredTotalAmount,
        int filteredTxnCount,
        List<DailyTransactionGroupResponse> days
) {
}
