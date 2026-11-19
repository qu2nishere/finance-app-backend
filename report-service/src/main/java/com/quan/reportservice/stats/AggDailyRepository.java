package com.quan.reportservice.stats;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface AggDailyRepository extends JpaRepository<AggDaily, AggDailyId> {

    Optional<AggDaily> findByUserIdAndOccurredDate(UUID userId, LocalDate occurredDate);
}
