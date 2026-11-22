package com.quan.budgetservice.budget;

import com.quan.budgetservice.budget.dto.CurrentBudgetResponse;
import com.quan.budgetservice.common.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/budgets")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;

    @GetMapping("/current")
    public CurrentBudgetResponse getCurrent(
            @CurrentUser UUID userId,
            @RequestParam(required = false) String period
    ) {
        return budgetService.getCurrent(userId, period);
    }
}
