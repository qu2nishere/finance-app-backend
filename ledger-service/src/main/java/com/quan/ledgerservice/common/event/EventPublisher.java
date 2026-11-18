package com.quan.ledgerservice.common.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class EventPublisher {

    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    /**
     * Goi ben trong @Transactional cua nghiep vu: chi ghi bang outbox_events,
     * chua day sang RabbitMQ de dam bao cung commit hoac cung rollback voi du lieu goc.
     */
    public void publish(String aggregateType,
                        UUID aggregateId,
                        String eventType,
                        UUID userId,
                        Object payload) {
        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID(),
                eventType,
                1,
                Instant.now(),
                userId,
                aggregateId,
                payload
        );

        OutboxEvent row = new OutboxEvent();
        row.setAggregateType(aggregateType);
        row.setAggregateId(aggregateId);
        row.setEventType(eventType);
        row.setPayload(toJson(envelope));
        outboxRepository.save(row);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Khong serialize duoc event", e);
        }
    }
}
