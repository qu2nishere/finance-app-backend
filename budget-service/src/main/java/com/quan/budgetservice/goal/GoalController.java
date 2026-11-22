package com.quan.budgetservice.goal;

import com.quan.budgetservice.common.security.CurrentUser;
import com.quan.budgetservice.goal.dto.CreateDepositRequest;
import com.quan.budgetservice.goal.dto.CreateGoalRequest;
import com.quan.budgetservice.goal.dto.DepositResultResponse;
import com.quan.budgetservice.goal.dto.GoalDepositResponse;
import com.quan.budgetservice.goal.dto.GoalResponse;
import com.quan.budgetservice.goal.dto.UpdateGoalRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;

    @GetMapping
    public List<GoalResponse> list(
            @CurrentUser UUID userId,
            @RequestParam(required = false) GoalStatus status
    ) {
        return goalService.list(userId, status);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GoalResponse create(
            @CurrentUser UUID userId,
            @Valid @RequestBody CreateGoalRequest request
    ) {
        return goalService.create(userId, request);
    }

    @GetMapping("/{id}")
    public GoalResponse get(
            @CurrentUser UUID userId,
            @PathVariable UUID id
    ) {
        return goalService.get(userId, id);
    }

    @PatchMapping("/{id}")
    public GoalResponse update(
            @CurrentUser UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateGoalRequest request
    ) {
        return goalService.update(userId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @CurrentUser UUID userId,
            @PathVariable UUID id
    ) {
        goalService.delete(userId, id);
    }

    @PostMapping("/{id}/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    public DepositResultResponse deposit(
            @CurrentUser UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody CreateDepositRequest request
    ) {
        return goalService.deposit(userId, id, request);
    }

    @GetMapping("/{id}/deposits")
    public List<GoalDepositResponse> listDeposits(
            @CurrentUser UUID userId,
            @PathVariable UUID id
    ) {
        return goalService.listDeposits(userId, id);
    }
}
