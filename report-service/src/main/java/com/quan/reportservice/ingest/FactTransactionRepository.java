package com.quan.reportservice.ingest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FactTransactionRepository extends JpaRepository<FactTransaction, UUID> {

    Optional<FactTransaction> findByTransactionIdAndUserId(UUID transactionId, UUID userId);

    @Query("""
           select f from FactTransaction f
           where f.userId = :userId
             and f.yearMonth = :yearMonth
             and f.deleted = false
             and f.excluded = false
             and f.type in ('INCOME', 'EXPENSE')
           order by f.occurredDate desc, f.occurredAt desc
           """)
    List<FactTransaction> findActiveReportableByMonth(
            @Param("userId") UUID userId,
            @Param("yearMonth") String yearMonth
    );

    @Query("""
           select f from FactTransaction f
           where f.userId = :userId
             and f.occurredDate = :occurredDate
             and f.deleted = false
             and f.excluded = false
             and f.type in ('INCOME', 'EXPENSE')
           """)
    List<FactTransaction> findActiveReportableByDate(
            @Param("userId") UUID userId,
            @Param("occurredDate") LocalDate occurredDate
    );

    interface WalletSignedSumProjection {
        UUID getWalletId();
        long getSignedTotal();
    }

    interface WalletMonthFlowProjection {
        UUID getWalletId();
        long getSignedTotal();
        long getIncomeTotal();
        long getExpenseTotal();
        long getTxnCount();
    }

    @Query("""
           select f.walletId as walletId,
                  coalesce(sum(f.amountSigned), 0L) as signedTotal
           from FactTransaction f
           where f.userId = :userId
             and f.occurredDate < :beforeDate
             and f.deleted = false
             and f.excluded = false
           group by f.walletId
           """)
    List<WalletSignedSumProjection> sumSignedBeforeDateByWallet(
            @Param("userId") UUID userId,
            @Param("beforeDate") LocalDate beforeDate
    );

    @Query("""
           select f.walletId as walletId,
                  coalesce(sum(f.amountSigned), 0L) as signedTotal,
                  coalesce(sum(case when f.type = 'INCOME' then f.amountAbs else 0L end), 0L) as incomeTotal,
                  coalesce(sum(case when f.type = 'EXPENSE' then f.amountAbs else 0L end), 0L) as expenseTotal,
                  count(f) as txnCount
           from FactTransaction f
           where f.userId = :userId
             and f.yearMonth = :yearMonth
             and f.deleted = false
             and f.excluded = false
           group by f.walletId
           """)
    List<WalletMonthFlowProjection> sumMonthFlowByWallet(
            @Param("userId") UUID userId,
            @Param("yearMonth") String yearMonth
    );
}
