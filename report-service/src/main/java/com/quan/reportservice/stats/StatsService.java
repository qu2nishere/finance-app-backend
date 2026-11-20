package com.quan.reportservice.stats;

import com.quan.reportservice.common.exception.InvalidRequestException;
import com.quan.reportservice.ingest.DimWallet;
import com.quan.reportservice.ingest.DimWalletRepository;
import com.quan.reportservice.ingest.FactTransaction;
import com.quan.reportservice.ingest.FactTransactionRepository;
import com.quan.reportservice.stats.dto.CashflowSummaryResponse;
import com.quan.reportservice.stats.dto.CategoryBreakdownItemResponse;
import com.quan.reportservice.stats.dto.DailyTransactionGroupResponse;
import com.quan.reportservice.stats.dto.MonthlyBarPointResponse;
import com.quan.reportservice.stats.dto.MonthlyTransactionsResponse;
import com.quan.reportservice.stats.dto.MonthlyTrendResponse;
import com.quan.reportservice.stats.dto.ReportTransactionItemResponse;
import com.quan.reportservice.stats.dto.WalletCashflowItemResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatsService {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String DEFAULT_CURRENCY = "VND";
    private static final int MIN_MONTHS = 1;
    private static final int MAX_MONTHS = 12;

    private final AggMonthlySummaryRepository aggMonthlySummaryRepository;
    private final AggMonthlyCategoryRepository aggMonthlyCategoryRepository;
    private final FactTransactionRepository factTransactionRepository;
    private final DimWalletRepository dimWalletRepository;

    @Transactional(readOnly = true)
    public MonthlyTrendResponse getMonthlyTrend(UUID userId,
                                                String period,
                                                int months,
                                                String type,
                                                UUID categoryId) {
        if (months < MIN_MONTHS || months > MAX_MONTHS) {
            throw new InvalidRequestException("INVALID_MONTHS_RANGE", "Số tháng thống kê phải từ 1 đến 12");
        }
        String flow = normalizeRequiredFlow(type, "INCOME");
        YearMonth selectedYm = parseOrCurrentYearMonth(period);
        YearMonth prevYm = selectedYm.minusMonths(1);
        YearMonth startYm = selectedYm.minusMonths(months - 1L);

        Set<String> queryMonths = new LinkedHashSet<>();
        queryMonths.add(prevYm.toString());
        for (int i = 0; i < months; i++) {
            queryMonths.add(startYm.plusMonths(i).toString());
        }

        Map<String, AggMonthlySummary> summaryMap = aggMonthlySummaryRepository
                .findByUserIdAndYearMonthIn(userId, queryMonths)
                .stream()
                .collect(Collectors.toMap(s -> s.getYearMonth().trim(), s -> s));

        List<MonthlyBarPointResponse> points = new ArrayList<>(months);
        long selectedAmount;
        long previousAmount;

        if (categoryId == null) {
            for (int i = 0; i < months; i++) {
                YearMonth ym = startYm.plusMonths(i);
                AggMonthlySummary s = summaryMap.get(ym.toString());
                long total = extractFlowAmount(s, flow);
                int count = extractFlowCount(s, flow);
                points.add(new MonthlyBarPointResponse(
                        ym.toString(),
                        ym.getYear(),
                        ym.getMonthValue(),
                        total,
                        count,
                        ym.equals(selectedYm)
                ));
            }
            selectedAmount = extractFlowAmount(summaryMap.get(selectedYm.toString()), flow);
            previousAmount = extractFlowAmount(summaryMap.get(prevYm.toString()), flow);
        } else {
            Map<String, AggMonthlyCategory> catMap = aggMonthlyCategoryRepository
                    .findByUserIdAndCategoryIdAndYearMonthIn(userId, categoryId, queryMonths)
                    .stream()
                    .collect(Collectors.toMap(c -> c.getYearMonth().trim(), c -> c));

            for (int i = 0; i < months; i++) {
                YearMonth ym = startYm.plusMonths(i);
                AggMonthlyCategory c = catMap.get(ym.toString());
                boolean matchFlow = c != null && flow.equals(c.getCategoryFlow());
                long total = matchFlow ? c.getTotalAmount() : 0L;
                int count = matchFlow ? c.getTxnCount() : 0;
                points.add(new MonthlyBarPointResponse(
                        ym.toString(),
                        ym.getYear(),
                        ym.getMonthValue(),
                        total,
                        count,
                        ym.equals(selectedYm)
                ));
            }
            AggMonthlyCategory curCat = catMap.get(selectedYm.toString());
            AggMonthlyCategory prevCat = catMap.get(prevYm.toString());
            selectedAmount = (curCat != null && flow.equals(curCat.getCategoryFlow())) ? curCat.getTotalAmount() : 0L;
            previousAmount = (prevCat != null && flow.equals(prevCat.getCategoryFlow())) ? prevCat.getTotalAmount() : 0L;
        }

        long momChangeAmount = selectedAmount - previousAmount;
        BigDecimal momChangePercent = computeMomPercent(selectedAmount, previousAmount);

        List<CategoryBreakdownItemResponse> breakdown = buildCategoryBreakdown(
                userId,
                selectedYm,
                prevYm,
                flow,
                summaryMap.get(selectedYm.toString()),
                summaryMap.get(prevYm.toString())
        );

        return new MonthlyTrendResponse(
                selectedYm.toString(),
                flow,
                categoryId,
                DEFAULT_CURRENCY,
                selectedAmount,
                previousAmount,
                momChangeAmount,
                momChangePercent,
                points,
                breakdown
        );
    }

    @Transactional(readOnly = true)
    public MonthlyTransactionsResponse getTransactions(UUID userId,
                                                       String period,
                                                       LocalDate date,
                                                       String type,
                                                       UUID categoryId) {
        YearMonth selectedYm = resolvePeriodForTransactions(period, date);
        if (date != null && !YearMonth.from(date).equals(selectedYm)) {
            throw new InvalidRequestException("INVALID_DATE_RANGE", "Ngày lọc phải nằm trong tháng đã chọn");
        }
        String flowFilter = normalizeOptionalFlow(type);

        List<FactTransaction> monthFacts =
                factTransactionRepository.findActiveReportableByMonth(userId, selectedYm.toString());

        List<FactTransaction> monthMatching = monthFacts.stream()
                .filter(f -> flowFilter == null || flowFilter.equals(f.getType()))
                .filter(f -> categoryId == null || categoryId.equals(f.getCategoryId()))
                .toList();

        long monthTotalAmount = monthMatching.stream().mapToLong(FactTransaction::getAmountAbs).sum();
        int monthTxnCount = monthMatching.size();

        List<FactTransaction> filtered = date == null
                ? monthMatching
                : monthMatching.stream().filter(f -> date.equals(f.getOccurredDate())).toList();

        long filteredTotalAmount = filtered.stream().mapToLong(FactTransaction::getAmountAbs).sum();
        int filteredTxnCount = filtered.size();

        Map<LocalDate, List<FactTransaction>> byDate = new LinkedHashMap<>();
        for (FactTransaction f : filtered) {
            byDate.computeIfAbsent(f.getOccurredDate(), k -> new ArrayList<>()).add(f);
        }

        List<DailyTransactionGroupResponse> days = new ArrayList<>(byDate.size());
        for (Map.Entry<LocalDate, List<FactTransaction>> entry : byDate.entrySet()) {
            List<FactTransaction> dayRows = entry.getValue();
            long dayTotalAbs = dayRows.stream().mapToLong(FactTransaction::getAmountAbs).sum();
            long dayNetSigned = dayRows.stream().mapToLong(FactTransaction::getAmountSigned).sum();
            List<ReportTransactionItemResponse> items = dayRows.stream()
                    .map(ReportTransactionItemResponse::from)
                    .toList();
            days.add(new DailyTransactionGroupResponse(
                    entry.getKey(),
                    dayTotalAbs,
                    dayNetSigned,
                    items.size(),
                    items
            ));
        }

        return new MonthlyTransactionsResponse(
                selectedYm.toString(),
                date,
                DEFAULT_CURRENCY,
                monthTotalAmount,
                monthTxnCount,
                filteredTotalAmount,
                filteredTxnCount,
                days
        );
    }

    @Transactional(readOnly = true)
    public CashflowSummaryResponse getCashflowSummary(UUID userId, String period) {
        YearMonth selectedYm = parseOrCurrentYearMonth(period);
        LocalDate monthStart = selectedYm.atDay(1);

        List<DimWallet> wallets = dimWalletRepository.findByUserIdAndDeletedFalseOrderByNameAsc(userId);
        Map<UUID, Long> beforeMap = factTransactionRepository
                .sumSignedBeforeDateByWallet(userId, monthStart)
                .stream()
                .collect(Collectors.toMap(
                        FactTransactionRepository.WalletSignedSumProjection::getWalletId,
                        FactTransactionRepository.WalletSignedSumProjection::getSignedTotal
                ));
        Map<UUID, FactTransactionRepository.WalletMonthFlowProjection> monthMap = factTransactionRepository
                .sumMonthFlowByWallet(userId, selectedYm.toString())
                .stream()
                .collect(Collectors.toMap(
                        FactTransactionRepository.WalletMonthFlowProjection::getWalletId,
                        p -> p
                ));

        List<WalletRef> walletRefs = new ArrayList<>();
        for (DimWallet w : wallets) {
            walletRefs.add(new WalletRef(w.getWalletId(), w.getName(), w.getKind(), w.getColor(),
                    w.getInitialBalance(), w.isIncludeInTotal()));
        }
        walletRefs.addAll(walletsMissingFromDim(userId, wallets, beforeMap.keySet(), monthMap.keySet()));

        long totalOpening = 0L;
        long totalIncome = 0L;
        long totalExpense = 0L;
        long totalClosing = 0L;
        List<WalletCashflowItemResponse> items = new ArrayList<>();

        for (WalletRef w : walletRefs) {
            long opening = w.initialBalance() + beforeMap.getOrDefault(w.walletId(), 0L);
            FactTransactionRepository.WalletMonthFlowProjection mf = monthMap.get(w.walletId());
            long signedInMonth = mf != null ? mf.getSignedTotal() : 0L;
            long income = mf != null ? mf.getIncomeTotal() : 0L;
            long expense = mf != null ? mf.getExpenseTotal() : 0L;
            long txnCount = mf != null ? mf.getTxnCount() : 0L;
            long closing = opening + signedInMonth;
            long netFlow = closing - opening;

            if (w.includeInTotal()) {
                totalOpening += opening;
                totalIncome += income;
                totalExpense += expense;
                totalClosing += closing;
            }

            if (opening != 0L || closing != 0L || txnCount > 0L) {
                items.add(new WalletCashflowItemResponse(
                        w.walletId(),
                        w.name(),
                        w.kind(),
                        w.color() != null ? w.color().trim() : null,
                        null,
                        opening,
                        income,
                        expense,
                        netFlow,
                        closing,
                        w.includeInTotal()
                ));
            }
        }

        items.sort(Comparator.comparingLong((WalletCashflowItemResponse item) -> Math.abs(item.netFlow()))
                .reversed()
                .thenComparing(WalletCashflowItemResponse::walletName));

        return new CashflowSummaryResponse(
                selectedYm.toString(),
                DEFAULT_CURRENCY,
                totalOpening,
                totalIncome,
                totalExpense,
                totalClosing,
                items
        );
    }

    /** Vi dung de tinh dong tien: lay tu dim_wallet, hoac dung tam tu fact khi dim_wallet thieu. */
    private record WalletRef(UUID walletId,
                             String name,
                             String kind,
                             String color,
                             long initialBalance,
                             boolean includeInTotal) {
    }

    /**
     * Vi co giao dich trong fact_transactions nhung chua co trong dim_wallet (vd event
     * WalletCreated bi mat). Van dua vao bao cao de Tien vao/ra khong ve 0; so du ban dau
     * tam coi la 0 va ghi WARN de biet can phat lai event vi.
     */
    private List<WalletRef> walletsMissingFromDim(UUID userId,
                                                  List<DimWallet> activeWallets,
                                                  Set<UUID> idsBefore,
                                                  Set<UUID> idsInMonth) {
        Set<UUID> missing = new LinkedHashSet<>(idsBefore);
        missing.addAll(idsInMonth);
        activeWallets.forEach(w -> missing.remove(w.getWalletId()));
        if (missing.isEmpty()) {
            return List.of();
        }
        dimWalletRepository.findDeletedWalletIds(userId).forEach(missing::remove);
        if (missing.isEmpty()) {
            return List.of();
        }

        log.warn("dim_wallet thieu {} vi cua user {}: {} — tam tinh initial_balance = 0, can phat lai WalletCreated",
                missing.size(), userId, missing);

        Map<UUID, FactTransactionRepository.WalletLabelProjection> labels = factTransactionRepository
                .findWalletLabels(userId, missing)
                .stream()
                .collect(Collectors.toMap(FactTransactionRepository.WalletLabelProjection::getWalletId, p -> p));

        List<WalletRef> refs = new ArrayList<>(missing.size());
        for (UUID id : missing) {
            FactTransactionRepository.WalletLabelProjection label = labels.get(id);
            String name = label != null && label.getWalletName() != null ? label.getWalletName() : "Ví";
            String kind = label != null && label.getWalletKind() != null ? label.getWalletKind() : "CASH";
            refs.add(new WalletRef(id, name, kind, null, 0L, true));
        }
        return refs;
    }

    private List<CategoryBreakdownItemResponse> buildCategoryBreakdown(UUID userId,
                                                                       YearMonth selectedYm,
                                                                       YearMonth prevYm,
                                                                       String flow,
                                                                       AggMonthlySummary currentSummary,
                                                                       AggMonthlySummary prevSummary) {
        List<AggMonthlyCategory> rows = aggMonthlyCategoryRepository
                .findByUserIdAndYearMonthAndCategoryFlowOrderByTotalAmountDesc(userId, selectedYm.toString(), flow);

        List<CategoryBreakdownItemResponse> list = new ArrayList<>();
        long categorizedTotal = 0L;
        int categorizedCount = 0;
        for (AggMonthlyCategory row : rows) {
            categorizedTotal += row.getTotalAmount();
            categorizedCount += row.getTxnCount();
            list.add(new CategoryBreakdownItemResponse(
                    row.getCategoryId(),
                    row.getCategoryName(),
                    row.getCategoryIcon(),
                    row.getCategoryColor() != null ? row.getCategoryColor().trim() : null,
                    row.getTotalAmount(),
                    row.getTxnCount(),
                    row.getSharePercent(),
                    row.getMomChangePercent()
            ));
        }

        // D9: Neu tong o agg_monthly_summary lon hon tong cac danh muc -> co giao dich chua phan loai
        long summaryFlowTotal = extractFlowAmount(currentSummary, flow);
        int summaryFlowCount = extractFlowCount(currentSummary, flow);
        if (summaryFlowTotal > categorizedTotal) {
            long uncatAmount = summaryFlowTotal - categorizedTotal;
            int uncatCount = Math.max(1, summaryFlowCount - categorizedCount);
            BigDecimal sharePct = BigDecimal.valueOf(uncatAmount)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(summaryFlowTotal), 2, RoundingMode.HALF_UP);

            long prevSummaryTotal = extractFlowAmount(prevSummary, flow);
            long prevCatTotal = prevSummaryTotal > 0L
                    ? aggMonthlyCategoryRepository
                            .findByUserIdAndYearMonthAndCategoryFlowOrderByTotalAmountDesc(
                                    userId, prevYm.toString(), flow)
                            .stream()
                            .mapToLong(AggMonthlyCategory::getTotalAmount)
                            .sum()
                    : 0L;
            long prevUncatAmount = Math.max(0L, prevSummaryTotal - prevCatTotal);
            BigDecimal uncatMom = computeMomPercent(uncatAmount, prevUncatAmount);

            list.add(new CategoryBreakdownItemResponse(
                    null,
                    "Chưa phân loại",
                    "Category",
                    "#94A3B8",
                    uncatAmount,
                    uncatCount,
                    sharePct,
                    uncatMom
            ));
            list.sort(Comparator.comparingLong(CategoryBreakdownItemResponse::totalAmount).reversed());
        }

        return list;
    }

    private static long extractFlowAmount(AggMonthlySummary s, String flow) {
        if (s == null) {
            return 0L;
        }
        return "INCOME".equals(flow) ? s.getTotalIncome() : s.getTotalExpense();
    }

    private static int extractFlowCount(AggMonthlySummary s, String flow) {
        if (s == null) {
            return 0;
        }
        return "INCOME".equals(flow) ? s.getIncomeTxnCount() : s.getExpenseTxnCount();
    }

    private static YearMonth parseOrCurrentYearMonth(String period) {
        if (period == null || period.isBlank()) {
            return YearMonth.now(DEFAULT_ZONE);
        }
        try {
            return YearMonth.parse(period.trim());
        } catch (DateTimeParseException ex) {
            throw new InvalidRequestException("INVALID_PERIOD", "Kỳ thống kê không đúng định dạng YYYY-MM");
        }
    }

    private static YearMonth resolvePeriodForTransactions(String period, LocalDate date) {
        if (period != null && !period.isBlank()) {
            return parseOrCurrentYearMonth(period);
        }
        if (date != null) {
            return YearMonth.from(date);
        }
        return YearMonth.now(DEFAULT_ZONE);
    }

    private static String normalizeRequiredFlow(String rawType, String defaultFlow) {
        String normalized = (rawType == null || rawType.isBlank())
                ? defaultFlow
                : rawType.trim().toUpperCase(Locale.ROOT);
        if (!"INCOME".equals(normalized) && !"EXPENSE".equals(normalized)) {
            throw new InvalidRequestException(
                    "INVALID_TRANSACTION_TYPE",
                    "Loại giao dịch thống kê chỉ hỗ trợ INCOME hoặc EXPENSE"
            );
        }
        return normalized;
    }

    private static String normalizeOptionalFlow(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return null;
        }
        return normalizeRequiredFlow(rawType, null);
    }

    private static BigDecimal computeMomPercent(long current, long previous) {
        if (previous <= 0L) {
            return null;
        }
        return BigDecimal.valueOf(current - previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(previous), 2, RoundingMode.HALF_UP);
    }
}
