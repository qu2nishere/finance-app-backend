package com.quan.budgetservice.budget.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

public record UpdateLimitRequest(
        @Positive(message = "Hạn mức phải lớn hơn 0")
        Long limitAmount,

        @Min(value = 1, message = "Ngưỡng cảnh báo từ 1% đến 100%")
        @Max(value = 100, message = "Ngưỡng cảnh báo từ 1% đến 100%")
        Integer alertThreshold
) {
}
