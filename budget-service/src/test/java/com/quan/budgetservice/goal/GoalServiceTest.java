package com.quan.budgetservice.goal;

import com.quan.budgetservice.common.exception.NotFoundException;
import com.quan.budgetservice.goal.dto.CreateDepositRequest;
import com.quan.budgetservice.goal.dto.DepositResultResponse;
import com.quan.budgetservice.goal.dto.GoalResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoalServiceTest {

    @Mock
    private SavingsGoalRepository goalRepository;
    @Mock
    private GoalDepositRepository depositRepository;

    private GoalService goalService;

    @BeforeEach
    void setUp() {
        goalService = new GoalService(goalRepository, depositRepository);
    }

    @Test
    void deposit_reachingTarget_marksGoalCompleted() {
        UUID userId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();

        SavingsGoal goal = new SavingsGoal();
        goal.setId(goalId);
        goal.setUserId(userId);
        goal.setName("Du lịch Đà Lạt");
        goal.setTargetAmount(10_000_000L);
        goal.setSavedAmount(4_500_000L);
        goal.setStatus(GoalStatus.ACTIVE);

        when(goalRepository.findForUpdate(goalId, userId)).thenReturn(Optional.of(goal));
        when(depositRepository.save(any(GoalDeposit.class))).thenAnswer(inv -> {
            GoalDeposit d = inv.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });

        DepositResultResponse res = goalService.deposit(userId, goalId, new CreateDepositRequest(5_500_000L, "Hoàn thành"));

        assertThat(res.deposit().amount()).isEqualTo(5_500_000L);
        assertThat(res.goal().savedAmount()).isEqualTo(10_000_000L);
        assertThat(res.goal().status()).isEqualTo(GoalStatus.COMPLETED);
        assertThat(res.goal().completedAt()).isNotNull();
    }

    @Test
    void deposit_forAnotherUsersGoal_throwsNotFound() {
        UUID otherUser = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();

        when(goalRepository.findForUpdate(goalId, otherUser)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> goalService.deposit(otherUser, goalId, new CreateDepositRequest(1_000_000L, null)))
                .isInstanceOf(NotFoundException.class)
                .extracting("code")
                .isEqualTo("GOAL_NOT_FOUND");
    }

    @Test
    void delete_softDeletesGoalAndExcludesFromList() {
        UUID userId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();

        SavingsGoal goal = new SavingsGoal();
        goal.setId(goalId);
        goal.setUserId(userId);
        goal.setName("Quỹ dự phòng");
        goal.setTargetAmount(50_000_000L);

        when(goalRepository.findByIdAndUserIdAndDeletedAtIsNull(goalId, userId)).thenReturn(Optional.of(goal));
        when(goalRepository.findByUserIdAndDeletedAtIsNullOrderByPriorityAscCreatedAtAsc(userId)).thenReturn(List.of());

        goalService.delete(userId, goalId);

        assertThat(goal.getDeletedAt()).isNotNull();
        List<GoalResponse> remaining = goalService.list(userId, null);
        assertThat(remaining).isEmpty();
    }
}
