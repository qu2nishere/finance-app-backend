package com.quan.reportservice.stats;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agg_monthly_summary")
@IdClass(AggMonthlySummaryId.class)
@Getter
@Setter
@NoArgsConstructor
public class AggMonthlySummary {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "year_month", nullable = false, length = 7)
    private String yearMonth;

    @Column(name = "total_income", nullable = false)
    private long totalIncome;

    @Column(name = "total_expense", nullable = false)
    private long totalExpense;

    @Column(name = "net_cashflow", nullable = false)
    private long netCashflow;

    @Column(name = "savings_rate", precision = 5, scale = 2)
    private BigDecimal savingsRate;

    @Column(name = "opening_balance", nullable = false)
    private long openingBalance;

    @Column(name = "closing_balance", nullable = false)
    private long closingBalance;

    @Column(name = "income_txn_count", nullable = false)
    private int incomeTxnCount;

    @Column(name = "expense_txn_count", nullable = false)
    private int expenseTxnCount;

    @Column(name = "top_category_id")
    private UUID topCategoryId;

    @Column(name = "top_category_name", length = 100)
    private String topCategoryName;

    @Column(name = "income_mom_percent", precision = 6, scale = 2)
    private BigDecimal incomeMomPercent;

    @Column(name = "expense_mom_percent", precision = 6, scale = 2)
    private BigDecimal expenseMomPercent;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
