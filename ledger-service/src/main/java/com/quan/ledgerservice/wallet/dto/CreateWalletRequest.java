package com.quan.ledgerservice.wallet.dto;

import com.quan.ledgerservice.wallet.WalletKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateWalletRequest(
        @NotBlank(message = "Tên ví không được để trống")
        String name,

        @NotNull(message = "Loại ví không được để trống")
        WalletKind kind,

        long initialBalance,

        String icon,

        String color,

        String note
) {
}
