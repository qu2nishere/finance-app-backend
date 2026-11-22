package com.quan.budgetservice.budget;

import com.quan.budgetservice.budget.dto.CategoryLimitResponse;
import com.quan.budgetservice.budget.dto.CreateLimitRequest;
import com.quan.budgetservice.budget.dto.UpdateLimitRequest;
import com.quan.budgetservice.common.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/limits")
@RequiredArgsConstructor
public class LimitController {

    private final BudgetService budgetService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryLimitResponse create(
            @CurrentUser UUID userId,
            @Valid @RequestBody CreateLimitRequest request
    ) {
        return budgetService.createLimit(userId, request);
    }

    @PatchMapping("/{id}")
    public CategoryLimitResponse update(
            @CurrentUser UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateLimitRequest request
    ) {
        return budgetService.updateLimit(userId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @CurrentUser UUID userId,
            @PathVariable UUID id
    ) {
        budgetService.deleteLimit(userId, id);
    }
}
