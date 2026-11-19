package com.quan.reportservice.ingest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_events")
@Getter
@Setter
@NoArgsConstructor
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(name = "aggregate_id")
    private UUID aggregateId;

    @CreationTimestamp
    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    public ProcessedEvent(UUID eventId, String eventType, UUID aggregateId) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
    }
}
