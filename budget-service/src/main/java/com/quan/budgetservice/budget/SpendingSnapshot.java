package com.quan.budgetservice.budget;

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
import java.util.UUID;

@Entity
@Table(name = "spending_snapshot")
@IdClass(SpendingSnapshotId.class)
@Getter
@Setter
@NoArgsConstructor
public class SpendingSnapshot {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "period_key", nullable = false, length = 10)
    private String periodKey;

    @Id
    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "spent_amount", nullable = false)
    private long spentAmount = 0L;

    @Column(name = "txn_count", nullable = false)
    private int txnCount = 0;

    @Column(name = "last_event_at")
    private Instant lastEventAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
