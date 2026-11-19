package com.quan.reportservice.ingest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "fact_transactions")
@Getter
@Setter
@NoArgsConstructor
public class FactTransaction {

    @Id
    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "occurred_date", nullable = false)
    private LocalDate occurredDate;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "year_month", nullable = false, length = 7)
    private String yearMonth;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "year_week", nullable = false, length = 8)
    private String yearWeek;

    @Column(name = "year_num", nullable = false)
    private short yearNum;

    @Column(nullable = false, length = 15)
    private String type;

    @Column(name = "amount_abs", nullable = false)
    private long amountAbs;

    @Column(name = "amount_signed", nullable = false)
    private long amountSigned;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "VND";

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "category_name", length = 100)
    private String categoryName;

    @Column(name = "category_flow", length = 10)
    private String categoryFlow;

    @Column(name = "category_icon", length = 50)
    private String categoryIcon;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "category_color", length = 7)
    private String categoryColor;

    @Column(name = "parent_category_id")
    private UUID parentCategoryId;

    @Column(name = "parent_category_name", length = 100)
    private String parentCategoryName;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(name = "wallet_name", nullable = false, length = 100)
    private String walletName;

    @Column(name = "wallet_kind", nullable = false, length = 20)
    private String walletKind;

    @Column(length = 150)
    private String merchant;

    @Column(length = 500)
    private String note;

    @Column(nullable = false, length = 20)
    private String source;

    @Column(name = "is_excluded", nullable = false)
    private boolean excluded;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @CreationTimestamp
    @Column(name = "ingested_at", nullable = false, updatable = false)
    private Instant ingestedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
