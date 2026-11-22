package com.quan.budgetservice.budget;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    Optional<Budget> findByUserIdAndPeriodTypeAndPeriodKeyAndDeletedAtIsNull(
            UUID userId,
            PeriodType periodType,
            String periodKey
    );

    Optional<Budget> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);
}
