package com.quan.budgetservice.budget;

import com.quan.budgetservice.common.security.AccountZone;

import com.quan.budgetservice.budget.dto.CategoryLimitResponse;
import com.quan.budgetservice.budget.dto.CreateLimitRequest;
import com.quan.budgetservice.budget.dto.CurrentBudgetResponse;
import com.quan.budgetservice.budget.dto.UpdateLimitRequest;
import com.quan.budgetservice.common.exception.ConflictException;
import com.quan.budgetservice.common.exception.NotFoundException;
import com.quan.budgetservice.common.util.PeriodUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private static final int DEFAULT_ALERT_THRESHOLD = 80;

    private final BudgetRepository budgetRepository;
    private final CategoryLimitRepository categoryLimitRepository;
    private final SpendingSnapshotRepository spendingSnapshotRepository;

    @Transactional(readOnly = true)
    public CurrentBudgetResponse getCurrent(UUID userId, String periodParam) {
        String periodKey = resolvePeriodKey(periodParam);
        PeriodUtils.DateRange range = PeriodUtils.range(periodKey);
        int daysLeft = PeriodUtils.daysLeft(range, LocalDate.now(AccountZone.current()));

        Budget budget = budgetRepository
                .findByUserIdAndPeriodTypeAndPeriodKeyAndDeletedAtIsNull(userId, PeriodType.MONTH, periodKey)
                .orElse(null);

        if (budget == null) {
            return new CurrentBudgetResponse(
                    periodKey,
                    range.start(),
                    range.end(),
                    daysLeft,
                    0L,
                    0L,
                    List.of()
            );
        }

        List<CategoryLimit> limits = categoryLimitRepository
                .findByBudgetIdAndUserIdAndActiveTrueOrderByCreatedAtAsc(budget.getId(), userId);

        Map<UUID, Long> spentByCategory = spendingSnapshotRepository
                .findByUserIdAndPeriodKey(userId, periodKey)
                .stream()
                .collect(Collectors.toMap(
                        SpendingSnapshot::getCategoryId,
                        s -> Math.max(s.getSpentAmount(), 0L),
                        Long::sum
                ));

        List<CategoryLimitResponse> limitResponses = limits.stream()
                .map(limit -> {
                    long spent = spentByCategory.getOrDefault(limit.getCategoryId(), 0L);
                    LimitStatus status = calculateStatus(spent, limit.getLimitAmount(), limit.getAlertThreshold());
                    return CategoryLimitResponse.from(limit, spent, status);
                })
                .toList();

        long totalLimit = limitResponses.stream().mapToLong(CategoryLimitResponse::limitAmount).sum();
        long totalSpent = limitResponses.stream().mapToLong(CategoryLimitResponse::spentAmount).sum();

        return new CurrentBudgetResponse(
                periodKey,
                range.start(),
                range.end(),
                daysLeft,
                totalLimit,
                totalSpent,
                limitResponses
        );
    }

    @Transactional
    public CategoryLimitResponse createLimit(UUID userId, CreateLimitRequest request) {
        String periodKey = resolvePeriodKey(request.periodKey());
        PeriodUtils.DateRange range = PeriodUtils.range(periodKey);

        Budget budget = budgetRepository
                .findByUserIdAndPeriodTypeAndPeriodKeyAndDeletedAtIsNull(userId, PeriodType.MONTH, periodKey)
                .orElseGet(() -> createBudgetForPeriod(userId, periodKey, range));

        if (categoryLimitRepository.existsByBudgetIdAndCategoryId(budget.getId(), request.categoryId())) {
            throw new ConflictException(
                    "LIMIT_ALREADY_EXISTS",
                    "Danh mục này đã có hạn mức trong kỳ " + periodKey
            );
        }

        CategoryLimit limit = new CategoryLimit();
        limit.setBudgetId(budget.getId());
        limit.setUserId(userId);
        limit.setCategoryId(request.categoryId());
        limit.setCategoryName(request.categoryName().trim());
        limit.setCategoryIcon(trimToNull(request.categoryIcon()));
        limit.setCategoryColor(trimToNull(request.categoryColor()));
        limit.setLimitAmount(request.limitAmount());
        limit.setAlertThreshold(toThresholdDecimal(request.alertThreshold()));

        CategoryLimit saved = categoryLimitRepository.save(limit);
        long spent = findSpentAmount(userId, periodKey, saved.getCategoryId());
        LimitStatus status = calculateStatus(spent, saved.getLimitAmount(), saved.getAlertThreshold());
        return CategoryLimitResponse.from(saved, spent, status);
    }

    @Transactional
    public CategoryLimitResponse updateLimit(UUID userId, UUID limitId, UpdateLimitRequest request) {
        CategoryLimit limit = categoryLimitRepository.findByIdAndUserId(limitId, userId)
                .orElseThrow(() -> new NotFoundException("LIMIT_NOT_FOUND", "Không tìm thấy hạn mức danh mục"));

        if (request.limitAmount() != null) {
            limit.setLimitAmount(request.limitAmount());
        }
        if (request.alertThreshold() != null) {
            limit.setAlertThreshold(toThresholdDecimal(request.alertThreshold()));
        }

        String periodKey = budgetRepository.findByIdAndUserIdAndDeletedAtIsNull(limit.getBudgetId(), userId)
                .map(Budget::getPeriodKey)
                .orElseGet(PeriodUtils::currentPeriodKey);

        long spent = findSpentAmount(userId, periodKey, limit.getCategoryId());
        LimitStatus status = calculateStatus(spent, limit.getLimitAmount(), limit.getAlertThreshold());
        return CategoryLimitResponse.from(limit, spent, status);
    }

    @Transactional
    public void deleteLimit(UUID userId, UUID limitId) {
        CategoryLimit limit = categoryLimitRepository.findByIdAndUserId(limitId, userId)
                .orElseThrow(() -> new NotFoundException("LIMIT_NOT_FOUND", "Không tìm thấy hạn mức danh mục"));
        categoryLimitRepository.delete(limit);
    }

    static LimitStatus calculateStatus(long spentAmount, long limitAmount, BigDecimal alertThreshold) {
        if (limitAmount <= 0 || spentAmount <= 0) {
            return LimitStatus.OK;
        }
        if (spentAmount > limitAmount) {
            return LimitStatus.EXCEEDED;
        }
        BigDecimal threshold = alertThreshold != null ? alertThreshold : new BigDecimal("0.80");
        BigDecimal warningMark = BigDecimal.valueOf(limitAmount).multiply(threshold);
        if (BigDecimal.valueOf(spentAmount).compareTo(warningMark) >= 0) {
            return LimitStatus.WARNING;
        }
        return LimitStatus.OK;
    }

    private Budget createBudgetForPeriod(UUID userId, String periodKey, PeriodUtils.DateRange range) {
        Budget budget = new Budget();
        budget.setUserId(userId);
        budget.setPeriodType(PeriodType.MONTH);
        budget.setPeriodKey(periodKey);
        budget.setPeriodStart(range.start());
        budget.setPeriodEnd(range.end());
        return budgetRepository.save(budget);
    }

    private long findSpentAmount(UUID userId, String periodKey, UUID categoryId) {
        return spendingSnapshotRepository
                .findByUserIdAndPeriodKeyAndCategoryId(userId, periodKey, categoryId)
                .map(s -> Math.max(s.getSpentAmount(), 0L))
                .orElse(0L);
    }

    private static String resolvePeriodKey(String periodParam) {
        return (periodParam == null || periodParam.isBlank())
                ? PeriodUtils.currentPeriodKey()
                : periodParam.trim();
    }

    private static BigDecimal toThresholdDecimal(Integer alertThreshold) {
        int pct = alertThreshold != null ? alertThreshold : DEFAULT_ALERT_THRESHOLD;
        return BigDecimal.valueOf(pct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
