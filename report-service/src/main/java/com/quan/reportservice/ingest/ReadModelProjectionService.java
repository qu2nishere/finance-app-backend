package com.quan.reportservice.ingest;

import com.quan.reportservice.common.security.AccountZone;

import com.quan.reportservice.stats.AggDaily;
import com.quan.reportservice.stats.AggDailyRepository;
import com.quan.reportservice.stats.AggMonthlyCategory;
import com.quan.reportservice.stats.AggMonthlyCategoryRepository;
import com.quan.reportservice.stats.AggMonthlySummary;
import com.quan.reportservice.stats.AggMonthlySummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReadModelProjectionService {

    private static final BigDecimal MAX_MOM_PERCENT = new BigDecimal("9999.99");
    private static final BigDecimal MIN_MOM_PERCENT = new BigDecimal("-9999.99");
    private static final BigDecimal MAX_RATE_PERCENT = new BigDecimal("999.99");
    private static final BigDecimal MIN_RATE_PERCENT = new BigDecimal("-999.99");

    private final ProcessedEventRepository processedEventRepository;
    private final DeadLetterEventRepository deadLetterEventRepository;
    private final FactTransactionRepository factTransactionRepository;
    private final DimWalletRepository dimWalletRepository;
    private final AggMonthlySummaryRepository aggMonthlySummaryRepository;
    private final AggMonthlyCategoryRepository aggMonthlyCategoryRepository;
    private final AggDailyRepository aggDailyRepository;

    /**
     * Ghi processed_events TRUOC nghiep vu trong cung @Transactional:
     * neu nghiep vu loi thi ca hai cung rollback; neu event trung thi bo qua an toan.
     */
    @Transactional
    public void processEvent(UUID eventId,
                             String eventType,
                             UUID userId,
                             UUID aggregateId,
                             JsonNode payload) {
        // Loai event chua ho tro: KHONG danh dau processed_events, de khi deploy ban
        // biet xu ly loai nay thi phat lai (tu dead_letter_events hoac outbox) van chay.
        if (!supports(eventType)) {
            throw new UnsupportedEventTypeException(eventType);
        }
        if (processedEventRepository.existsById(eventId)) {
            log.debug("Bo qua event da xu ly eventId={}", eventId);
            return;
        }
        processedEventRepository.save(new ProcessedEvent(eventId, eventType, aggregateId));

        switch (eventType) {
            case "WalletCreated" -> handleWalletCreated(userId, payload);
            case "TransactionCreated" -> handleTransactionCreated(userId, payload);
            case "TransactionDeleted" -> handleTransactionDeleted(userId, payload);
            default -> throw new UnsupportedEventTypeException(eventType);
        }
    }

    /** Cac loai event report-service biet xu ly. Them case moi o processEvent thi them vao day. */
    public static final Set<String> SUPPORTED_EVENT_TYPES =
            Set.of("WalletCreated", "TransactionCreated", "TransactionDeleted");

    public static boolean supports(String eventType) {
        return eventType != null && SUPPORTED_EVENT_TYPES.contains(eventType);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDeadLetter(UUID eventId, String eventType, String rawPayload, String error, int attempts) {
        DeadLetterEvent dlq = new DeadLetterEvent();
        dlq.setEventId(eventId);
        dlq.setEventType(eventType);
        dlq.setPayload(rawPayload != null && !rawPayload.isBlank() ? rawPayload : "{}");
        dlq.setError(error != null ? error : "Unknown consumer error");
        dlq.setAttempts((short) Math.max(1, attempts));
        deadLetterEventRepository.save(dlq);
    }

    private void handleWalletCreated(UUID envelopeUserId, JsonNode payload) {
        UUID walletId = requiredUuid(payload, "walletId");
        UUID userId = envelopeUserId != null ? envelopeUserId : requiredUuid(payload, "userId");

        DimWallet wallet = dimWalletRepository.findById(walletId).orElseGet(DimWallet::new);
        wallet.setWalletId(walletId);
        wallet.setUserId(userId);
        wallet.setName(optionalTextOrDefault(payload, "name", "Ví"));
        wallet.setKind(optionalTextOrDefault(payload, "kind", "CASH"));
        wallet.setColor(optionalText(payload, "color"));
        wallet.setInitialBalance(payload.path("initialBalance").asLong(0L));
        wallet.setIncludeInTotal(payload.path("includeInTotal").asBoolean(true));
        wallet.setDeleted(false);
        String createdAtText = optionalText(payload, "createdAt");
        if (createdAtText != null) {
            wallet.setCreatedAt(Instant.parse(createdAtText));
        }
        dimWalletRepository.save(wallet);
    }

    private void ensureDimWalletExists(UUID walletId, UUID userId, String walletName, String walletKind) {
        if (dimWalletRepository.findById(walletId).isPresent()) {
            return;
        }
        DimWallet wallet = new DimWallet();
        wallet.setWalletId(walletId);
        wallet.setUserId(userId);
        wallet.setName(walletName);
        wallet.setKind(walletKind);
        wallet.setInitialBalance(0L);
        wallet.setIncludeInTotal(true);
        wallet.setDeleted(false);
        dimWalletRepository.save(wallet);
    }

    private void handleTransactionCreated(UUID envelopeUserId, JsonNode payload) {
        UUID txnId = requiredUuid(payload, "transactionId");
        UUID userId = envelopeUserId != null ? envelopeUserId : requiredUuid(payload, "userId");
        Instant occurredAt = Instant.parse(requiredText(payload, "occurredAt"));
        String storedDate = optionalText(payload, "occurredDate");
        LocalDate occurredDate = storedDate == null
                ? LocalDate.ofInstant(occurredAt, AccountZone.fromClaim(optionalText(payload, "timezone")))
                : LocalDate.parse(storedDate);
        YearMonth ym = YearMonth.from(occurredDate);

        FactTransaction fact = factTransactionRepository.findById(txnId).orElseGet(FactTransaction::new);
        fact.setTransactionId(txnId);
        fact.setUserId(userId);
        fact.setOccurredAt(occurredAt);
        fact.setOccurredDate(occurredDate);
        fact.setYearMonth(ym.toString());
        fact.setYearWeek(formatIsoYearWeek(occurredDate));
        fact.setYearNum((short) occurredDate.getYear());
        fact.setType(requiredText(payload, "type"));
        fact.setAmountAbs(payload.path("amount").asLong());
        fact.setAmountSigned(payload.path("signedAmount").asLong());
        fact.setCurrencyCode(optionalTextOrDefault(payload, "currencyCode", "VND"));

        fact.setCategoryId(optionalUuid(payload, "categoryId"));
        fact.setCategoryName(optionalText(payload, "categoryName"));
        fact.setCategoryFlow(optionalText(payload, "categoryFlow"));
        fact.setCategoryIcon(optionalText(payload, "categoryIcon"));
        fact.setCategoryColor(optionalText(payload, "categoryColor"));
        fact.setParentCategoryId(optionalUuid(payload, "parentCategoryId"));
        fact.setParentCategoryName(optionalText(payload, "parentCategoryName"));

        fact.setWalletId(requiredUuid(payload, "walletId"));
        fact.setWalletName(optionalTextOrDefault(payload, "walletName", "Ví"));
        fact.setWalletKind(optionalTextOrDefault(payload, "walletKind", "CASH"));
        fact.setMerchant(optionalText(payload, "merchant"));
        fact.setNote(optionalText(payload, "note"));
        fact.setSource(optionalTextOrDefault(payload, "source", "MANUAL"));
        fact.setExcluded(payload.path("isExcludedFromReport").asBoolean(false));
        fact.setDeleted(false);

        factTransactionRepository.save(fact);
        ensureDimWalletExists(fact.getWalletId(), userId, fact.getWalletName(), fact.getWalletKind());

        // D10: chi INCOME/EXPENSE va khong excluded moi anh huong bang agg_*
        if (!fact.isExcluded() && isReportableFlow(fact.getType())) {
            recomputeDaily(userId, occurredDate);
            recomputeMonth(userId, ym);
            recomputeMonth(userId, ym.plusMonths(1));
        }
    }

    private void handleTransactionDeleted(UUID envelopeUserId, JsonNode payload) {
        UUID txnId = requiredUuid(payload, "transactionId");
        UUID userId = envelopeUserId != null ? envelopeUserId : requiredUuid(payload, "userId");

        Optional<FactTransaction> existingOpt = factTransactionRepository.findByTransactionIdAndUserId(txnId, userId);
        if (existingOpt.isPresent()) {
            FactTransaction fact = existingOpt.get();
            if (!fact.isDeleted()) {
                fact.setDeleted(true);
                factTransactionRepository.save(fact);
                if (!fact.isExcluded() && isReportableFlow(fact.getType())) {
                    LocalDate date = fact.getOccurredDate();
                    YearMonth ym = YearMonth.from(date);
                    recomputeDaily(userId, date);
                    recomputeMonth(userId, ym);
                    recomputeMonth(userId, ym.plusMonths(1));
                }
            }
            return;
        }

        String dateStr = optionalText(payload, "occurredDate");
        String instant = optionalText(payload, "occurredAt");
        String type = optionalText(payload, "type");
        boolean excluded = payload.path("isExcludedFromReport").asBoolean(false);
        if ((dateStr != null || instant != null) && !excluded && isReportableFlow(type)) {
            LocalDate date = dateStr != null ? LocalDate.parse(dateStr)
                    : LocalDate.ofInstant(Instant.parse(instant), AccountZone.fromClaim(optionalText(payload, "timezone")));
            YearMonth ym = YearMonth.from(date);
            recomputeDaily(userId, date);
            recomputeMonth(userId, ym);
            recomputeMonth(userId, ym.plusMonths(1));
        }
    }

    void recomputeDaily(UUID userId, LocalDate date) {
        List<FactTransaction> dayFacts = factTransactionRepository.findActiveReportableByDate(userId, date);
        Optional<AggDaily> existingOpt = aggDailyRepository.findByUserIdAndOccurredDate(userId, date);

        if (dayFacts.isEmpty()) {
            existingOpt.ifPresent(aggDailyRepository::delete);
            return;
        }

        long income = 0L;
        long expense = 0L;
        for (FactTransaction f : dayFacts) {
            if ("INCOME".equals(f.getType())) {
                income += f.getAmountAbs();
            } else if ("EXPENSE".equals(f.getType())) {
                expense += f.getAmountAbs();
            }
        }

        AggDaily agg = existingOpt.orElseGet(() -> {
            AggDaily d = new AggDaily();
            d.setUserId(userId);
            d.setOccurredDate(date);
            return d;
        });
        agg.setTotalIncome(income);
        agg.setTotalExpense(expense);
        agg.setNetFlow(income - expense);
        agg.setTxnCount(dayFacts.size());
        aggDailyRepository.save(agg);
    }

    /**
     * D6: Tinh lai agg_monthly_summary va agg_monthly_category tu fact_transactions
     * de share_percent va mom_change_percent luon khop 100% ke ca khi nhap bu/xoa thang cu.
     */
    public void recomputeMonth(UUID userId, YearMonth ym) {
        String ymStr = ym.toString();
        String prevYmStr = ym.minusMonths(1).toString();

        List<FactTransaction> currentFacts = factTransactionRepository.findActiveReportableByMonth(userId, ymStr);
        Optional<AggMonthlySummary> existingSummaryOpt =
                aggMonthlySummaryRepository.findByUserIdAndYearMonth(userId, ymStr);
        List<AggMonthlyCategory> existingCategories =
                aggMonthlyCategoryRepository.findByUserIdAndYearMonth(userId, ymStr);

        if (currentFacts.isEmpty()) {
            existingSummaryOpt.ifPresent(aggMonthlySummaryRepository::delete);
            if (!existingCategories.isEmpty()) {
                aggMonthlyCategoryRepository.deleteAll(existingCategories);
            }
            return;
        }

        List<FactTransaction> prevFacts = factTransactionRepository.findActiveReportableByMonth(userId, prevYmStr);

        long totalIncome = 0L;
        long totalExpense = 0L;
        int incomeCount = 0;
        int expenseCount = 0;
        for (FactTransaction f : currentFacts) {
            if ("INCOME".equals(f.getType())) {
                totalIncome += f.getAmountAbs();
                incomeCount++;
            } else if ("EXPENSE".equals(f.getType())) {
                totalExpense += f.getAmountAbs();
                expenseCount++;
            }
        }

        long prevIncome = 0L;
        long prevExpense = 0L;
        Map<UUID, Long> prevCategoryTotals = new HashMap<>();
        for (FactTransaction pf : prevFacts) {
            if ("INCOME".equals(pf.getType())) {
                prevIncome += pf.getAmountAbs();
            } else if ("EXPENSE".equals(pf.getType())) {
                prevExpense += pf.getAmountAbs();
            }
            if (pf.getCategoryId() != null) {
                prevCategoryTotals.merge(pf.getCategoryId(), pf.getAmountAbs(), Long::sum);
            }
        }

        // D9: chi nhung giao dich co category_id != null moi dua vao agg_monthly_category
        Map<UUID, List<FactTransaction>> byCategory = new LinkedHashMap<>();
        for (FactTransaction f : currentFacts) {
            if (f.getCategoryId() != null) {
                byCategory.computeIfAbsent(f.getCategoryId(), k -> new ArrayList<>()).add(f);
            }
        }

        Map<UUID, AggMonthlyCategory> existingCatMap = existingCategories.stream()
                .collect(Collectors.toMap(AggMonthlyCategory::getCategoryId, c -> c));
        Set<UUID> activeCatIds = new HashSet<>();
        List<AggMonthlyCategory> categoriesToSave = new ArrayList<>();

        UUID topExpenseCatId = null;
        String topExpenseCatName = null;
        long topExpenseAmount = -1L;

        for (Map.Entry<UUID, List<FactTransaction>> entry : byCategory.entrySet()) {
            UUID catId = entry.getKey();
            List<FactTransaction> catFacts = entry.getValue();
            activeCatIds.add(catId);

            FactTransaction latest = catFacts.stream()
                    .max(Comparator.comparing(FactTransaction::getOccurredAt))
                    .orElse(catFacts.get(0));

            String flow = latest.getCategoryFlow() != null ? latest.getCategoryFlow() : latest.getType();
            long catTotal = catFacts.stream().mapToLong(FactTransaction::getAmountAbs).sum();
            int catCount = catFacts.size();
            long avgAmount = catTotal / catCount;
            long flowTotal = "INCOME".equals(flow) ? totalIncome : totalExpense;
            long prevCatTotal = prevCategoryTotals.getOrDefault(catId, 0L);

            AggMonthlyCategory aggCat = existingCatMap.getOrDefault(catId, new AggMonthlyCategory());
            aggCat.setUserId(userId);
            aggCat.setYearMonth(ymStr);
            aggCat.setCategoryId(catId);
            aggCat.setCategoryName(latest.getCategoryName() != null ? latest.getCategoryName() : "Khác");
            aggCat.setCategoryFlow(flow);
            aggCat.setCategoryIcon(latest.getCategoryIcon());
            aggCat.setCategoryColor(latest.getCategoryColor());
            aggCat.setTotalAmount(catTotal);
            aggCat.setTxnCount(catCount);
            aggCat.setAvgAmount(avgAmount);
            aggCat.setSharePercent(computeSharePercent(catTotal, flowTotal));
            aggCat.setMomChangePercent(computeMomPercent(catTotal, prevCatTotal));
            categoriesToSave.add(aggCat);

            if ("EXPENSE".equals(flow) && catTotal > topExpenseAmount) {
                topExpenseAmount = catTotal;
                topExpenseCatId = catId;
                topExpenseCatName = aggCat.getCategoryName();
            }
        }

        List<AggMonthlyCategory> obsoleteCats = existingCategories.stream()
                .filter(c -> !activeCatIds.contains(c.getCategoryId()))
                .toList();
        if (!obsoleteCats.isEmpty()) {
            aggMonthlyCategoryRepository.deleteAll(obsoleteCats);
        }
        if (!categoriesToSave.isEmpty()) {
            aggMonthlyCategoryRepository.saveAll(categoriesToSave);
        }

        AggMonthlySummary summary = existingSummaryOpt.orElseGet(() -> {
            AggMonthlySummary s = new AggMonthlySummary();
            s.setUserId(userId);
            s.setYearMonth(ymStr);
            return s;
        });
        summary.setTotalIncome(totalIncome);
        summary.setTotalExpense(totalExpense);
        summary.setNetCashflow(totalIncome - totalExpense);
        summary.setSavingsRate(computeSavingsRate(totalIncome, totalExpense));
        summary.setIncomeTxnCount(incomeCount);
        summary.setExpenseTxnCount(expenseCount);
        summary.setTopCategoryId(topExpenseCatId);
        summary.setTopCategoryName(topExpenseCatName);
        summary.setIncomeMomPercent(computeMomPercent(totalIncome, prevIncome));
        summary.setExpenseMomPercent(computeMomPercent(totalExpense, prevExpense));
        aggMonthlySummaryRepository.save(summary);
    }

    static boolean isReportableFlow(String type) {
        return "INCOME".equals(type) || "EXPENSE".equals(type);
    }

    private static String formatIsoYearWeek(LocalDate date) {
        int weekYear = date.get(IsoFields.WEEK_BASED_YEAR);
        int week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return String.format(Locale.ROOT, "%04d-W%02d", weekYear, week);
    }

    private static BigDecimal computeSharePercent(long part, long total) {
        if (total <= 0L) {
            return null;
        }
        BigDecimal pct = BigDecimal.valueOf(part)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        if (pct.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal hundred = new BigDecimal("100.00");
        return pct.compareTo(hundred) > 0 ? hundred : pct;
    }

    private static BigDecimal computeSavingsRate(long income, long expense) {
        if (income <= 0L) {
            return null;
        }
        BigDecimal rate = BigDecimal.valueOf(income - expense)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(income), 2, RoundingMode.HALF_UP);
        if (rate.compareTo(MAX_RATE_PERCENT) > 0) {
            return MAX_RATE_PERCENT;
        }
        return rate.compareTo(MIN_RATE_PERCENT) < 0 ? MIN_RATE_PERCENT : rate;
    }

    private static BigDecimal computeMomPercent(long current, long previous) {
        if (previous <= 0L) {
            return null;
        }
        BigDecimal mom = BigDecimal.valueOf(current - previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(previous), 2, RoundingMode.HALF_UP);
        if (mom.compareTo(MAX_MOM_PERCENT) > 0) {
            return MAX_MOM_PERCENT;
        }
        return mom.compareTo(MIN_MOM_PERCENT) < 0 ? MIN_MOM_PERCENT : mom;
    }

    private static UUID requiredUuid(JsonNode node, String field) {
        String val = optionalText(node, field);
        if (val == null) {
            throw new IllegalArgumentException("Missing required UUID field: " + field);
        }
        return UUID.fromString(val);
    }

    private static UUID optionalUuid(JsonNode node, String field) {
        String val = optionalText(node, field);
        return val != null ? UUID.fromString(val) : null;
    }

    private static String requiredText(JsonNode node, String field) {
        String val = optionalText(node, field);
        if (val == null) {
            throw new IllegalArgumentException("Missing required text field: " + field);
        }
        return val;
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode child = node.path(field);
        if (child.isMissingNode() || child.isNull()) {
            return null;
        }
        String text = child.asString();
        return (text == null || text.isBlank()) ? null : text.trim();
    }

    private static String optionalTextOrDefault(JsonNode node, String field, String defaultValue) {
        String val = optionalText(node, field);
        return val != null ? val : defaultValue;
    }
}
