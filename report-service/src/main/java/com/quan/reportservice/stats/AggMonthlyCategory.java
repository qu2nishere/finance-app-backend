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
@Table(name = "agg_monthly_category")
@IdClass(AggMonthlyCategoryId.class)
@Getter
@Setter
@NoArgsConstructor
public class AggMonthlyCategory {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "year_month", nullable = false, length = 7)
    private String yearMonth;

    @Id
    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "category_name", nullable = false, length = 100)
    private String categoryName;

    @Column(name = "category_flow", nullable = false, length = 10)
    private String categoryFlow;

    @Column(name = "category_icon", length = 50)
    private String categoryIcon;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "category_color", length = 7)
    private String categoryColor;

    @Column(name = "total_amount", nullable = false)
    private long totalAmount;

    @Column(name = "txn_count", nullable = false)
    private int txnCount;

    @Column(name = "avg_amount", nullable = false)
    private long avgAmount;

    @Column(name = "share_percent", precision = 5, scale = 2)
    private BigDecimal sharePercent;

    @Column(name = "mom_change_percent", precision = 6, scale = 2)
    private BigDecimal momChangePercent;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
