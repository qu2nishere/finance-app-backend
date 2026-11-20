package com.quan.reportservice.stats;

import com.quan.reportservice.common.security.CurrentUser;
import com.quan.reportservice.stats.dto.CashflowSummaryResponse;
import com.quan.reportservice.stats.dto.MonthlyTransactionsResponse;
import com.quan.reportservice.stats.dto.MonthlyTrendResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/monthly-trend")
    public MonthlyTrendResponse getMonthlyTrend(
            @CurrentUser UUID userId,
            @RequestParam(required = false) String period,
            @RequestParam(defaultValue = "5") int months,
            @RequestParam(defaultValue = "INCOME") String type,
            @RequestParam(required = false) UUID categoryId
    ) {
        return statsService.getMonthlyTrend(userId, period, months, type, categoryId);
    }

    @GetMapping("/transactions")
    public MonthlyTransactionsResponse getTransactions(
            @CurrentUser UUID userId,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) UUID categoryId
    ) {
        return statsService.getTransactions(userId, period, date, type, categoryId);
    }

    @GetMapping("/cashflow-summary")
    public CashflowSummaryResponse getCashflowSummary(
            @CurrentUser UUID userId,
            @RequestParam(required = false) String period
    ) {
        return statsService.getCashflowSummary(userId, period);
    }
}
