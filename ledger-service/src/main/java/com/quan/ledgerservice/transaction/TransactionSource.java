package com.quan.ledgerservice.transaction;

public enum TransactionSource {
    MANUAL,
    NOTIFICATION,
    SMS,
    OCR,
    RECURRING,
    IMPORT,
    AUTO_SAVE
}
