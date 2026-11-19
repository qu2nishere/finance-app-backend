package com.quan.ledgerservice.common.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxEventRepository outboxRepository;
    @Mock
    private RabbitTemplate rabbitTemplate;

    private OutboxPublisher outboxPublisher;

    @BeforeEach
    void setUp() {
        outboxPublisher = new OutboxPublisher(outboxRepository, rabbitTemplate);
        ReflectionTestUtils.setField(outboxPublisher, "exchange", "finance.events");
        outboxPublisher.configureRabbitTemplate();
    }

    @Test
    void publishPending_whenBrokerAcksAndNoReturn_setsPublishedAt() {
        OutboxEvent event = sampleEvent();
        when(outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of(event));

        doAnswer(inv -> {
            CorrelationData cd = inv.getArgument(3);
            cd.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                eq("finance.events"),
                eq("transaction.created"),
                eq(event.getPayload()),
                any(CorrelationData.class)
        );

        outboxPublisher.publishPending();

        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getAttempts()).isEqualTo((short) 0);
        assertThat(event.getLastError()).isNull();
    }

    @Test
    void publishPending_whenMessageReturnedUnrouted_keepsPublishedAtNullAndIncrementsAttempts() {
        OutboxEvent event = sampleEvent();
        when(outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of(event));

        doAnswer(inv -> {
            CorrelationData cd = inv.getArgument(3);
            Message msg = new Message("{}".getBytes(StandardCharsets.UTF_8), new MessageProperties());
            cd.setReturned(new ReturnedMessage(msg, 312, "NO_ROUTE", "finance.events", "transaction.created"));
            cd.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                eq("finance.events"),
                eq("transaction.created"),
                eq(event.getPayload()),
                any(CorrelationData.class)
        );

        outboxPublisher.publishPending();

        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getAttempts()).isEqualTo((short) 1);
        assertThat(event.getLastError()).contains("transaction.created");
    }

    private OutboxEvent sampleEvent() {
        OutboxEvent event = new OutboxEvent();
        event.setId(UUID.randomUUID());
        event.setAggregateType("TRANSACTION");
        event.setAggregateId(UUID.randomUUID());
        event.setEventType("TransactionCreated");
        event.setPayload("{\"eventType\":\"TransactionCreated\"}");
        return event;
    }
}
