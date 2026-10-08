package com.quan.ledgerservice.wallet.dto;

import com.quan.ledgerservice.wallet.Wallet;
import com.quan.ledgerservice.wallet.WalletKind;

import java.util.UUID;

public record WalletResponse(
        UUID id,
        String name,
        WalletKind kind,
        String currencyCode,
        long initialBalance,
        long currentBalance,
        String icon,
        String color,
        boolean includeInTotal,
        short displayOrder,
        String note
) {
    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(
                wallet.getId(),
                wallet.getName(),
                wallet.getKind(),
                wallet.getCurrencyCode(),
                wallet.getInitialBalance(),
                wallet.getCurrentBalance(),
                wallet.getIcon(),
                wallet.getColor(),
                wallet.isIncludeInTotal(),
                wallet.getDisplayOrder(),
                wallet.getNote()
        );
    }
}
