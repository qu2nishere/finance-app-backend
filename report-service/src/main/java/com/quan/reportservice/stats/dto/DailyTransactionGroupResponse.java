package com.quan.reportservice.stats.dto;

import java.time.LocalDate;
import java.util.List;

public record DailyTransactionGroupResponse(
        LocalDate date,
        long totalAmount,
        long netAmount,
        int txnCount,
        List<ReportTransactionItemResponse> items
) {
}
