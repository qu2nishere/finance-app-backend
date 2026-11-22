package com.quan.budgetservice.budget.dto;

import java.time.LocalDate;
import java.util.List;

public record CurrentBudgetResponse(
        String periodKey,
        LocalDate periodStart,
        LocalDate periodEnd,
        int daysLeft,
        long totalLimit,
        long totalSpent,
        List<CategoryLimitResponse> limits
) {
}
