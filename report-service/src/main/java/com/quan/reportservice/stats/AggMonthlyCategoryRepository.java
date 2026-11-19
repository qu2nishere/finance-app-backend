package com.quan.reportservice.stats;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AggMonthlyCategoryRepository extends JpaRepository<AggMonthlyCategory, AggMonthlyCategoryId> {

    List<AggMonthlyCategory> findByUserIdAndYearMonth(UUID userId, String yearMonth);

    List<AggMonthlyCategory> findByUserIdAndYearMonthAndCategoryFlowOrderByTotalAmountDesc(
            UUID userId, String yearMonth, String categoryFlow);

    List<AggMonthlyCategory> findByUserIdAndCategoryIdAndYearMonthIn(
            UUID userId, UUID categoryId, Collection<String> yearMonths);

    @Modifying
    @Query("delete from AggMonthlyCategory a where a.userId = :userId and a.yearMonth = :yearMonth")
    void deleteByUserIdAndYearMonth(@Param("userId") UUID userId, @Param("yearMonth") String yearMonth);
}
