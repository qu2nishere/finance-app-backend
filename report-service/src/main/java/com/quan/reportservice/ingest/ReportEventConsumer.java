package com.quan.reportservice.ingest;

import com.quan.reportservice.common.config.RabbitConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReportEventConsumer {

    private static final int MAX_ATTEMPTS = 3;

    private final ObjectMapper objectMapper;
    private final ReadModelProjectionService projectionService;

    @RabbitListener(queues = RabbitConfig.ALL_EVENTS_QUEUE)
    public void onMessage(String rawJson) {
        UUID eventId = null;
        String eventType = null;
        try {
            JsonNode envelope = objectMapper.readTree(rawJson);
            eventType = optionalString(envelope, "eventType");
            if (eventType == null) {
                throw new IllegalArgumentException("Event envelope missing eventType");
            }
            String eventIdStr = optionalString(envelope, "eventId");
            if (eventIdStr == null) {
                throw new IllegalArgumentException("Event envelope missing eventId");
            }
            eventId = UUID.fromString(eventIdStr);
            UUID userId = parseOptionalUuid(envelope, "userId");
            UUID aggregateId = parseOptionalUuid(envelope, "aggregateId");
            JsonNode payload = envelope.path("payload");

            executeWithRetry(eventId, eventType, userId, aggregateId, payload, rawJson);
        } catch (Exception ex) {
            log.error("Loi parse/xu ly event o report-service (eventId={}, eventType={})", eventId, eventType, ex);
            String safeJson = isValidJsonObject(rawJson) ? rawJson : "{\"raw\":\"invalid_json\"}";
            projectionService.recordDeadLetter(eventId, eventType, safeJson, ex.getMessage(), 1);
        }
    }

    private void executeWithRetry(UUID eventId,
                                  String eventType,
                                  UUID userId,
                                  UUID aggregateId,
                                  JsonNode payload,
                                  String rawJson) {
        Exception lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                projectionService.processEvent(eventId, eventType, userId, aggregateId, payload);
                return;
            } catch (Exception ex) {
                lastError = ex;
                log.warn("Xu ly event {} lan {}/{} that bai: {}", eventId, attempt, MAX_ATTEMPTS, ex.getMessage());
            }
        }
        String errMsg = lastError != null ? lastError.getMessage() : "Exceeded max retry attempts";
        log.error("Event {} vuot qua {} lan thu, ghi vao dead_letter_events", eventId, MAX_ATTEMPTS, lastError);
        projectionService.recordDeadLetter(eventId, eventType, rawJson, errMsg, MAX_ATTEMPTS);
    }

    private boolean isValidJsonObject(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return false;
        }
        try {
            objectMapper.readTree(rawJson);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String optionalString(JsonNode node, String field) {
        JsonNode child = node.path(field);
        if (child.isMissingNode() || child.isNull()) {
            return null;
        }
        String s = child.asString();
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static UUID parseOptionalUuid(JsonNode node, String field) {
        String val = optionalString(node, field);
        return val != null ? UUID.fromString(val) : null;
    }
}
