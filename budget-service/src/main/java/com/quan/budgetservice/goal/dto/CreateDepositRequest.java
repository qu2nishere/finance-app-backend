package com.quan.budgetservice.goal.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateDepositRequest(
        @Positive(message = "Số tiền nạp phải lớn hơn 0")
        long amount,

        @Size(max = 255, message = "Ghi chú tối đa 255 ký tự")
        String note
) {
}
