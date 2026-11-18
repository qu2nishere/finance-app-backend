package com.quan.reportservice.stats.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CategoryBreakdownItemResponse(
        UUID categoryId,
        String categoryName,
        String categoryIcon,
        String categoryColor,
        long totalAmount,
        int txnCount,
        BigDecimal sharePercent,
        BigDecimal momChangePercent
) {
}
