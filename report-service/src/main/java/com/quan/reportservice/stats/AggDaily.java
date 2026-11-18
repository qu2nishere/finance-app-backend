package com.quan.reportservice.stats;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "agg_daily")
@IdClass(AggDailyId.class)
@Getter
@Setter
@NoArgsConstructor
public class AggDaily {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "occurred_date", nullable = false)
    private LocalDate occurredDate;

    @Column(name = "total_income", nullable = false)
    private long totalIncome;

    @Column(name = "total_expense", nullable = false)
    private long totalExpense;

    @Column(name = "net_flow", nullable = false)
    private long netFlow;

    @Column(name = "txn_count", nullable = false)
    private int txnCount;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
