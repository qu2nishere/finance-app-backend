package com.quan.ledgerservice.wallet;

import java.time.Instant;
import java.util.UUID;

public record WalletCreatedPayload(
        UUID walletId,
        String name,
        WalletKind kind,
        String color,
        long initialBalance,
        String currencyCode,
        boolean includeInTotal,
        Instant createdAt
) {
    public static WalletCreatedPayload from(Wallet wallet) {
        return new WalletCreatedPayload(
                wallet.getId(),
                wallet.getName(),
                wallet.getKind(),
                wallet.getColor() != null ? wallet.getColor().trim() : null,
                wallet.getInitialBalance(),
                wallet.getCurrencyCode() != null ? wallet.getCurrencyCode().trim() : "VND",
                wallet.isIncludeInTotal(),
                wallet.getCreatedAt() != null ? wallet.getCreatedAt() : Instant.now()
        );
    }
}
