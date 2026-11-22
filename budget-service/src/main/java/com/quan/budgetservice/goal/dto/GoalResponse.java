package com.quan.budgetservice.goal.dto;

import com.quan.budgetservice.goal.GoalStatus;
import com.quan.budgetservice.goal.SavingsGoal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record GoalResponse(
        UUID id,
        String name,
        String icon,
        String color,
        long targetAmount,
        long savedAmount,
        LocalDate targetDate,
        UUID walletId,
        String walletName,
        int priority,
        GoalStatus status,
        Instant completedAt,
        String note
) {
    public static GoalResponse from(SavingsGoal g) {
        String trimmedColor = g.getColor() != null ? g.getColor().trim() : "#16A34A";
        return new GoalResponse(
                g.getId(),
                g.getName(),
                g.getIcon(),
                trimmedColor,
                g.getTargetAmount(),
                g.getSavedAmount(),
                g.getTargetDate(),
                g.getWalletId(),
                g.getWalletName(),
                g.getPriority(),
                g.getStatus(),
                g.getCompletedAt(),
                g.getNote()
        );
    }
}
