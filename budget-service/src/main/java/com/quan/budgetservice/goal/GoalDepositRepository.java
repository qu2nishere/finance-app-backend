package com.quan.budgetservice.goal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GoalDepositRepository extends JpaRepository<GoalDeposit, UUID> {

    List<GoalDeposit> findByGoalIdAndUserIdOrderByDepositedAtDesc(UUID goalId, UUID userId);
}
