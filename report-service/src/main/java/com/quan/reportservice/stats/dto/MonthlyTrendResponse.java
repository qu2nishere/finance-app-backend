package com.quan.reportservice.stats.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record MonthlyTrendResponse(
        String period,
        String type,
        UUID categoryId,
        String currencyCode,
        long selectedAmount,
        long previousAmount,
        long momChangeAmount,
        BigDecimal momChangePercent,
        List<MonthlyBarPointResponse> points,
        List<CategoryBreakdownItemResponse> categoryBreakdown
) {
}
