package com.quan.ledgerservice.wallet;

import com.quan.ledgerservice.common.event.EventPublisher;
import com.quan.ledgerservice.common.event.OutboxEvent;
import com.quan.ledgerservice.common.event.OutboxEventRepository;
import com.quan.ledgerservice.wallet.dto.CreateWalletRequest;
import com.quan.ledgerservice.wallet.dto.WalletResponse;
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
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;

    private ObjectMapper objectMapper;
    private WalletService walletService;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        EventPublisher eventPublisher = new EventPublisher(outboxEventRepository, objectMapper);
        walletService = new WalletService(walletRepository, eventPublisher);
    }

    @Test
    void createWallet_savesWalletAndPublishesWalletCreatedOutbox() {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-11-20T05:00:00Z");

        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> {
            Wallet w = inv.getArgument(0);
            w.setId(walletId);
            w.setCreatedAt(createdAt);
            return w;
        });

        CreateWalletRequest request = new CreateWalletRequest(
                "  Vietcombank  ",
                WalletKind.BANK,
                11_510_000L,
                "AccountBalance",
                "#0E9F6E",
                "Tài khoản lương"
        );

        WalletResponse response = walletService.createWallet(userId, request);

        assertThat(response.id()).isEqualTo(walletId);
        assertThat(response.name()).isEqualTo("Vietcombank");
        assertThat(response.currentBalance()).isEqualTo(11_510_000L);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());

        OutboxEvent outbox = captor.getValue();
        assertThat(outbox.getAggregateType()).isEqualTo("WALLET");
        assertThat(outbox.getAggregateId()).isEqualTo(walletId);
        assertThat(outbox.getEventType()).isEqualTo("WalletCreated");
        assertThat(outbox.getPublishedAt()).isNull();

        JsonNode root = objectMapper.readTree(outbox.getPayload());
        assertThat(root.path("eventType").asString()).isEqualTo("WalletCreated");
        assertThat(root.path("userId").asString()).isEqualTo(userId.toString());
        assertThat(root.path("aggregateId").asString()).isEqualTo(walletId.toString());

        JsonNode payload = root.path("payload");
        assertThat(payload.path("walletId").asString()).isEqualTo(walletId.toString());
        assertThat(payload.path("name").asString()).isEqualTo("Vietcombank");
        assertThat(payload.path("kind").asString()).isEqualTo("BANK");
        assertThat(payload.path("color").asString()).isEqualTo("#0E9F6E");
        assertThat(payload.path("initialBalance").asLong()).isEqualTo(11_510_000L);
        assertThat(payload.path("currencyCode").asString()).isEqualTo("VND");
        assertThat(payload.path("includeInTotal").asBoolean()).isTrue();
        assertThat(payload.path("createdAt").asString()).isEqualTo("2026-11-20T05:00:00Z");
    }

    @Test
    void getWallets_whenEmpty_createsDefaultWalletAndPublishesWalletCreatedOutbox() {
        UUID userId = UUID.randomUUID();
        UUID defaultWalletId = UUID.randomUUID();

        when(walletRepository.findByUserIdAndDeletedAtIsNullOrderByDisplayOrderAsc(userId))
                .thenReturn(Collections.emptyList());
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> {
            Wallet w = inv.getArgument(0);
            w.setId(defaultWalletId);
            return w;
        });

        List<WalletResponse> result = walletService.getWallets(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Tiền mặt");

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());

        OutboxEvent outbox = captor.getValue();
        assertThat(outbox.getAggregateType()).isEqualTo("WALLET");
        assertThat(outbox.getAggregateId()).isEqualTo(defaultWalletId);
        assertThat(outbox.getEventType()).isEqualTo("WalletCreated");

        JsonNode payload = objectMapper.readTree(outbox.getPayload()).path("payload");
        assertThat(payload.path("name").asString()).isEqualTo("Tiền mặt");
        assertThat(payload.path("kind").asString()).isEqualTo("CASH");
        assertThat(payload.path("initialBalance").asLong()).isEqualTo(0L);
        assertThat(payload.path("includeInTotal").asBoolean()).isTrue();
    }

    @Test
    void getWallets_whenExisting_doesNotPublishOutbox() {
        UUID userId = UUID.randomUUID();
        Wallet existing = new Wallet();
        existing.setId(UUID.randomUUID());
        existing.setUserId(userId);
        existing.setName("Momo");
        existing.setKind(WalletKind.EWALLET);
        existing.setCurrencyCode("VND");

        when(walletRepository.findByUserIdAndDeletedAtIsNullOrderByDisplayOrderAsc(userId))
                .thenReturn(List.of(existing));

        List<WalletResponse> result = walletService.getWallets(userId);

        assertThat(result).hasSize(1);
        verify(outboxEventRepository, never()).save(any());
    }
}
