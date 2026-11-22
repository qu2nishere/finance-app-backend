package com.quan.budgetservice.budget;

import com.quan.budgetservice.budget.dto.CategoryLimitResponse;
import com.quan.budgetservice.budget.dto.CreateLimitRequest;
import com.quan.budgetservice.budget.dto.CurrentBudgetResponse;
import com.quan.budgetservice.budget.dto.UpdateLimitRequest;
import com.quan.budgetservice.common.exception.ConflictException;
import com.quan.budgetservice.common.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    @Mock
    private BudgetRepository budgetRepository;
    @Mock
    private CategoryLimitRepository categoryLimitRepository;
    @Mock
    private SpendingSnapshotRepository spendingSnapshotRepository;

    private BudgetService budgetService;

    @BeforeEach
    void setUp() {
        budgetService = new BudgetService(budgetRepository, categoryLimitRepository, spendingSnapshotRepository);
    }

    @Test
    void createLimit_createsBudgetAutomaticallyAndReturnsResponse() {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID budgetId = UUID.randomUUID();
        UUID limitId = UUID.randomUUID();

        when(budgetRepository.findByUserIdAndPeriodTypeAndPeriodKeyAndDeletedAtIsNull(userId, PeriodType.MONTH, "2026-11"))
                .thenReturn(Optional.empty());
        when(budgetRepository.save(any(Budget.class))).thenAnswer(inv -> {
            Budget b = inv.getArgument(0);
            b.setId(budgetId);
            return b;
        });
        when(categoryLimitRepository.existsByBudgetIdAndCategoryId(budgetId, categoryId)).thenReturn(false);
        when(categoryLimitRepository.save(any(CategoryLimit.class))).thenAnswer(inv -> {
            CategoryLimit l = inv.getArgument(0);
            l.setId(limitId);
            return l;
        });
        when(spendingSnapshotRepository.findByUserIdAndPeriodKeyAndCategoryId(userId, "2026-11", categoryId))
                .thenReturn(Optional.empty());

        CategoryLimitResponse resp = budgetService.createLimit(userId, new CreateLimitRequest(
                categoryId, "Ăn uống", "Storefront", "#EF4444", 3_000_000L, 80, "2026-11"
        ));

        assertThat(resp.id()).isEqualTo(limitId);
        assertThat(resp.categoryName()).isEqualTo("Ăn uống");
        assertThat(resp.limitAmount()).isEqualTo(3_000_000L);
        assertThat(resp.spentAmount()).isEqualTo(0L);
        assertThat(resp.alertThreshold()).isEqualTo(80);
        assertThat(resp.status()).isEqualTo(LimitStatus.OK);
    }

    @Test
    void createLimit_whenCategoryAlreadyHasLimit_throwsConflict() {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        Budget budget = new Budget();
        budget.setId(UUID.randomUUID());
        budget.setUserId(userId);
        budget.setPeriodKey("2026-11");

        when(budgetRepository.findByUserIdAndPeriodTypeAndPeriodKeyAndDeletedAtIsNull(userId, PeriodType.MONTH, "2026-11"))
                .thenReturn(Optional.of(budget));
        when(categoryLimitRepository.existsByBudgetIdAndCategoryId(budget.getId(), categoryId)).thenReturn(true);

        assertThatThrownBy(() -> budgetService.createLimit(userId, new CreateLimitRequest(
                categoryId, "Ăn uống", "Storefront", "#EF4444", 3_000_000L, 80, "2026-11"
        )))
                .isInstanceOf(ConflictException.class)
                .extracting("code")
                .isEqualTo("LIMIT_ALREADY_EXISTS");
    }

    @Test
    void updateAndDeleteLimit_forDifferentUser_throwsNotFound() {
        UUID userId = UUID.randomUUID();
        UUID limitId = UUID.randomUUID();

        when(categoryLimitRepository.findByIdAndUserId(limitId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> budgetService.updateLimit(userId, limitId, new UpdateLimitRequest(5_000_000L, 90)))
                .isInstanceOf(NotFoundException.class);

        assertThatThrownBy(() -> budgetService.deleteLimit(userId, limitId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getCurrent_calculatesWarningAndExceededStatusesAndTotals() {
        UUID userId = UUID.randomUUID();
        UUID budgetId = UUID.randomUUID();
        UUID catFood = UUID.randomUUID();
        UUID catMove = UUID.randomUUID();
        UUID catShop = UUID.randomUUID();

        Budget budget = new Budget();
        budget.setId(budgetId);
        budget.setUserId(userId);
        budget.setPeriodKey("2026-11");
        budget.setPeriodStart(LocalDate.of(2026, 11, 1));
        budget.setPeriodEnd(LocalDate.of(2026, 11, 30));

        CategoryLimit lFood = makeLimit(budgetId, userId, catFood, "Ăn uống", 3_000_000L, "0.80");
        CategoryLimit lMove = makeLimit(budgetId, userId, catMove, "Di chuyển", 2_000_000L, "0.80");
        CategoryLimit lShop = makeLimit(budgetId, userId, catShop, "Mua sắm", 2_000_000L, "0.80");

        SpendingSnapshot sFood = makeSnapshot(userId, "2026-11", catFood, 3_200_000L);
        SpendingSnapshot sMove = makeSnapshot(userId, "2026-11", catMove, 1_600_000L);
        SpendingSnapshot sShop = makeSnapshot(userId, "2026-11", catShop, 500_000L);

        when(budgetRepository.findByUserIdAndPeriodTypeAndPeriodKeyAndDeletedAtIsNull(userId, PeriodType.MONTH, "2026-11"))
                .thenReturn(Optional.of(budget));
        when(categoryLimitRepository.findByBudgetIdAndUserIdAndActiveTrueOrderByCreatedAtAsc(budgetId, userId))
                .thenReturn(List.of(lFood, lMove, lShop));
        when(spendingSnapshotRepository.findByUserIdAndPeriodKey(userId, "2026-11"))
                .thenReturn(List.of(sFood, sMove, sShop));

        CurrentBudgetResponse resp = budgetService.getCurrent(userId, "2026-11");

        assertThat(resp.totalLimit()).isEqualTo(7_000_000L);
        assertThat(resp.totalSpent()).isEqualTo(5_300_000L);
        assertThat(resp.limits()).hasSize(3);
        assertThat(resp.limits().get(0).status()).isEqualTo(LimitStatus.EXCEEDED);
        assertThat(resp.limits().get(1).status()).isEqualTo(LimitStatus.WARNING);
        assertThat(resp.limits().get(2).status()).isEqualTo(LimitStatus.OK);
    }

    @Test
    void deleteLimit_deletesOwnLimit() {
        UUID userId = UUID.randomUUID();
        UUID limitId = UUID.randomUUID();
        CategoryLimit limit = new CategoryLimit();
        limit.setId(limitId);
        limit.setUserId(userId);

        when(categoryLimitRepository.findByIdAndUserId(limitId, userId)).thenReturn(Optional.of(limit));

        budgetService.deleteLimit(userId, limitId);

        verify(categoryLimitRepository).delete(limit);
    }

    private static CategoryLimit makeLimit(UUID budgetId, UUID userId, UUID catId, String name, long amount, String threshold) {
        CategoryLimit l = new CategoryLimit();
        l.setId(UUID.randomUUID());
        l.setBudgetId(budgetId);
        l.setUserId(userId);
        l.setCategoryId(catId);
        l.setCategoryName(name);
        l.setLimitAmount(amount);
        l.setAlertThreshold(new BigDecimal(threshold));
        return l;
    }

    private static SpendingSnapshot makeSnapshot(UUID userId, String periodKey, UUID catId, long spent) {
        SpendingSnapshot s = new SpendingSnapshot();
        s.setUserId(userId);
        s.setPeriodKey(periodKey);
        s.setCategoryId(catId);
        s.setSpentAmount(spent);
        return s;
    }
}
