package com.quan.budgetservice.goal.dto;

import com.quan.budgetservice.goal.GoalDeposit;

import java.time.Instant;
import java.util.UUID;

public record GoalDepositResponse(
        UUID id,
        long amount,
        Instant depositedAt,
        String note
) {
    public static GoalDepositResponse from(GoalDeposit d) {
        return new GoalDepositResponse(
                d.getId(),
                d.getAmount(),
                d.getDepositedAt(),
                d.getNote()
        );
    }
}
