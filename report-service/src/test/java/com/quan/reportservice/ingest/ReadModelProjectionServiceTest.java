
package com.quan.reportservice.ingest;

import com.quan.reportservice.stats.AggDaily;
import com.quan.reportservice.stats.AggDailyRepository;
import com.quan.reportservice.stats.AggMonthlyCategory;
import com.quan.reportservice.stats.AggMonthlyCategoryRepository;
import com.quan.reportservice.stats.AggMonthlySummary;
import com.quan.reportservice.stats.AggMonthlySummaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ReadModelProjectionServiceTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;
    @Mock
    private DeadLetterEventRepository deadLetterEventRepository;
    @Mock
    private FactTransactionRepository factTransactionRepository;
    @Mock
    private DimWalletRepository dimWalletRepository;
    @Mock
    private AggMonthlySummaryRepository aggMonthlySummaryRepository;
    @Mock
    private AggMonthlyCategoryRepository aggMonthlyCategoryRepository;
    @Mock
    private AggDailyRepository aggDailyRepository;

    private final Set<UUID> processedIds = new HashSet<>();
    private final Map<UUID, FactTransaction> facts = new HashMap<>();
    private final Map<UUID, DimWallet> wallets = new HashMap<>();
    private final Map<String, AggMonthlySummary> summaries = new HashMap<>();
    private final Map<String, List<AggMonthlyCategory>> categoriesByMonth = new HashMap<>();
    private final Map<LocalDate, AggDaily> dailies = new HashMap<>();

    private ObjectMapper mapper;
    private ReadModelProjectionService service;

    @BeforeEach
    void setUp() {
        mapper = JsonMapper.builder().build();
        service = new ReadModelProjectionService(
                processedEventRepository,
                deadLetterEventRepository,
                factTransactionRepository,
                dimWalletRepository,
                aggMonthlySummaryRepository,
                aggMonthlyCategoryRepository,
                aggDailyRepository
        );

        lenient().when(dimWalletRepository.findById(any())).thenAnswer(inv ->
                Optional.ofNullable(wallets.get(inv.getArgument(0))));
        lenient().when(dimWalletRepository.save(any())).thenAnswer(inv -> {
            DimWallet w = inv.getArgument(0);
            wallets.put(w.getWalletId(), w);
            return w;
        });

        lenient().when(processedEventRepository.existsById(any())).thenAnswer(inv ->
                processedIds.contains(inv.getArgument(0)));
        lenient().when(processedEventRepository.save(any())).thenAnswer(inv -> {
            ProcessedEvent pe = inv.getArgument(0);
            processedIds.add(pe.getEventId());
            return pe;
        });

        lenient().when(factTransactionRepository.findById(any())).thenAnswer(inv ->
                Optional.ofNullable(facts.get(inv.getArgument(0))));
        lenient().when(factTransactionRepository.findByTransactionIdAndUserId(any(), any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            UUID uid = inv.getArgument(1);
            FactTransaction f = facts.get(id);
            return (f != null && f.getUserId().equals(uid)) ? Optional.of(f) : Optional.empty();
        });
        lenient().when(factTransactionRepository.save(any())).thenAnswer(inv -> {
            FactTransaction f = inv.getArgument(0);
            facts.put(f.getTransactionId(), f);
            return f;
        });
        lenient().when(factTransactionRepository.findActiveReportableByMonth(any(), any())).thenAnswer(inv -> {
            UUID uid = inv.getArgument(0);
            String ym = inv.getArgument(1);
            return facts.values().stream()
                    .filter(f -> f.getUserId().equals(uid)
                            && ym.equals(f.getYearMonth())
                            && !f.isDeleted()
                            && !f.isExcluded()
                            && ("INCOME".equals(f.getType()) || "EXPENSE".equals(f.getType())))
                    .toList();
        });
        lenient().when(factTransactionRepository.findActiveReportableByDate(any(), any())).thenAnswer(inv -> {
            UUID uid = inv.getArgument(0);
            LocalDate d = inv.getArgument(1);
            return facts.values().stream()
                    .filter(f -> f.getUserId().equals(uid)
                            && d.equals(f.getOccurredDate())
                            && !f.isDeleted()
                            && !f.isExcluded()
                            && ("INCOME".equals(f.getType()) || "EXPENSE".equals(f.getType())))
                    .toList();
        });

        lenient().when(aggMonthlySummaryRepository.findByUserIdAndYearMonth(any(), any())).thenAnswer(inv ->
                Optional.ofNullable(summaries.get(inv.getArgument(1))));
        lenient().when(aggMonthlySummaryRepository.save(any())).thenAnswer(inv -> {
            AggMonthlySummary s = inv.getArgument(0);
            summaries.put(s.getYearMonth(), s);
            return s;
        });
        lenient().doAnswer(inv -> {
            AggMonthlySummary s = inv.getArgument(0);
            summaries.remove(s.getYearMonth());
            return null;
        }).when(aggMonthlySummaryRepository).delete(any());

        lenient().when(aggMonthlyCategoryRepository.findByUserIdAndYearMonth(any(), any())).thenAnswer(inv ->
                new ArrayList<>(categoriesByMonth.getOrDefault(inv.getArgument(1), List.of())));
        lenient().when(aggMonthlyCategoryRepository.saveAll(any())).thenAnswer(inv -> {
            Iterable<AggMonthlyCategory> list = inv.getArgument(0);
            for (AggMonthlyCategory c : list) {
                List<AggMonthlyCategory> bucket =
                        categoriesByMonth.computeIfAbsent(c.getYearMonth(), k -> new ArrayList<>());
                bucket.removeIf(existing -> existing.getCategoryId().equals(c.getCategoryId()));
                bucket.add(c);
            }
            return list;
        });
        lenient().doAnswer(inv -> {
            Iterable<AggMonthlyCategory> obsolete = inv.getArgument(0);
            for (AggMonthlyCategory c : obsolete) {
                List<AggMonthlyCategory> bucket = categoriesByMonth.get(c.getYearMonth());
                if (bucket != null) {
                    bucket.removeIf(existing -> existing.getCategoryId().equals(c.getCategoryId()));
                }
            }
            return null;
        }).when(aggMonthlyCategoryRepository).deleteAll(any());

        lenient().when(aggDailyRepository.findByUserIdAndOccurredDate(any(), any())).thenAnswer(inv ->
                Optional.ofNullable(dailies.get(inv.getArgument(1))));
        lenient().when(aggDailyRepository.save(any())).thenAnswer(inv -> {
            AggDaily d = inv.getArgument(0);
            dailies.put(d.getOccurredDate(), d);
            return d;
        });
        lenient().doAnswer(inv -> {
            AggDaily d = inv.getArgument(0);
            dailies.remove(d.getOccurredDate());
            return null;
        }).when(aggDailyRepository).delete(any());
    }

    @Test
    void createAndDelete_recomputesTotalsAndMomPercentAcrossMonths() {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID salaryCatId = UUID.randomUUID();
        UUID bonusCatId = UUID.randomUUID();
        UUID octTxnId = UUID.randomUUID();
        UUID novSalaryId = UUID.randomUUID();
        UUID novBonusId = UUID.randomUUID();

        // Thang 10: Luong 10.000.000
        service.processEvent(
                UUID.randomUUID(),
                "TransactionCreated",
                userId,
                octTxnId,
                mapper.readTree(createdPayloadJson(octTxnId, walletId, salaryCatId, "Lương", "INCOME", 10_000_000L, 10_000_000L, "2026-10-25"))
        );

        // Thang 11: Luong 12.000.000 + Thuong 3.000.000 = 15.000.000 (+50.00% vs T10)
        service.processEvent(
                UUID.randomUUID(),
                "TransactionCreated",
                userId,
                novSalaryId,
                mapper.readTree(createdPayloadJson(novSalaryId, walletId, salaryCatId, "Lương", "INCOME", 12_000_000L, 12_000_000L, "2026-11-10"))
        );
        service.processEvent(
                UUID.randomUUID(),
                "TransactionCreated",
                userId,
                novBonusId,
                mapper.readTree(createdPayloadJson(novBonusId, walletId, bonusCatId, "Thưởng", "INCOME", 3_000_000L, 3_000_000L, "2026-11-15"))
        );

        AggMonthlySummary novSummary = summaries.get("2026-11");
        assertThat(novSummary).isNotNull();
        assertThat(novSummary.getTotalIncome()).isEqualTo(15_000_000L);
        assertThat(novSummary.getIncomeMomPercent()).isEqualByComparingTo(new BigDecimal("50.00"));

        List<AggMonthlyCategory> novCats = categoriesByMonth.get("2026-11");
        assertThat(novCats).hasSize(2);
        AggMonthlyCategory salaryAgg = novCats.stream()
                .filter(c -> c.getCategoryId().equals(salaryCatId))
                .findFirst().orElseThrow();
        assertThat(salaryAgg.getSharePercent()).isEqualByComparingTo(new BigDecimal("80.00"));
        assertThat(salaryAgg.getMomChangePercent()).isEqualByComparingTo(new BigDecimal("20.00"));

        // Xoa khoan Thuong 3.000.000 -> T11 con 12.000.000 (+20.00% vs T10)
        service.processEvent(
                UUID.randomUUID(),
                "TransactionDeleted",
                userId,
                novBonusId,
                mapper.readTree("""
                        {
                          "transactionId": "%s",
                          "userId": "%s",
                          "walletId": "%s",
                          "categoryId": "%s",
                          "type": "INCOME",
                          "amount": 3000000,
                          "signedAmount": 3000000,
                          "occurredAt": "2026-11-15T04:00:00Z",
                          "occurredDate": "2026-11-15",
                          "isExcludedFromReport": false
                        }
                        """.formatted(novBonusId, userId, walletId, bonusCatId))
        );

        assertThat(summaries.get("2026-11").getTotalIncome()).isEqualTo(12_000_000L);
        assertThat(summaries.get("2026-11").getIncomeMomPercent()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(categoriesByMonth.get("2026-11")).hasSize(1);
    }

    @Test
    void duplicateEventId_isProcessedOnlyOnce() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID catId = UUID.randomUUID();
        UUID txnId = UUID.randomUUID();
        String payload = createdPayloadJson(txnId, walletId, catId, "Ăn uống", "EXPENSE", 50_000L, -50_000L, "2026-11-18");

        service.processEvent(eventId, "TransactionCreated", userId, txnId, mapper.readTree(payload));
        service.processEvent(eventId, "TransactionCreated", userId, txnId, mapper.readTree(payload));

        assertThat(summaries.get("2026-11").getTotalExpense()).isEqualTo(50_000L);
        assertThat(summaries.get("2026-11").getExpenseTxnCount()).isEqualTo(1);
    }

    @Test
    void unknownEventType_isIgnoredSilently() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        service.processEvent(eventId, "UserRegistered", userId, userId, mapper.readTree("{\"email\":\"a@b.com\"}"));

        assertThat(processedIds).contains(eventId);
        assertThat(facts).isEmpty();
        assertThat(summaries).isEmpty();
    }

    @Test
    void uncategorizedTransaction_countsInSummaryButSkippedFromCategoryAgg_D9() {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID txnId = UUID.randomUUID();
        String payload = """
                {
                  "transactionId": "%s",
                  "walletId": "%s",
                  "walletName": "Tiền mặt",
                  "walletKind": "CASH",
                  "categoryId": null,
                  "categoryName": null,
                  "type": "EXPENSE",
                  "amount": 120000,
                  "signedAmount": -120000,
                  "currencyCode": "VND",
                  "occurredAt": "2026-11-18T05:00:00Z",
                  "occurredDate": "2026-11-18",
                  "source": "MANUAL",
                  "isExcludedFromReport": false
                }
                """.formatted(txnId, walletId);

        service.processEvent(UUID.randomUUID(), "TransactionCreated", userId, txnId, mapper.readTree(payload));

        assertThat(facts).containsKey(txnId);
        assertThat(summaries.get("2026-11").getTotalExpense()).isEqualTo(120_000L);
        assertThat(categoriesByMonth.getOrDefault("2026-11", List.of())).isEmpty();
    }

    @Test
    void transferAndAdjustment_savedToFactButExcludedFromAggregates_D10() {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        UUID adjustId = UUID.randomUUID();

        String transferPayload = """
                {
                  "transactionId": "%s",
                  "walletId": "%s",
                  "walletName": "Vietcombank",
                  "walletKind": "BANK",
                  "type": "TRANSFER_OUT",
                  "amount": 2000000,
                  "signedAmount": -2000000,
                  "currencyCode": "VND",
                  "occurredAt": "2026-11-18T06:00:00Z",
                  "occurredDate": "2026-11-18",
                  "source": "MANUAL",
                  "isExcludedFromReport": false
                }
                """.formatted(transferId, walletId);

        String adjustPayload = """
                {
                  "transactionId": "%s",
                  "walletId": "%s",
                  "walletName": "Tiền mặt",
                  "walletKind": "CASH",
                  "type": "ADJUSTMENT",
                  "amount": 50000,
                  "signedAmount": 50000,
                  "currencyCode": "VND",
                  "occurredAt": "2026-11-18T07:00:00Z",
                  "occurredDate": "2026-11-18",
                  "source": "MANUAL",
                  "isExcludedFromReport": false
                }
                """.formatted(adjustId, walletId);

        service.processEvent(UUID.randomUUID(), "TransactionCreated", userId, transferId, mapper.readTree(transferPayload));
        service.processEvent(UUID.randomUUID(), "TransactionCreated", userId, adjustId, mapper.readTree(adjustPayload));

        assertThat(facts).containsKeys(transferId, adjustId);
        assertThat(summaries).isEmpty();
        assertThat(dailies).isEmpty();
    }

    @Test
    void walletCreated_upsertsDimWalletAndIgnoresDuplicateEventId() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        String payload = """
                {
                  "walletId": "%s",
                  "name": "Vietcombank",
                  "kind": "BANK",
                  "color": "#0E9F6E",
                  "initialBalance": 11510000,
                  "currencyCode": "VND",
                  "includeInTotal": true,
                  "createdAt": "2026-11-20T05:00:00Z"
                }
                """.formatted(walletId);

        service.processEvent(eventId, "WalletCreated", userId, walletId, mapper.readTree(payload));
        service.processEvent(eventId, "WalletCreated", userId, walletId, mapper.readTree(payload));

        assertThat(wallets).containsKey(walletId);
        DimWallet saved = wallets.get(walletId);
        assertThat(saved.getName()).isEqualTo("Vietcombank");
        assertThat(saved.getKind()).isEqualTo("BANK");
        assertThat(saved.getColor()).isEqualTo("#0E9F6E");
        assertThat(saved.getInitialBalance()).isEqualTo(11_510_000L);
        assertThat(saved.isIncludeInTotal()).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(processedIds).contains(eventId);
    }

    @Test
    void transactionCreatedBeforeWalletCreated_upsertsPlaceholderThenWalletCreatedFillsInitialBalance() {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID txnId = UUID.randomUUID();
        UUID catId = UUID.randomUUID();

        service.processEvent(
                UUID.randomUUID(),
                "TransactionCreated",
                userId,
                txnId,
                mapper.readTree(createdPayloadJson(txnId, walletId, catId, "Lương", "INCOME", 5_000_000L, 5_000_000L, "2026-11-10"))
        );

        assertThat(wallets).containsKey(walletId);
        assertThat(wallets.get(walletId).getInitialBalance()).isEqualTo(0L);
        assertThat(wallets.get(walletId).getName()).isEqualTo("Vietcombank");

        String walletPayload = """
                {
                  "walletId": "%s",
                  "name": "Vietcombank",
                  "kind": "BANK",
                  "color": "#0E9F6E",
                  "initialBalance": 10000000,
                  "currencyCode": "VND",
                  "includeInTotal": true,
                  "createdAt": "2026-11-01T00:00:00Z"
                }
                """.formatted(walletId);
        service.processEvent(UUID.randomUUID(), "WalletCreated", userId, walletId, mapper.readTree(walletPayload));

        assertThat(wallets.get(walletId).getInitialBalance()).isEqualTo(10_000_000L);
        assertThat(wallets.get(walletId).getColor()).isEqualTo("#0E9F6E");
    }

    private static String createdPayloadJson(UUID txnId,
                                             UUID walletId,
                                             UUID categoryId,
                                             String categoryName,
                                             String type,
                                             long amount,
                                             long signedAmount,
                                             String occurredDate) {
        return """
                {
                  "transactionId": "%s",
                  "walletId": "%s",
                  "walletName": "Vietcombank",
                  "walletKind": "BANK",
                  "categoryId": "%s",
                  "categoryName": "%s",
                  "categoryFlow": "%s",
                  "categoryIcon": "Payments",
                  "categoryColor": "#16A34A",
                  "type": "%s",
                  "amount": %d,
                  "signedAmount": %d,
                  "currencyCode": "VND",
                  "occurredAt": "%sT03:00:00Z",
                  "occurredDate": "%s",
                  "merchant": "Công ty",
                  "note": null,
                  "source": "MANUAL",
                  "isExcludedFromReport": false
                }
                """.formatted(txnId, walletId, categoryId, categoryName, type, type, amount, signedAmount, occurredDate, occurredDate);
    }
}
