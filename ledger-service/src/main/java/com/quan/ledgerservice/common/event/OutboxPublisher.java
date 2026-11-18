package com.quan.ledgerservice.common.event;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private static final long CONFIRM_TIMEOUT_SECONDS = 5L;
    private static final int MAX_ERROR_LENGTH = 500;
    private static final String RETURNED_CORRELATION_HEADER = "spring_returned_message_correlation";

    private final OutboxEventRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final Set<String> returnedCorrelationIds = ConcurrentHashMap.newKeySet();

    @Value("${app.events.exchange}")
    private String exchange;

    @PostConstruct
    void configureRabbitTemplate() {
        // Bat mandatory + returns callback: neu exchange chua co queue nao bind,
        // broker tra message ve va ta giu published_at = NULL de lan quet sau gui lai.
        rabbitTemplate.setMandatory(true);
        rabbitTemplate.setReturnsCallback(this::onMessageReturned);
    }

    void onMessageReturned(ReturnedMessage returned) {
        Object correlationHeader = returned.getMessage()
                .getMessageProperties()
                .getHeader(RETURNED_CORRELATION_HEADER);
        if (correlationHeader != null) {
            returnedCorrelationIds.add(correlationHeader.toString());
        }
        log.warn("Outbox event bi broker return (replyCode={}, replyText={}, routingKey={})",
                returned.getReplyCode(), returned.getReplyText(), returned.getRoutingKey());
    }

    @Scheduled(initialDelay = 10000, fixedDelay = 2000)
    @Transactional
    public void publishPending() {
        List<OutboxEvent> pending = outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
        for (OutboxEvent event : pending) {
            String correlationId = event.getId() + "-" + System.nanoTime();
            CorrelationData correlationData = new CorrelationData(correlationId);
            String key = routingKey(event.getEventType());
            try {
                rabbitTemplate.convertAndSend(exchange, key, event.getPayload(), correlationData);
                CorrelationData.Confirm confirm = correlationData.getFuture()
                        .get(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                boolean wasReturned = correlationData.getReturned() != null
                        || returnedCorrelationIds.remove(correlationId);

                if (confirm != null && confirm.ack() && !wasReturned) {
                    event.setPublishedAt(Instant.now());
                    event.setLastError(null);
                } else {
                    String reason = wasReturned
                            ? "Khong co queue nao bind cho routingKey=" + key
                            : (confirm != null ? confirm.reason() : "Publisher confirm null");
                    markRetry(event, reason);
                    log.warn("Chua xac nhan duoc outbox event {}: {}", event.getId(), reason);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                returnedCorrelationIds.remove(correlationId);
                markRetry(event, e.getMessage());
                break;
            } catch (Exception e) {
                returnedCorrelationIds.remove(correlationId);
                markRetry(event, e.getMessage());
                log.warn("Khong gui duoc outbox event {}: {}", event.getId(), e.getMessage());
            }
        }
    }

    /** TransactionCreated -> transaction.created */
    String routingKey(String eventType) {
        return eventType.replaceAll("([a-z])([A-Z])", "$1.$2").toLowerCase(Locale.ROOT);
    }

    private void markRetry(OutboxEvent event, String error) {
        event.setAttempts((short) (event.getAttempts() + 1));
        if (error == null) {
            event.setLastError("Unknown publish error");
        } else {
            event.setLastError(error.length() > MAX_ERROR_LENGTH ? error.substring(0, MAX_ERROR_LENGTH) : error);
        }
    }
}
