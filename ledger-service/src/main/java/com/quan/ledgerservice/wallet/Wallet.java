package com.quan.ledgerservice.wallet;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wallets")
@Getter
@Setter
@NoArgsConstructor
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WalletKind kind;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "VND";

    @Column(name = "initial_balance", nullable = false)
    private long initialBalance;

    @Column(name = "current_balance", nullable = false)
    private long currentBalance;

    @Column(name = "credit_limit")
    private Long creditLimit;

    @Column(nullable = false, length = 50)
    private String icon = "AccountBalanceWallet";

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 7)
    private String color = "#2563EB";

    @Column(name = "bank_code", length = 20)
    private String bankCode;

    @Column(name = "account_mask", length = 10)
    private String accountMask;

    @Column(name = "is_linked", nullable = false)
    private boolean linked;

    @Column(name = "sync_enabled", nullable = false)
    private boolean syncEnabled;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "include_in_total", nullable = false)
    private boolean includeInTotal = true;

    @Column(name = "is_archived", nullable = false)
    private boolean archived;

    @Column(name = "display_order", nullable = false)
    private short displayOrder;

    @Column(length = 255)
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
