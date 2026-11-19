package com.quan.ledgerservice.common.event;

import java.time.Instant;
import java.util.UUID;

public record EventEnvelope(
        UUID eventId,
        String eventType,
        int version,
        Instant occurredAt,
        UUID userId,
        UUID aggregateId,
        Object payload
) {
}
