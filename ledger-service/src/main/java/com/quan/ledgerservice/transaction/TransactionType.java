package com.quan.ledgerservice.transaction;

public enum TransactionType {
    EXPENSE,
    INCOME,
    TRANSFER_OUT,
    TRANSFER_IN,
    ADJUSTMENT;

    public int sign() {
        return switch (this) {
            case EXPENSE, TRANSFER_OUT -> -1;
            case INCOME, TRANSFER_IN   ->  1;
            case ADJUSTMENT            ->  0;
        };
    }
}
