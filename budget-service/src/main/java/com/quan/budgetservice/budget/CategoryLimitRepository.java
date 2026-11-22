package com.quan.budgetservice.budget;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryLimitRepository extends JpaRepository<CategoryLimit, UUID> {

    List<CategoryLimit> findByBudgetIdAndUserIdAndActiveTrueOrderByCreatedAtAsc(UUID budgetId, UUID userId);

    boolean existsByBudgetIdAndCategoryId(UUID budgetId, UUID categoryId);

    Optional<CategoryLimit> findByIdAndUserId(UUID id, UUID userId);
}
