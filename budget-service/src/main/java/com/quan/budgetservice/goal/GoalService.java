package com.quan.budgetservice.goal;

import com.quan.budgetservice.common.security.AccountZone;

import com.quan.budgetservice.common.exception.InvalidRequestException;
import com.quan.budgetservice.common.exception.NotFoundException;
import com.quan.budgetservice.common.util.PeriodUtils;
import com.quan.budgetservice.goal.dto.CreateDepositRequest;
import com.quan.budgetservice.goal.dto.CreateGoalRequest;
import com.quan.budgetservice.goal.dto.DepositResultResponse;
import com.quan.budgetservice.goal.dto.GoalDepositResponse;
import com.quan.budgetservice.goal.dto.GoalResponse;
import com.quan.budgetservice.goal.dto.UpdateGoalRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GoalService {

    private final SavingsGoalRepository goalRepository;
    private final GoalDepositRepository depositRepository;

    @Transactional(readOnly = true)
    public List<GoalResponse> list(UUID userId, GoalStatus status) {
        List<SavingsGoal> goals = (status == null)
                ? goalRepository.findByUserIdAndDeletedAtIsNullOrderByPriorityAscCreatedAtAsc(userId)
                : goalRepository.findByUserIdAndStatusAndDeletedAtIsNullOrderByPriorityAscCreatedAtAsc(userId, status);
        return goals.stream().map(GoalResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public GoalResponse get(UUID userId, UUID goalId) {
        SavingsGoal goal = goalRepository.findByIdAndUserIdAndDeletedAtIsNull(goalId, userId)
                .orElseThrow(() -> new NotFoundException("GOAL_NOT_FOUND", "Không tìm thấy mục tiêu tiết kiệm"));
        return GoalResponse.from(goal);
    }

    @Transactional
    public GoalResponse create(UUID userId, CreateGoalRequest request) {
        LocalDate startDate = LocalDate.now(AccountZone.current());
        validateTargetDate(request.targetDate(), startDate);

        SavingsGoal goal = new SavingsGoal();
        goal.setUserId(userId);
        goal.setName(request.name().trim());
        goal.setIcon(nonBlankOrDefault(request.icon(), "CardGiftcard"));
        goal.setColor(nonBlankOrDefault(request.color(), "#16A34A"));
        goal.setTargetAmount(request.targetAmount());
        goal.setSavedAmount(0L);
        goal.setStartDate(startDate);
        goal.setTargetDate(request.targetDate());
        goal.setWalletId(request.walletId());
        goal.setWalletName(trimToNull(request.walletName()));
        goal.setPriority(request.priority() != null ? request.priority().shortValue() : (short) 0);
        goal.setStatus(GoalStatus.ACTIVE);
        goal.setNote(trimToNull(request.note()));

        return GoalResponse.from(goalRepository.save(goal));
    }

    @Transactional
    public GoalResponse update(UUID userId, UUID goalId, UpdateGoalRequest request) {
        SavingsGoal goal = goalRepository.findByIdAndUserIdAndDeletedAtIsNull(goalId, userId)
                .orElseThrow(() -> new NotFoundException("GOAL_NOT_FOUND", "Không tìm thấy mục tiêu tiết kiệm"));

        if (request.name() != null) {
            String trimmed = request.name().trim();
            if (trimmed.isEmpty()) {
                throw new InvalidRequestException("Tên mục tiêu không được để trống");
            }
            goal.setName(trimmed);
        }
        if (request.icon() != null && !request.icon().isBlank()) {
            goal.setIcon(request.icon().trim());
        }
        if (request.color() != null && !request.color().isBlank()) {
            goal.setColor(request.color().trim());
        }
        if (request.targetAmount() != null) {
            goal.setTargetAmount(request.targetAmount());
            syncCompletionStatus(goal);
        }
        if (request.targetDate() != null) {
            validateTargetDate(request.targetDate(), goal.getStartDate());
            goal.setTargetDate(request.targetDate());
        }
        if (request.walletId() != null) {
            goal.setWalletId(request.walletId());
        }
        if (request.walletName() != null) {
            goal.setWalletName(trimToNull(request.walletName()));
        }
        if (request.priority() != null) {
            goal.setPriority(request.priority().shortValue());
        }
        if (request.note() != null) {
            goal.setNote(trimToNull(request.note()));
        }

        return GoalResponse.from(goal);
    }

    @Transactional
    public void delete(UUID userId, UUID goalId) {
        SavingsGoal goal = goalRepository.findByIdAndUserIdAndDeletedAtIsNull(goalId, userId)
                .orElseThrow(() -> new NotFoundException("GOAL_NOT_FOUND", "Không tìm thấy mục tiêu tiết kiệm"));
        goal.setDeletedAt(Instant.now());
    }

    @Transactional
    public DepositResultResponse deposit(UUID userId, UUID goalId, CreateDepositRequest request) {
        SavingsGoal goal = goalRepository.findForUpdate(goalId, userId)
                .orElseThrow(() -> new NotFoundException("GOAL_NOT_FOUND", "Không tìm thấy mục tiêu tiết kiệm"));

        GoalDeposit deposit = new GoalDeposit();
        deposit.setGoalId(goal.getId());
        deposit.setUserId(userId);
        deposit.setAmount(request.amount());
        deposit.setSource(DepositSource.MANUAL);
        deposit.setWalletId(goal.getWalletId());
        deposit.setDepositedAt(Instant.now());
        deposit.setNote(trimToNull(request.note()));

        GoalDeposit savedDeposit = depositRepository.save(deposit);

        goal.setSavedAmount(goal.getSavedAmount() + request.amount());
        syncCompletionStatus(goal);

        return new DepositResultResponse(
                GoalDepositResponse.from(savedDeposit),
                GoalResponse.from(goal)
        );
    }

    @Transactional(readOnly = true)
    public List<GoalDepositResponse> listDeposits(UUID userId, UUID goalId) {
        goalRepository.findByIdAndUserIdAndDeletedAtIsNull(goalId, userId)
                .orElseThrow(() -> new NotFoundException("GOAL_NOT_FOUND", "Không tìm thấy mục tiêu tiết kiệm"));
        return depositRepository.findByGoalIdAndUserIdOrderByDepositedAtDesc(goalId, userId)
                .stream()
                .map(GoalDepositResponse::from)
                .toList();
    }

    private static void syncCompletionStatus(SavingsGoal goal) {
        if (goal.getSavedAmount() >= goal.getTargetAmount()) {
            goal.setStatus(GoalStatus.COMPLETED);
            if (goal.getCompletedAt() == null) {
                goal.setCompletedAt(Instant.now());
            }
        } else if (goal.getStatus() == GoalStatus.COMPLETED) {
            goal.setStatus(GoalStatus.ACTIVE);
            goal.setCompletedAt(null);
        }
    }

    private static void validateTargetDate(LocalDate targetDate, LocalDate startDate) {
        if (targetDate != null && startDate != null && targetDate.isBefore(startDate)) {
            throw new InvalidRequestException("INVALID_DATE_RANGE", "Ngày hoàn thành không được trước ngày bắt đầu");
        }
    }

    private static String nonBlankOrDefault(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
