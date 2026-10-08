package com.quan.ledgerservice.transaction.dto;

import com.quan.ledgerservice.transaction.TransactionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.UUID;

public record CreateTransactionRequest(
        @NotNull(message = "Ví không được để trống")
        UUID walletId,

        UUID categoryId,

        @NotNull(message = "Loại giao dịch không được để trống")
        TransactionType type,

        @Positive(message = "Số tiền phải lớn hơn 0")
        long amount,

        @NotNull(message = "Thời gian giao dịch không được để trống")
        Instant occurredAt,

        String note,
        String merchant
) {
}
