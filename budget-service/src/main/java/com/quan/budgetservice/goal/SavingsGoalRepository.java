package com.quan.budgetservice.goal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, UUID> {

    List<SavingsGoal> findByUserIdAndDeletedAtIsNullOrderByPriorityAscCreatedAtAsc(UUID userId);

    List<SavingsGoal> findByUserIdAndStatusAndDeletedAtIsNullOrderByPriorityAscCreatedAtAsc(
            UUID userId,
            GoalStatus status
    );

    Optional<SavingsGoal> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from SavingsGoal g where g.id = :id and g.userId = :userId and g.deletedAt is null")
    Optional<SavingsGoal> findForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);
}
