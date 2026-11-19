package com.quan.reportservice.stats;

import com.quan.reportservice.common.exception.InvalidRequestException;
import com.quan.reportservice.ingest.FactTransaction;
import com.quan.reportservice.ingest.FactTransactionRepository;
import com.quan.reportservice.stats.dto.CategoryBreakdownItemResponse;
import com.quan.reportservice.stats.dto.MonthlyTransactionsResponse;
import com.quan.reportservice.stats.dto.MonthlyTrendResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock
    private AggMonthlySummaryRepository aggMonthlySummaryRepository;
    @Mock
    private AggMonthlyCategoryRepository aggMonthlyCategoryRepository;
    @Mock
    private FactTransactionRepository factTransactionRepository;

    @InjectMocks
    private StatsService statsService;

    @Test
    void getMonthlyTrend_whenPreviousMonthZero_returnsNullMomPercentAndFills5Months() {
        UUID userId = UUID.randomUUID();

        AggMonthlySummary aug = new AggMonthlySummary();
        aug.setUserId(userId);
        aug.setYearMonth("2026-08");
        aug.setTotalIncome(18_200_000L);
        aug.setIncomeTxnCount(2);

        when(aggMonthlySummaryRepository.findByUserIdAndYearMonthIn(eq(userId), anyCollection()))
                .thenReturn(List.of(aug));
        when(aggMonthlyCategoryRepository.findByUserIdAndYearMonthAndCategoryFlowOrderByTotalAmountDesc(
                userId, "2026-08", "INCOME"))
                .thenReturn(List.of());

        MonthlyTrendResponse resp = statsService.getMonthlyTrend(userId, "2026-08", 5, "INCOME", null);

        assertThat(resp.period()).isEqualTo("2026-08");
        assertThat(resp.selectedAmount()).isEqualTo(18_200_000L);
        assertThat(resp.previousAmount()).isZero();
        assertThat(resp.momChangeAmount()).isEqualTo(18_200_000L);
        assertThat(resp.momChangePercent()).isNull();
        assertThat(resp.points()).hasSize(5);
        assertThat(resp.points().get(0).period()).isEqualTo("2026-04");
        assertThat(resp.points().get(0).totalAmount()).isZero();
        assertThat(resp.points().get(4).period()).isEqualTo("2026-08");
        assertThat(resp.points().get(4).totalAmount()).isEqualTo(18_200_000L);
        assertThat(resp.points().get(4).isSelected()).isTrue();
        // D9: 18.2tr trong summary ma khong co agg_monthly_category -> sinh nhom "Chưa phân loại"
        assertThat(resp.categoryBreakdown()).hasSize(1);
        CategoryBreakdownItemResponse uncat = resp.categoryBreakdown().get(0);
        assertThat(uncat.categoryId()).isNull();
        assertThat(uncat.categoryName()).isEqualTo("Chưa phân loại");
        assertThat(uncat.totalAmount()).isEqualTo(18_200_000L);
    }

    @Test
    void getMonthlyTrend_withPreviousMonthAndCategoryFilter_computesExactMomPercent() {
        UUID userId = UUID.randomUUID();
        UUID salaryId = UUID.randomUUID();

        AggMonthlyCategory jul = new AggMonthlyCategory();
        jul.setUserId(userId);
        jul.setYearMonth("2026-07");
        jul.setCategoryId(salaryId);
        jul.setCategoryName("Lương");
        jul.setCategoryFlow("INCOME");
        jul.setTotalAmount(15_000_000L);
        jul.setTxnCount(1);

        AggMonthlyCategory aug = new AggMonthlyCategory();
        aug.setUserId(userId);
        aug.setYearMonth("2026-08");
        aug.setCategoryId(salaryId);
        aug.setCategoryName("Lương");
        aug.setCategoryFlow("INCOME");
        aug.setTotalAmount(18_000_000L);
        aug.setTxnCount(1);

        when(aggMonthlySummaryRepository.findByUserIdAndYearMonthIn(eq(userId), anyCollection()))
                .thenReturn(List.of());
        when(aggMonthlyCategoryRepository.findByUserIdAndCategoryIdAndYearMonthIn(
                eq(userId), eq(salaryId), anyCollection()))
                .thenReturn(List.of(jul, aug));
        when(aggMonthlyCategoryRepository.findByUserIdAndYearMonthAndCategoryFlowOrderByTotalAmountDesc(
                userId, "2026-08", "INCOME"))
                .thenReturn(List.of(aug));

        MonthlyTrendResponse resp = statsService.getMonthlyTrend(userId, "2026-08", 5, "INCOME", salaryId);

        assertThat(resp.selectedAmount()).isEqualTo(18_000_000L);
        assertThat(resp.previousAmount()).isEqualTo(15_000_000L);
        assertThat(resp.momChangeAmount()).isEqualTo(3_000_000L);
        assertThat(resp.momChangePercent()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(resp.points().get(3).totalAmount()).isEqualTo(15_000_000L);
        assertThat(resp.points().get(4).totalAmount()).isEqualTo(18_000_000L);
    }

    @Test
    void getTransactions_wholeMonthVsSpecificDate_keepsMonthSummaryAndFiltersDays() {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();

        FactTransaction d22a = fact(userId, walletId, "EXPENSE", 72_000L, -72_000L,
                LocalDate.of(2026, 8, 22), Instant.parse("2026-08-22T02:12:00Z"), "Highlands Coffee");
        FactTransaction d22b = fact(userId, walletId, "EXPENSE", 168_000L, -168_000L,
                LocalDate.of(2026, 8, 22), Instant.parse("2026-08-22T11:45:00Z"), "WinMart");
        FactTransaction d21 = fact(userId, walletId, "EXPENSE", 55_000L, -55_000L,
                LocalDate.of(2026, 8, 21), Instant.parse("2026-08-21T05:15:00Z"), "Bún chả");

        when(factTransactionRepository.findActiveReportableByMonth(userId, "2026-08"))
                .thenReturn(List.of(d22b, d22a, d21));

        // 1. Xem ca thang
        MonthlyTransactionsResponse fullMonth =
                statsService.getTransactions(userId, "2026-08", null, "EXPENSE", null);
        assertThat(fullMonth.monthTotalAmount()).isEqualTo(295_000L);
        assertThat(fullMonth.monthTxnCount()).isEqualTo(3);
        assertThat(fullMonth.filteredTotalAmount()).isEqualTo(295_000L);
        assertThat(fullMonth.days()).hasSize(2);
        assertThat(fullMonth.days().get(0).date()).isEqualTo(LocalDate.of(2026, 8, 22));
        assertThat(fullMonth.days().get(0).totalAmount()).isEqualTo(240_000L);

        // 2. Loc rieng ngay 21/08 -> monthTotalAmount van la 295.000, filteredTotalAmount la 55.000
        MonthlyTransactionsResponse singleDay =
                statsService.getTransactions(userId, "2026-08", LocalDate.of(2026, 8, 21), "EXPENSE", null);
        assertThat(singleDay.monthTotalAmount()).isEqualTo(295_000L);
        assertThat(singleDay.monthTxnCount()).isEqualTo(3);
        assertThat(singleDay.filteredTotalAmount()).isEqualTo(55_000L);
        assertThat(singleDay.filteredTxnCount()).isEqualTo(1);
        assertThat(singleDay.days()).hasSize(1);
        assertThat(singleDay.days().get(0).items().get(0).merchant()).isEqualTo("Bún chả");
    }

    @Test
    void getTransactions_whenDateOutsidePeriod_throwsInvalidDateRange() {
        UUID userId = UUID.randomUUID();
        assertThatThrownBy(() ->
                statsService.getTransactions(userId, "2026-08", LocalDate.of(2026, 9, 1), "EXPENSE", null)
        )
                .isInstanceOf(InvalidRequestException.class)
                .extracting("code")
                .isEqualTo("INVALID_DATE_RANGE");
    }

    @Test
    void getMonthlyTrend_invalidTypeTransfer_throwsInvalidTransactionType_D10() {
        UUID userId = UUID.randomUUID();
        assertThatThrownBy(() ->
                statsService.getMonthlyTrend(userId, "2026-08", 5, "TRANSFER_OUT", null)
        )
                .isInstanceOf(InvalidRequestException.class)
                .extracting("code")
                .isEqualTo("INVALID_TRANSACTION_TYPE");
    }

    private static FactTransaction fact(UUID userId,
                                        UUID walletId,
                                        String type,
                                        long abs,
                                        long signed,
                                        LocalDate date,
                                        Instant at,
                                        String merchant) {
        FactTransaction f = new FactTransaction();
        f.setTransactionId(UUID.randomUUID());
        f.setUserId(userId);
        f.setWalletId(walletId);
        f.setWalletName("Ví Momo");
        f.setWalletKind("EWALLET");
        f.setType(type);
        f.setAmountAbs(abs);
        f.setAmountSigned(signed);
        f.setCurrencyCode("VND");
        f.setOccurredDate(date);
        f.setOccurredAt(at);
        f.setYearMonth("2026-08");
        f.setYearWeek("2026-W34");
        f.setYearNum((short) 2026);
        f.setCategoryName("Ăn uống");
        f.setCategoryIcon("Restaurant");
        f.setCategoryColor("#EF4444");
        f.setMerchant(merchant);
        f.setSource("MANUAL");
        return f;
    }
}
