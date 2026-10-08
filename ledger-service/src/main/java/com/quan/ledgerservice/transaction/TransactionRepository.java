package com.quan.ledgerservice.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Page<Transaction> findByUserIdAndDeletedAtIsNullOrderByOccurredAtDesc(UUID userId, Pageable pageable);

    Page<Transaction> findByUserIdAndWalletIdAndDeletedAtIsNullOrderByOccurredAtDesc(
            UUID userId, UUID walletId, Pageable pageable);

    Optional<Transaction> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    @Query("""
           select coalesce(sum(t.amount), 0) from Transaction t
           where t.userId = :userId
             and t.type = :type
             and t.occurredDate between :startDate and :endDate
             and t.deletedAt is null
             and t.excludedFromReport = false
           """)
    long sumAmountByTypeAndPeriod(
            @Param("userId") UUID userId,
            @Param("type") TransactionType type,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
