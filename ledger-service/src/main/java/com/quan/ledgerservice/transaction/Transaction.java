package com.quan.ledgerservice.transaction;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(name = "category_id")
    private UUID categoryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private TransactionType type;

    @Column(nullable = false)
    private long amount;

    @Column(name = "signed_amount", nullable = false)
    private long signedAmount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "VND";

    @Column(name = "exchange_rate", nullable = false, precision = 18, scale = 8)
    private BigDecimal exchangeRate = BigDecimal.ONE;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "occurred_date", nullable = false)
    private LocalDate occurredDate;

    @Column(length = 500)
    private String note;

    @Column(length = 150)
    private String merchant;

    @Column(length = 200)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionSource source = TransactionSource.MANUAL;

    @Column(name = "external_ref", length = 120)
    private String externalRef;

    @Column(name = "transfer_group_id")
    private UUID transferGroupId;

    @Column(name = "counterpart_wallet_id")
    private UUID counterpartWalletId;

    @Column(name = "recurring_rule_id")
    private UUID recurringRuleId;

    @Column(name = "receipt_draft_id")
    private UUID receiptDraftId;

    @Column(name = "parent_transaction_id")
    private UUID parentTransactionId;

    @Column(name = "is_excluded_from_report", nullable = false)
    private boolean excludedFromReport;

    @Column(name = "is_pending", nullable = false)
    private boolean pending;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
