package com.quan.reportservice.stats;

import com.quan.reportservice.common.exception.InvalidRequestException;
import com.quan.reportservice.ingest.DimWallet;
import com.quan.reportservice.ingest.DimWalletRepository;
import com.quan.reportservice.ingest.FactTransaction;
import com.quan.reportservice.ingest.FactTransactionRepository;
import com.quan.reportservice.stats.dto.CashflowSummaryResponse;
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
    @Mock
    private DimWalletRepository dimWalletRepository;

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

    @Test
    void getCashflowSummary_computesOpeningClosingAndRespectsIncludeInTotalAndSorting() {
        UUID userId = UUID.randomUUID();
        UUID vcbId = UUID.randomUUID();
        UUID momoId = UUID.randomUUID();
        UUID hiddenId = UUID.randomUUID();
        UUID emptyId = UUID.randomUUID();

        DimWallet vcb = dimWallet(vcbId, userId, "Vietcombank", "BANK", "#0E9F6E", 10_000_000L, true);
        DimWallet momo = dimWallet(momoId, userId, "Momo", "EWALLET", "#D946EF", 1_000_000L, true);
        DimWallet hidden = dimWallet(hiddenId, userId, "Tiết kiệm riêng", "SAVING", "#64748B", 5_000_000L, false);
        DimWallet empty = dimWallet(emptyId, userId, "Ví rỗng", "CASH", "#2563EB", 0L, true);

        when(dimWalletRepository.findByUserIdAndDeletedFalseOrderByNameAsc(userId))
                .thenReturn(List.of(momo, hidden, vcb, empty));
        when(factTransactionRepository.sumSignedBeforeDateByWallet(userId, LocalDate.of(2026, 8, 1)))
                .thenReturn(List.of(
                        signedSum(vcbId, 510_000L),
                        signedSum(momoId, 0L)
                ));
        when(factTransactionRepository.sumMonthFlowByWallet(userId, "2026-08"))
                .thenReturn(List.of(
                        monthFlow(vcbId, 7_900_000L, 18_200_000L, 10_300_000L, 5L),
                        monthFlow(momoId, -1_200_000L, 0L, 1_200_000L, 2L),
                        monthFlow(hiddenId, 2_000_000L, 2_000_000L, 0L, 1L)
                ));

        CashflowSummaryResponse resp = statsService.getCashflowSummary(userId, "2026-08");

        assertThat(resp.period()).isEqualTo("2026-08");
        assertThat(resp.currencyCode()).isEqualTo("VND");
        // Chi tinh vcb + momo (hidden co includeInTotal = false)
        assertThat(resp.openingBalance()).isEqualTo(11_510_000L);
        assertThat(resp.totalIncome()).isEqualTo(18_200_000L);
        assertThat(resp.totalExpense()).isEqualTo(11_500_000L);
        assertThat(resp.closingBalance()).isEqualTo(18_210_000L);

        // Danh sach Theo vi: bo empty (so du = 0 & khong giao dich), sap xep theo |netFlow| giam dan
        assertThat(resp.wallets()).hasSize(3);
        assertThat(resp.wallets().get(0).walletId()).isEqualTo(vcbId);
        assertThat(resp.wallets().get(0).openingBalance()).isEqualTo(10_510_000L);
        assertThat(resp.wallets().get(0).netFlow()).isEqualTo(7_900_000L);
        assertThat(resp.wallets().get(0).closingBalance()).isEqualTo(18_410_000L);

        assertThat(resp.wallets().get(1).walletId()).isEqualTo(hiddenId);
        assertThat(resp.wallets().get(1).netFlow()).isEqualTo(2_000_000L);
        assertThat(resp.wallets().get(1).includeInTotal()).isFalse();

        assertThat(resp.wallets().get(2).walletId()).isEqualTo(momoId);
        assertThat(resp.wallets().get(2).netFlow()).isEqualTo(-1_200_000L);
        assertThat(resp.wallets().get(2).closingBalance()).isEqualTo(-200_000L);
    }

    @Test
    void getCashflowSummary_emptyMonthAndNoWallets_returnsZeros() {
        UUID userId = UUID.randomUUID();
        when(dimWalletRepository.findByUserIdAndDeletedFalseOrderByNameAsc(userId)).thenReturn(List.of());
        when(factTransactionRepository.sumSignedBeforeDateByWallet(userId, LocalDate.of(2026, 8, 1))).thenReturn(List.of());
        when(factTransactionRepository.sumMonthFlowByWallet(userId, "2026-08")).thenReturn(List.of());

        CashflowSummaryResponse resp = statsService.getCashflowSummary(userId, "2026-08");

        assertThat(resp.openingBalance()).isZero();
        assertThat(resp.totalIncome()).isZero();
        assertThat(resp.totalExpense()).isZero();
        assertThat(resp.closingBalance()).isZero();
        assertThat(resp.wallets()).isEmpty();
    }

    @Test
    void getCashflowSummary_walletMissingFromDimWallet_stillCountedFromFacts() {
        // Tai hien loi 2026-11-20: WalletCreated bi mat -> dim_wallet rong nhung fact van co giao dich
        UUID userId = UUID.randomUUID();
        UUID cashId = UUID.randomUUID();
        when(dimWalletRepository.findByUserIdAndDeletedFalseOrderByNameAsc(userId)).thenReturn(List.of());
        when(factTransactionRepository.sumSignedBeforeDateByWallet(userId, LocalDate.of(2026, 11, 1)))
                .thenReturn(List.of(signedSum(cashId, -25_002L)));
        when(factTransactionRepository.sumMonthFlowByWallet(userId, "2026-11"))
                .thenReturn(List.of(monthFlow(cashId, -50_050L, 0L, 50_050L, 2L)));
        when(factTransactionRepository.findWalletLabels(eq(userId), anyCollection()))
                .thenReturn(List.of(walletLabel(cashId, "Tiền mặt", "CASH")));

        CashflowSummaryResponse resp = statsService.getCashflowSummary(userId, "2026-11");

        assertThat(resp.openingBalance()).isEqualTo(-25_002L);
        assertThat(resp.totalIncome()).isZero();
        assertThat(resp.totalExpense()).isEqualTo(50_050L);
        assertThat(resp.closingBalance()).isEqualTo(-75_052L);
        assertThat(resp.wallets()).hasSize(1);
        assertThat(resp.wallets().get(0).walletName()).isEqualTo("Tiền mặt");
        assertThat(resp.wallets().get(0).netFlow()).isEqualTo(-50_050L);
        assertThat(resp.wallets().get(0).includeInTotal()).isTrue();
    }

    @Test
    void getCashflowSummary_deletedWalletInFacts_isNotTreatedAsMissing() {
        UUID userId = UUID.randomUUID();
        UUID deletedId = UUID.randomUUID();
        when(dimWalletRepository.findByUserIdAndDeletedFalseOrderByNameAsc(userId)).thenReturn(List.of());
        when(dimWalletRepository.findDeletedWalletIds(userId)).thenReturn(List.of(deletedId));
        when(factTransactionRepository.sumSignedBeforeDateByWallet(userId, LocalDate.of(2026, 11, 1)))
                .thenReturn(List.of(signedSum(deletedId, 1_000_000L)));
        when(factTransactionRepository.sumMonthFlowByWallet(userId, "2026-11")).thenReturn(List.of());

        CashflowSummaryResponse resp = statsService.getCashflowSummary(userId, "2026-11");

        assertThat(resp.openingBalance()).isZero();
        assertThat(resp.wallets()).isEmpty();
    }

    @Test
    void getCashflowSummary_invalidPeriod_throwsInvalidPeriod() {
        UUID userId = UUID.randomUUID();
        assertThatThrownBy(() -> statsService.getCashflowSummary(userId, "2026-99"))
                .isInstanceOf(InvalidRequestException.class)
                .extracting("code")
                .isEqualTo("INVALID_PERIOD");
    }

    private static DimWallet dimWallet(UUID id,
                                       UUID userId,
                                       String name,
                                       String kind,
                                       String color,
                                       long initialBalance,
                                       boolean includeInTotal) {
        DimWallet w = new DimWallet();
        w.setWalletId(id);
        w.setUserId(userId);
        w.setName(name);
        w.setKind(kind);
        w.setColor(color);
        w.setInitialBalance(initialBalance);
        w.setIncludeInTotal(includeInTotal);
        return w;
    }

    private static FactTransactionRepository.WalletLabelProjection walletLabel(UUID walletId, String name, String kind) {
        return new FactTransactionRepository.WalletLabelProjection() {
            @Override
            public UUID getWalletId() {
                return walletId;
            }

            @Override
            public String getWalletName() {
                return name;
            }

            @Override
            public String getWalletKind() {
                return kind;
            }
        };
    }

    private static FactTransactionRepository.WalletSignedSumProjection signedSum(UUID walletId, long signedTotal) {
        return new FactTransactionRepository.WalletSignedSumProjection() {
            @Override
            public UUID getWalletId() {
                return walletId;
            }

            @Override
            public long getSignedTotal() {
                return signedTotal;
            }
        };
    }

    private static FactTransactionRepository.WalletMonthFlowProjection monthFlow(UUID walletId,
                                                                                  long signedTotal,
                                                                                  long incomeTotal,
                                                                                  long expenseTotal,
                                                                                  long txnCount) {
        return new FactTransactionRepository.WalletMonthFlowProjection() {
            @Override
            public UUID getWalletId() {
                return walletId;
            }

            @Override
            public long getSignedTotal() {
                return signedTotal;
            }

            @Override
            public long getIncomeTotal() {
                return incomeTotal;
            }

            @Override
            public long getExpenseTotal() {
                return expenseTotal;
            }

            @Override
            public long getTxnCount() {
                return txnCount;
            }
        };
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
