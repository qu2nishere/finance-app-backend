package com.quan.budgetservice.budget.dto;

import com.quan.budgetservice.budget.CategoryLimit;
import com.quan.budgetservice.budget.LimitStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record CategoryLimitResponse(
        UUID id,
        UUID categoryId,
        String categoryName,
        String categoryIcon,
        String categoryColor,
        long limitAmount,
        long spentAmount,
        int alertThreshold,
        LimitStatus status
) {
    public static CategoryLimitResponse from(CategoryLimit limit, long spentAmount, LimitStatus status) {
        int thresholdPercent = limit.getAlertThreshold() != null
                ? limit.getAlertThreshold().multiply(BigDecimal.valueOf(100)).intValue()
                : 80;
        String trimmedColor = limit.getCategoryColor() != null ? limit.getCategoryColor().trim() : null;
        return new CategoryLimitResponse(
                limit.getId(),
                limit.getCategoryId(),
                limit.getCategoryName(),
                limit.getCategoryIcon(),
                trimmedColor,
                limit.getLimitAmount(),
                spentAmount,
                thresholdPercent,
                status
        );
    }
}
