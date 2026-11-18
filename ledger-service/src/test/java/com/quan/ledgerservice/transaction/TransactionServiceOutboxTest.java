package com.quan.ledgerservice.transaction;

import com.quan.ledgerservice.category.Category;
import com.quan.ledgerservice.category.CategoryFlow;
import com.quan.ledgerservice.category.CategoryRepository;
import com.quan.ledgerservice.common.event.EventPublisher;
import com.quan.ledgerservice.common.event.OutboxEvent;
import com.quan.ledgerservice.common.event.OutboxEventRepository;
import com.quan.ledgerservice.common.exception.ResourceNotFoundException;
import com.quan.ledgerservice.transaction.dto.CreateTransactionRequest;
import com.quan.ledgerservice.wallet.Wallet;
import com.quan.ledgerservice.wallet.WalletKind;
import com.quan.ledgerservice.wallet.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceOutboxTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private WalletRepository walletRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;

    private ObjectMapper objectMapper;
    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        EventPublisher eventPublisher = new EventPublisher(outboxEventRepository, objectMapper);
        transactionService = new TransactionService(
                transactionRepository,
                walletRepository,
                categoryRepository,
                eventPublisher
        );
    }

    @Test
    void create_withCategoryAndParent_savesOutboxEventWithFullEnvelope() {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID parentCatId = UUID.randomUUID();
        UUID catId = UUID.randomUUID();
        UUID txnId = UUID.randomUUID();

        Wallet wallet = new Wallet();
        wallet.setId(walletId);
        wallet.setUserId(userId);
        wallet.setName("Vietcombank");
        wallet.setKind(WalletKind.BANK);
        wallet.setCurrencyCode("VND");
        wallet.setCurrentBalance(5_000_000L);

        Category parentCategory = new Category();
        parentCategory.setId(parentCatId);
        parentCategory.setName("Ăn uống");
        parentCategory.setFlow(CategoryFlow.EXPENSE);

        Category category = new Category();
        category.setId(catId);
        category.setParentId(parentCatId);
        category.setName("Cà phê");
        category.setFlow(CategoryFlow.EXPENSE);
        category.setIcon("LocalCafe");
        category.setColor("#EF4444");

        when(walletRepository.findByIdAndUserIdAndDeletedAtIsNull(walletId, userId))
                .thenReturn(Optional.of(wallet));
        when(categoryRepository.findByIdAndDeletedAtIsNull(catId))
                .thenReturn(Optional.of(category));
        when(categoryRepository.findByIdAndDeletedAtIsNull(parentCatId))
                .thenReturn(Optional.of(parentCategory));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> {
            Transaction t = inv.getArgument(0);
            t.setId(txnId);
            return t;
        });

        CreateTransactionRequest request = new CreateTransactionRequest(
                walletId,
                catId,
                TransactionType.EXPENSE,
                72_000L,
                Instant.parse("2026-11-18T02:15:00Z"),
                "Họp nhóm sáng",
                "Highlands Coffee"
        );

        transactionService.create(userId, request);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());

        OutboxEvent outbox = captor.getValue();
        assertThat(outbox.getAggregateType()).isEqualTo("TRANSACTION");
        assertThat(outbox.getAggregateId()).isEqualTo(txnId);
        assertThat(outbox.getEventType()).isEqualTo("TransactionCreated");
        assertThat(outbox.getPublishedAt()).isNull();

        JsonNode root = objectMapper.readTree(outbox.getPayload());
        assertThat(root.path("eventId").asString()).isNotBlank();
        assertThat(root.path("eventType").asString()).isEqualTo("TransactionCreated");
        assertThat(root.path("version").asInt()).isEqualTo(1);
        assertThat(root.path("userId").asString()).isEqualTo(userId.toString());
        assertThat(root.path("aggregateId").asString()).isEqualTo(txnId.toString());

        JsonNode payload = root.path("payload");
        assertThat(payload.path("transactionId").asString()).isEqualTo(txnId.toString());
        assertThat(payload.path("walletName").asString()).isEqualTo("Vietcombank");
        assertThat(payload.path("walletKind").asString()).isEqualTo("BANK");
        assertThat(payload.path("categoryName").asString()).isEqualTo("Cà phê");
        assertThat(payload.path("categoryIcon").asString()).isEqualTo("LocalCafe");
        assertThat(payload.path("categoryColor").asString()).isEqualTo("#EF4444");
        assertThat(payload.path("parentCategoryId").asString()).isEqualTo(parentCatId.toString());
        assertThat(payload.path("parentCategoryName").asString()).isEqualTo("Ăn uống");
        assertThat(payload.path("type").asString()).isEqualTo("EXPENSE");
        assertThat(payload.path("amount").asLong()).isEqualTo(72_000L);
        assertThat(payload.path("signedAmount").asLong()).isEqualTo(-72_000L);
        assertThat(payload.path("occurredDate").asString()).isEqualTo("2026-11-18");
        assertThat(payload.path("merchant").asString()).isEqualTo("Highlands Coffee");
        assertThat(payload.path("note").asString()).isEqualTo("Họp nhóm sáng");
        assertThat(payload.path("source").asString()).isEqualTo("MANUAL");
    }

    @Test
    void create_withoutCategory_keepsCategoryFieldsNullInOutboxPayload() {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID txnId = UUID.randomUUID();

        Wallet wallet = new Wallet();
        wallet.setId(walletId);
        wallet.setUserId(userId);
        wallet.setName("Tiền mặt");
        wallet.setKind(WalletKind.CASH);
        wallet.setCurrencyCode("VND");

        when(walletRepository.findByIdAndUserIdAndDeletedAtIsNull(walletId, userId))
                .thenReturn(Optional.of(wallet));
        when(categoryRepository.findVisible(userId)).thenReturn(Collections.emptyList());
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> {
            Transaction t = inv.getArgument(0);
            t.setId(txnId);
            return t;
        });

        CreateTransactionRequest request = new CreateTransactionRequest(
                walletId,
                null,
                TransactionType.INCOME,
                18_000_000L,
                Instant.parse("2026-11-18T03:00:00Z"),
                null,
                "Lương tháng 11"
        );

        transactionService.create(userId, request);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());

        JsonNode payload = objectMapper.readTree(captor.getValue().getPayload()).path("payload");
        assertThat(payload.path("categoryId").isNull()).isTrue();
        assertThat(payload.path("categoryName").isNull()).isTrue();
        assertThat(payload.path("categoryIcon").isNull()).isTrue();
        assertThat(payload.path("categoryColor").isNull()).isTrue();
    }

    @Test
    void create_whenRepositoryThrows_doesNotWriteOutboxEvent() {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();

        Wallet wallet = new Wallet();
        wallet.setId(walletId);
        wallet.setUserId(userId);
        wallet.setName("Tiền mặt");
        wallet.setKind(WalletKind.CASH);
        wallet.setCurrencyCode("VND");

        when(walletRepository.findByIdAndUserIdAndDeletedAtIsNull(walletId, userId))
                .thenReturn(Optional.of(wallet));
        when(categoryRepository.findVisible(userId)).thenReturn(Collections.emptyList());
        when(transactionRepository.save(any(Transaction.class)))
                .thenThrow(new IllegalStateException("DB constraint failure"));

        CreateTransactionRequest request = new CreateTransactionRequest(
                walletId,
                null,
                TransactionType.EXPENSE,
                50_000L,
                Instant.now(),
                null,
                null
        );

        assertThatThrownBy(() -> transactionService.create(userId, request))
                .isInstanceOf(IllegalStateException.class);

        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void delete_existingTransaction_restoresBalanceAndPublishesDeletedOutbox() {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID txnId = UUID.randomUUID();
        UUID catId = UUID.randomUUID();

        Wallet wallet = new Wallet();
        wallet.setId(walletId);
        wallet.setUserId(userId);
        wallet.setCurrentBalance(900_000L);

        Transaction txn = new Transaction();
        txn.setId(txnId);
        txn.setUserId(userId);
        txn.setWalletId(walletId);
        txn.setCategoryId(catId);
        txn.setType(TransactionType.EXPENSE);
        txn.setAmount(100_000L);
        txn.setSignedAmount(-100_000L);
        txn.setOccurredAt(Instant.parse("2026-11-18T05:00:00Z"));
        txn.setOccurredDate(LocalDate.of(2026, 11, 18));

        when(transactionRepository.findByIdAndUserIdAndDeletedAtIsNull(txnId, userId))
                .thenReturn(Optional.of(txn));
        when(walletRepository.findByIdAndUserIdAndDeletedAtIsNull(walletId, userId))
                .thenReturn(Optional.of(wallet));

        transactionService.delete(userId, txnId);

        assertThat(wallet.getCurrentBalance()).isEqualTo(1_000_000L);
        assertThat(txn.getDeletedAt()).isNotNull();

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());

        OutboxEvent outbox = captor.getValue();
        assertThat(outbox.getEventType()).isEqualTo("TransactionDeleted");
        JsonNode payload = objectMapper.readTree(outbox.getPayload()).path("payload");
        assertThat(payload.path("transactionId").asString()).isEqualTo(txnId.toString());
        assertThat(payload.path("signedAmount").asLong()).isEqualTo(-100_000L);
        assertThat(payload.path("occurredDate").asString()).isEqualTo("2026-11-18");
    }

    @Test
    void delete_whenWalletNotFound_throwsAndDoesNotPublishOutbox() {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID txnId = UUID.randomUUID();

        Transaction txn = new Transaction();
        txn.setId(txnId);
        txn.setUserId(userId);
        txn.setWalletId(walletId);

        when(transactionRepository.findByIdAndUserIdAndDeletedAtIsNull(txnId, userId))
                .thenReturn(Optional.of(txn));
        when(walletRepository.findByIdAndUserIdAndDeletedAtIsNull(walletId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.delete(userId, txnId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(outboxEventRepository, never()).save(any());
    }
}
