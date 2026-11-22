package com.quan.budgetservice.goal.dto;

public record DepositResultResponse(
        GoalDepositResponse deposit,
        GoalResponse goal
) {
}
