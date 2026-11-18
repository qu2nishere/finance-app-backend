package com.quan.reportservice.stats;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AggMonthlySummaryRepository extends JpaRepository<AggMonthlySummary, AggMonthlySummaryId> {

    Optional<AggMonthlySummary> findByUserIdAndYearMonth(UUID userId, String yearMonth);

    List<AggMonthlySummary> findByUserIdAndYearMonthIn(UUID userId, Collection<String> yearMonths);
}
