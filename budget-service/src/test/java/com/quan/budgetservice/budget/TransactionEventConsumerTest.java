package com.quan.budgetservice.budget;

import com.quan.budgetservice.common.event.ProcessedEvent;
import com.quan.budgetservice.common.event.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionEventConsumerTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;
    @Mock
    private SpendingSnapshotRepository spendingSnapshotRepository;

    private TransactionEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new TransactionEventConsumer(new ObjectMapper(), processedEventRepository, spendingSnapshotRepository);
    }

    @Test
    void onMessage_transactionCreated_addsToSnapshotOnceEvenIfDuplicated() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        String json = """
                {
                  "eventId": "%s",
                  "eventType": "TransactionCreated",
                  "version": 1,
                  "occurredAt": "2026-11-22T10:00:00Z",
                  "userId": "%s",
                  "aggregateId": "%s",
                  "payload": {
                    "transactionId": "%s",
                    "categoryId": "%s",
                    "type": "EXPENSE",
                    "amount": 500000,
                    "occurredAt": "2026-11-22T10:00:00Z",
                    "occurredDate": "2026-11-22",
                    "isExcludedFromReport": false
                  }
                }
                """.formatted(eventId, userId, UUID.randomUUID(), UUID.randomUUID(), categoryId);

        when(processedEventRepository.existsById(eventId)).thenReturn(false, true);

        consumer.onMessage(json);
        consumer.onMessage(json);

        verify(processedEventRepository, times(1)).save(any(ProcessedEvent.class));
        verify(spendingSnapshotRepository, times(1)).upsertDelta(
                userId, "2026-11", categoryId, 500_000L, 1, Instant.parse("2026-11-22T10:00:00Z")
        );
    }

    @ParameterizedTest
    @CsvSource({
            "America/Los_Angeles,2026-11", "Asia/Ho_Chi_Minh,2026-12", "Unknown/Zone,2026-12"
    })
    void legacyEventWithoutBusinessDateUsesItsZoneAtMonthBoundary(String zone, String expectedPeriod) {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        Instant instant = Instant.parse("2026-11-30T17:15:00Z");
        String message = """
                {"eventId":"%s","eventType":"TransactionCreated","userId":"%s",
                 "payload":{"categoryId":"%s","type":"EXPENSE","amount":1700,
                            "occurredAt":"%s","timezone":"%s"}}
                """.formatted(UUID.randomUUID(), userId, categoryId, instant, zone);
        consumer.onMessage(message);

        verify(spendingSnapshotRepository).upsertDelta(userId, expectedPeriod, categoryId, 1700L, 1, instant);
    }

    @Test
    void onMessage_transactionDeleted_subtractsFromSnapshot() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        String json = """
                {
                  "eventId": "%s",
                  "eventType": "TransactionDeleted",
                  "version": 1,
                  "occurredAt": "2026-11-22T11:00:00Z",
                  "userId": "%s",
                  "aggregateId": "%s",
                  "payload": {
                    "transactionId": "%s",
                    "userId": "%s",
                    "categoryId": "%s",
                    "type": "EXPENSE",
                    "amount": 250000,
                    "occurredAt": "2026-11-22T10:00:00Z",
                    "occurredDate": "2026-11-22",
                    "isExcludedFromReport": false
                  }
                }
                """.formatted(eventId, userId, UUID.randomUUID(), UUID.randomUUID(), userId, categoryId);

        when(processedEventRepository.existsById(eventId)).thenReturn(false);

        consumer.onMessage(json);

        verify(spendingSnapshotRepository).upsertDelta(
                userId, "2026-11", categoryId, -250_000L, -1, Instant.parse("2026-11-22T10:00:00Z")
        );
    }

    @Test
    void onMessage_incomeExcludedOrNullCategory_isIgnored() {
        UUID event1 = UUID.randomUUID();
        UUID event2 = UUID.randomUUID();
        UUID event3 = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        when(processedEventRepository.existsById(any())).thenReturn(false);

        String incomeJson = """
                {"eventId":"%s","eventType":"TransactionCreated","userId":"%s","payload":{
                  "categoryId":"%s","type":"INCOME","amount":1000000,"occurredDate":"2026-11-22","isExcludedFromReport":false}}
                """.formatted(event1, userId, categoryId);
        String excludedJson = """
                {"eventId":"%s","eventType":"TransactionCreated","userId":"%s","payload":{
                  "categoryId":"%s","type":"EXPENSE","amount":100000,"occurredDate":"2026-11-22","isExcludedFromReport":true}}
                """.formatted(event2, userId, categoryId);
        String noCatJson = """
                {"eventId":"%s","eventType":"TransactionCreated","userId":"%s","payload":{
                  "categoryId":null,"type":"EXPENSE","amount":100000,"occurredDate":"2026-11-22","isExcludedFromReport":false}}
                """.formatted(event3, userId);

        consumer.onMessage(incomeJson);
        consumer.onMessage(excludedJson);
        consumer.onMessage(noCatJson);

        verify(spendingSnapshotRepository, never()).upsertDelta(any(), any(), any(), any(Long.class), any(Integer.class), any());
    }

    @Test
    void onMessage_unsupportedEventType_rejectsWithoutMarkingProcessed() {
        UUID eventId = UUID.randomUUID();
        String json = """
                {"eventId":"%s","eventType":"TransactionUpdated","userId":"%s","payload":{}}
                """.formatted(eventId, UUID.randomUUID());

        assertThatThrownBy(() -> consumer.onMessage(json))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verify(processedEventRepository, never()).save(any());
        verify(spendingSnapshotRepository, never()).upsertDelta(any(), any(), any(), any(Long.class), any(Integer.class), any());
    }
}
