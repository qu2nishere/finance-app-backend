package com.quan.budgetservice.budget.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateLimitRequest(
        @NotNull(message = "Danh mục không được để trống")
        UUID categoryId,

        @NotBlank(message = "Tên danh mục không được để trống")
        @Size(max = 100, message = "Tên danh mục tối đa 100 ký tự")
        String categoryName,

        @Size(max = 50, message = "Tên biểu tượng tối đa 50 ký tự")
        String categoryIcon,

        @Size(max = 7, message = "Mã màu tối đa 7 ký tự")
        String categoryColor,

        @Positive(message = "Hạn mức phải lớn hơn 0")
        long limitAmount,

        @Min(value = 1, message = "Ngưỡng cảnh báo từ 1% đến 100%")
        @Max(value = 100, message = "Ngưỡng cảnh báo từ 1% đến 100%")
        Integer alertThreshold,

        @Size(max = 10, message = "Kỳ ngân sách tối đa 10 ký tự")
        String periodKey
) {
}
