package com.quan.reportservice.stats.dto;

public record MonthlyBarPointResponse(
        String period,
        int year,
        int month,
        long totalAmount,
        int txnCount,
        boolean isSelected
) {
}
