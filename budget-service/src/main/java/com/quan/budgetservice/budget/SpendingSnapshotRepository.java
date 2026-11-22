package com.quan.budgetservice.budget;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpendingSnapshotRepository extends JpaRepository<SpendingSnapshot, SpendingSnapshotId> {

    List<SpendingSnapshot> findByUserIdAndPeriodKey(UUID userId, String periodKey);

    Optional<SpendingSnapshot> findByUserIdAndPeriodKeyAndCategoryId(UUID userId, String periodKey, UUID categoryId);

    @Modifying
    @Query(value = """
            INSERT INTO spending_snapshot (user_id, period_key, category_id, spent_amount, txn_count, last_event_at, updated_at)
            VALUES (:userId, :periodKey, :categoryId, GREATEST(:deltaAmount, 0), GREATEST(:deltaCount, 0), :eventAt, now())
            ON CONFLICT (user_id, period_key, category_id) DO UPDATE
            SET spent_amount = GREATEST(spending_snapshot.spent_amount + :deltaAmount, 0),
                txn_count = GREATEST(spending_snapshot.txn_count + :deltaCount, 0),
                last_event_at = COALESCE(:eventAt, spending_snapshot.last_event_at),
                updated_at = now()
            """, nativeQuery = true)
    void upsertDelta(@Param("userId") UUID userId,
                     @Param("periodKey") String periodKey,
                     @Param("categoryId") UUID categoryId,
                     @Param("deltaAmount") long deltaAmount,
                     @Param("deltaCount") int deltaCount,
                     @Param("eventAt") Instant eventAt);
}
