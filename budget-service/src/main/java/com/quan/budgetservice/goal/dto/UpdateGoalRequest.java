package com.quan.budgetservice.goal.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record UpdateGoalRequest(
        @Size(max = 100, message = "Tên mục tiêu tối đa 100 ký tự")
        String name,

        @Size(max = 50, message = "Tên biểu tượng tối đa 50 ký tự")
        String icon,

        @Size(max = 7, message = "Mã màu tối đa 7 ký tự")
        String color,

        @Positive(message = "Số tiền mục tiêu phải lớn hơn 0")
        Long targetAmount,

        LocalDate targetDate,

        UUID walletId,

        @Size(max = 100, message = "Tên ví tối đa 100 ký tự")
        String walletName,

        @Min(value = 0, message = "Độ ưu tiên không được âm")
        Integer priority,

        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
        String note
) {
}
