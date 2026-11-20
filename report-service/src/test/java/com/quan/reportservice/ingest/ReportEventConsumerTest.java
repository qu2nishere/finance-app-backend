package com.quan.reportservice.ingest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReportEventConsumerTest {

    @Mock
    private ReadModelProjectionService projectionService;

    private ReportEventConsumer consumer;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = JsonMapper.builder().build();
        consumer = new ReportEventConsumer(mapper, projectionService);
    }

    @Test
    void onMessage_validEnvelope_delegatesToProjectionServiceOnce() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID txnId = UUID.randomUUID();
        String json = """
                {
                  "eventId": "%s",
                  "eventType": "TransactionCreated",
                  "version": 1,
                  "occurredAt": "2026-11-18T03:00:00Z",
                  "userId": "%s",
                  "aggregateId": "%s",
                  "payload": { "transactionId": "%s", "amount": 100000 }
                }
                """.formatted(eventId, userId, txnId, txnId);

        consumer.onMessage(json);

        verify(projectionService, times(1))
                .processEvent(eq(eventId), eq("TransactionCreated"), eq(userId), eq(txnId), any(JsonNode.class));
        verify(projectionService, never())
                .recordDeadLetter(any(), any(), any(), any(), any(Integer.class));
    }

    @Test
    void onMessage_whenProjectionThrowsThreeTimes_recordsDeadLetterAndDoesNotPropagate() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String json = """
                {
                  "eventId": "%s",
                  "eventType": "TransactionCreated",
                  "version": 1,
                  "userId": "%s",
                  "payload": {}
                }
                """.formatted(eventId, userId);

        doThrow(new IllegalStateException("Simulated DB failure"))
                .when(projectionService)
                .processEvent(eq(eventId), eq("TransactionCreated"), eq(userId), any(), any(JsonNode.class));

        consumer.onMessage(json);

        verify(projectionService, times(3))
                .processEvent(eq(eventId), eq("TransactionCreated"), eq(userId), any(), any(JsonNode.class));
        verify(projectionService, times(1))
                .recordDeadLetter(eq(eventId), eq("TransactionCreated"), eq(json), eq("Simulated DB failure"), eq(3));
    }

    @Test
    void onMessage_invalidJson_recordsDeadLetterSafely() {
        consumer.onMessage("not-valid-json");

        verify(projectionService, never()).processEvent(any(), any(), any(), any(), any());
        verify(projectionService, times(1))
                .recordDeadLetter(eq(null), eq(null), eq("{\"raw\":\"invalid_json\"}"), any(), eq(1));
    }

    @Test
    void onMessage_unsupportedEventType_recordsDeadLetterOnceWithoutRetry() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String json = """
                {
                  "eventId": "%s",
                  "eventType": "BudgetExceeded",
                  "version": 1,
                  "occurredAt": "2026-11-20T03:00:00Z",
                  "userId": "%s",
                  "aggregateId": "%s",
                  "payload": {}
                }
                """.formatted(eventId, userId, userId);

        consumer.onMessage(json);

        verify(projectionService, never()).processEvent(any(), any(), any(), any(), any());
        verify(projectionService, times(1))
                .recordDeadLetter(eq(eventId), eq("BudgetExceeded"), eq(json),
                        eq("UNSUPPORTED_EVENT_TYPE: BudgetExceeded"), eq(1));
    }
}
