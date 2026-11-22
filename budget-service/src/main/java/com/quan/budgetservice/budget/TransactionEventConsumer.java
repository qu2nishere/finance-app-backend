package com.quan.budgetservice.budget;

import com.quan.budgetservice.common.config.RabbitConfig;
import com.quan.budgetservice.common.event.ProcessedEvent;
import com.quan.budgetservice.common.event.ProcessedEventRepository;
import com.quan.budgetservice.common.util.PeriodUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventConsumer {

    private static final Set<String> SUPPORTED_EVENTS = Set.of("TransactionCreated", "TransactionDeleted");

    private final ObjectMapper objectMapper;
    private final ProcessedEventRepository processedEventRepository;
    private final SpendingSnapshotRepository spendingSnapshotRepository;

    @RabbitListener(queues = RabbitConfig.TXN_EVENTS_QUEUE)
    @Transactional
    public void onMessage(String rawJson) {
        JsonNode envelope = objectMapper.readTree(rawJson);
        String eventType = optionalString(envelope, "eventType");
        String eventIdText = optionalString(envelope, "eventId");
        if (eventType == null || eventIdText == null) {
            throw new AmqpRejectAndDontRequeueException("Event thieu eventId hoac eventType");
        }

        // Chi ghi processed_events cho event consumer biet xu ly; loai la -> day thang DLQ
        if (!SUPPORTED_EVENTS.contains(eventType)) {
            log.warn("UNSUPPORTED_EVENT_TYPE o budget-service: eventId={}, eventType={}", eventIdText, eventType);
            throw new AmqpRejectAndDontRequeueException("UNSUPPORTED_EVENT_TYPE: " + eventType);
        }

        UUID eventId = UUID.fromString(eventIdText);
        if (processedEventRepository.existsById(eventId)) {
            log.warn("Bo qua event trung lap eventId={} eventType={}", eventId, eventType);
            return;
        }
        processedEventRepository.save(new ProcessedEvent(eventId, eventType));

        JsonNode payload = envelope.path("payload");
        if (payload.isMissingNode() || payload.isNull()) {
            log.warn("Event {} khong co payload, bo qua", eventId);
            return;
        }

        String type = optionalString(payload, "type");
        boolean excluded = payload.path("isExcludedFromReport").asBoolean(false);
        UUID categoryId = parseOptionalUuid(payload, "categoryId");
        long amount = payload.path("amount").asLong(0L);

        if (!"EXPENSE".equalsIgnoreCase(type) || excluded || categoryId == null || amount <= 0) {
            log.debug("Bo qua event {} (type={}, excluded={}, categoryId={}, amount={})",
                    eventId, type, excluded, categoryId, amount);
            return;
        }

        UUID userId = parseOptionalUuid(envelope, "userId");
        if (userId == null) {
            userId = parseOptionalUuid(payload, "userId");
        }
        if (userId == null) {
            throw new AmqpRejectAndDontRequeueException("Event thieu userId: " + eventId);
        }

        Instant occurredAt = parseOptionalInstant(payload, "occurredAt");
        if (occurredAt == null) {
            occurredAt = parseOptionalInstant(envelope, "occurredAt");
        }
        LocalDate occurredDate = parseOccurredDate(payload, occurredAt);
        String periodKey = PeriodUtils.periodKey(occurredDate);

        if ("TransactionCreated".equals(eventType)) {
            spendingSnapshotRepository.upsertDelta(userId, periodKey, categoryId, amount, 1, occurredAt);
        } else if ("TransactionDeleted".equals(eventType)) {
            spendingSnapshotRepository.upsertDelta(userId, periodKey, categoryId, -amount, -1, occurredAt);
        }
    }

    private static LocalDate parseOccurredDate(JsonNode payload, Instant occurredAt) {
        String dateText = optionalString(payload, "occurredDate");
        if (dateText != null) {
            return LocalDate.parse(dateText);
        }
        if (occurredAt != null) {
            return LocalDate.ofInstant(occurredAt, PeriodUtils.USER_ZONE);
        }
        return LocalDate.now(PeriodUtils.USER_ZONE);
    }

    private static String optionalString(JsonNode node, String field) {
        JsonNode child = node.path(field);
        if (child.isMissingNode() || child.isNull()) {
            return null;
        }
        String text = child.asString();
        return (text == null || text.isBlank()) ? null : text.trim();
    }

    private static UUID parseOptionalUuid(JsonNode node, String field) {
        String text = optionalString(node, field);
        return text != null ? UUID.fromString(text) : null;
    }

    private static Instant parseOptionalInstant(JsonNode node, String field) {
        String text = optionalString(node, field);
        return text != null ? Instant.parse(text) : null;
    }
}
